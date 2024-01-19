/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.tai.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import play.api.http.Status._
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException, HeaderNames, HttpClient}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.admin.RtiCallToggle
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.rti.QaData
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate
import scala.concurrent.Future

class RtiConnectorSpec extends ConnectorBaseSpec {

  val taxYear: TaxYear = TaxYear()
  val url = s"/rti/individual/payments/nino/${nino.withoutSuffix}/tax-year/${taxYear.twoDigitRange}"

  val mockHttp = mock[HttpClient]
  implicit val request = FakeRequest()

  lazy val sut: RtiConnector = inject[DefaultRtiConnector]
  lazy val sutWithMockHttp = new DefaultRtiConnector(
    mockHttp,
    inject[Metrics],
    inject[DesConfig],
    inject[RtiUrls],
    mockFeatureFlagService
  )

  def verifyOutgoingUpdateHeaders(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern
        .withHeader("Environment", equalTo("local"))
        .withHeader("Authorization", equalTo("Bearer desAuthorization"))
        .withHeader("Gov-Uk-Originator-Id", equalTo(desOriginatorId))
        .withHeader(HeaderNames.xSessionId, equalTo(sessionId))
        .withHeader(HeaderNames.xRequestId, equalTo(requestId))
        .withHeader(
          "CorrelationId",
          matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")))

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      Future.successful(FeatureFlag(RtiCallToggle, isEnabled = true))
    )
  }

  "RtiConnector" when {

    "withoutSuffix is called" must {
      "return a nino without the suffix" in {
        sut.withoutSuffix(nino) mustBe nino.withoutSuffix
      }
    }

    "getPaymentsForYear" must {
      "return a sequence of annual accounts" when {
        "a successful Http response is received from RTI" in {
          val taxYearRange = "16-17"
          val fileName = "1"
          val rtiJson = QaData.paymentDetailsForYear(taxYearRange)(fileName)

          val expectedPayments = Seq(
            AnnualAccount(
              26,
              TaxYear(2016),
              Available,
              List(
                Payment(
                  LocalDate.of(2016, 4, 30),
                  5000.00,
                  1500.00,
                  600.00,
                  5000.00,
                  1500.00,
                  600.00,
                  Quarterly,
                  None),
                Payment(
                  LocalDate.of(2016, 7, 31),
                  11000.00,
                  3250.00,
                  1320.00,
                  6000.00,
                  1750.00,
                  720.00,
                  Quarterly,
                  None),
                Payment(
                  LocalDate.of(2016, 10, 31),
                  15000.00,
                  4250.00,
                  1800.00,
                  4000.00,
                  1000.00,
                  480.00,
                  Quarterly,
                  None),
                Payment(
                  LocalDate.of(2017, 2, 28),
                  19000.00,
                  5250.00,
                  2280.00,
                  4000.00,
                  1000.00,
                  480.00,
                  Quarterly,
                  None)
              ),
              List()
            ))

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(rtiJson.toString()))
          )

          val result = sut.getPaymentsForYear(nino, taxYear).value.futureValue
          result mustBe Right(expectedPayments)

          verifyOutgoingUpdateHeaders(getRequestedFor(urlEqualTo(url)))
        }
      }

      "return an empty list" when {
        s"a 404 status is returned from RTI" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(404)
          ))

          val result = sut.getPaymentsForYear(nino, taxYear).value.futureValue

          result mustBe Right(Seq.empty)
        }
      }

      "return an error result " when {

        val errors = Seq(
          (BAD_REQUEST, "The request was not formed correctly", BadRequestError),
          (SERVICE_UNAVAILABLE, "The service is currently unavailable", ServiceUnavailableError),
          (INTERNAL_SERVER_ERROR, "An RTI error has occurred", ServerError),
          (BAD_GATEWAY, "An error has occurred", BadGatewayError),
          (GATEWAY_TIMEOUT, "Gateway timeout", TimeoutError),
          (499, "Nginx error", TimeoutError),
          (413, "Unhandled status", UnhandledStatusError)
        )

        errors foreach { error =>
          s"a ${error._1} status is returned from RTI" in {

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(error._1)
                  .withBody(error._2))
            )

            val result = sut.getPaymentsForYear(nino, taxYear).value.futureValue

            result mustBe Left(error._3)
          }
        }
      }

      "return a BadGateway error" when {

        val exMessage = "Bad gateway error"

        "a BadGateway is received" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(exMessage))
          )

          sut.getPaymentsForYear(nino, taxYear).value.futureValue mustBe Left(BadGatewayError)
        }

        "a BadGatewayException is received" in {

          when(mockHttp.GET(any(), any(), any())(any(), any(), any())) thenReturn Future.failed(
            new BadGatewayException(exMessage))

          sutWithMockHttp.getPaymentsForYear(nino, taxYear).value.futureValue mustBe Left(BadGatewayError)
        }
      }

      "return a TimeOutError" when {

        val exMessage = "Could not reach gateway"

        "a 499 is received" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(499)
                .withBody(exMessage))
          )

          sut.getPaymentsForYear(nino, taxYear).value.futureValue mustBe Left(TimeoutError)
        }

        "a GatewayTimeout is received" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(GATEWAY_TIMEOUT)
                .withBody(exMessage))
          )

          sut.getPaymentsForYear(nino, taxYear).value.futureValue mustBe Left(TimeoutError)
        }

        "a GatewayTimeoutException is received" in {

          when(mockHttp.GET(any(), any(), any())(any(), any(), any())) thenReturn Future.failed(
            new GatewayTimeoutException(exMessage))

          sutWithMockHttp.getPaymentsForYear(nino, taxYear).value.futureValue mustBe Left(TimeoutError)
        }
      }

      "return an exception " when {
        "an exception is thrown whilst trying to contact RTI" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK))
          )

          val result = sut.getPaymentsForYear(nino, taxYear).value.failed.futureValue

          result mustBe a[Exception]
        }
      }
    }

    "return a ServiceUnavailableError " when {
      "the rti toggle is set to false" in {

        when(mockFeatureFlagService.get(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
          Future.successful(FeatureFlag(RtiCallToggle, isEnabled = false))
        )

        lazy val sutWithRTIDisabled = new DefaultRtiConnector(
          inject[HttpClient],
          inject[Metrics],
          inject[DesConfig],
          inject[RtiUrls],
          mockFeatureFlagService
        )

        sutWithRTIDisabled.getPaymentsForYear(nino, taxYear).value.futureValue mustBe
          Left(ServiceUnavailableError)
      }

      "the year is CY+1" in {

        lazy val sutWithRTIDisabled = new DefaultRtiConnector(
          inject[HttpClient],
          inject[Metrics],
          inject[DesConfig],
          inject[RtiUrls],
          mockFeatureFlagService
        )

        sutWithRTIDisabled.getPaymentsForYear(nino, taxYear.next).value.futureValue mustBe
          Left(ServiceUnavailableError)
      }
    }
  }
}
