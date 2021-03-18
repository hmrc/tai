/*
 * Copyright 2021 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.when
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{BadGatewayException, GatewayTimeoutException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.{DesConfig, RtiToggleConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.rti.{QaData, RtiData}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class RtiConnectorSpec extends ConnectorBaseSpec {

  val taxYear: TaxYear = TaxYear()
  val url = s"/rti/individual/payments/nino/${nino.withoutSuffix}/tax-year/20-21"

  val mockHttp = mock[HttpClient]

  lazy val sut: RtiConnector = inject[RtiConnector]
  lazy val sutWithMockHttp = new RtiConnector(
    mockHttp,
    inject[Metrics],
    inject[Auditor],
    inject[DesConfig],
    inject[RtiUrls],
    inject[RtiToggleConfig])

  "RtiConnector" when {

    "withoutSuffix is called" should {
      "return a nino without the suffix" in {
        sut.withoutSuffix(nino) mustBe nino.withoutSuffix
      }
    }

    "createHeader is called" should {
      "set the correct headers for a request" in {
        val headers = sut.createHeader
        headers.extraHeaders mustBe List(
          ("Environment", "local"),
          ("Authorization", "Bearer Local"),
          ("Gov-Uk-Originator-Id", originatorId))
      }
    }

    "getRti is called" should {
      "return RTI data when the response is OK (200)" in {
        val fakeRtiData = Json.toJson(RtiData(nino.withoutSuffix, taxYear, "req123", Nil))

        server.stubFor(
          get(urlEqualTo(url)).willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(fakeRtiData.toString()))
        )

        val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

        rtiStatus.status mustBe OK
        rtiData mustBe Some(fakeRtiData.as[RtiData])
      }

      "return No RTI data" when {
        "the ninos are mismatched" in {
          val mismatchedNino = new Generator(new Random).nextNino.withoutSuffix
          val fakeRtiData = Json.toJson(RtiData(mismatchedNino, taxYear, "req123", Nil))

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(fakeRtiData.toString()))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe OK
          rtiStatus.response mustBe "Incorrect RTI Payload"
          rtiData mustBe None
        }

        "connector returns 400" in {

          val errorMessage = "Invalid query"

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(errorMessage))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe BAD_REQUEST
          rtiStatus.response mustBe errorMessage
          rtiData mustBe None
        }

        "connector returns 404" in {

          val errorMessage = "No RTI found"

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(errorMessage))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe NOT_FOUND
          rtiStatus.response mustBe errorMessage
          rtiData mustBe None
        }

        "connector returns 4xx" in {

          val errorMessage = "RTI record locked"

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(LOCKED)
                .withBody(errorMessage))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe LOCKED
          rtiStatus.response mustBe errorMessage
          rtiData mustBe None
        }

        "connector returns 500" in {

          val errorMessage = "An error occurred"

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(errorMessage))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe INTERNAL_SERVER_ERROR
          rtiStatus.response mustBe errorMessage
          rtiData mustBe None
        }

        "connector returns 5xx" in {

          val errorMessage = "Can not reach gateway"

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(BAD_GATEWAY)
                .withBody(errorMessage))
          )

          val (rtiData, rtiStatus) = Await.result(sut.getRTI(nino, taxYear), 5 seconds)

          rtiStatus.status mustBe BAD_GATEWAY
          rtiStatus.response mustBe errorMessage
          rtiData mustBe None
        }
      }
    }

    "getPaymentsForYear" should {
      "return a sequence of annual accounts" when {
        "a successful Http response is received from RTI" in {
          val taxYearRange = "16-17"
          val fileName = "1"
          val rtiJson = QaData.paymentDetailsForYear(taxYearRange)(fileName)

          val expectedPayments = Seq(
            AnnualAccount(
              "267-000-000",
              TaxYear(2016),
              Available,
              List(
                Payment(
                  new LocalDate(2016, 4, 30),
                  5000.00,
                  1500.00,
                  600.00,
                  5000.00,
                  1500.00,
                  600.00,
                  Quarterly,
                  None),
                Payment(
                  new LocalDate(2016, 7, 31),
                  11000.00,
                  3250.00,
                  1320.00,
                  6000.00,
                  1750.00,
                  720.00,
                  Quarterly,
                  None),
                Payment(
                  new LocalDate(2016, 10, 31),
                  15000.00,
                  4250.00,
                  1800.00,
                  4000.00,
                  1000.00,
                  480.00,
                  Quarterly,
                  None),
                Payment(
                  new LocalDate(2017, 2, 28),
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

          val result = Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds)
          result mustBe Right(expectedPayments)
        }
      }

      "return an error result " when {

        val errors = Seq(
          (NOT_FOUND, "Resource not found", ResourceNotFoundError),
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

            val result = Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds)

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

          Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe Left(BadGatewayError)
        }

        "a BadGatewayException is received" in {

          when(mockHttp.GET(any())(any(), any(), any())) thenReturn Future.failed(new BadGatewayException(exMessage))

          Await.result(sutWithMockHttp.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe Left(BadGatewayError)
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

          Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe Left(TimeoutError)
        }

        "a GatewayTimeout is received" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(
              aResponse()
                .withStatus(GATEWAY_TIMEOUT)
                .withBody(exMessage))
          )

          Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe Left(TimeoutError)
        }

        "a GatewayTimeoutException is received" in {

          when(mockHttp.GET(any())(any(), any(), any())) thenReturn Future.failed(
            new GatewayTimeoutException(exMessage))

          Await.result(sutWithMockHttp.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe Left(TimeoutError)
        }
      }

      "return an exception " when {
        "an exception is thrown whilst trying to contact RTI" in {

          server.stubFor(
            get(urlEqualTo(url)).willReturn(aResponse()
              .withFault(Fault.MALFORMED_RESPONSE_CHUNK))
          )

          an[Exception] mustBe thrownBy {
            Await.result(sut.getPaymentsForYear(nino, taxYear), 5 seconds)
          }
        }
      }
    }

    "return a ServiceUnavailableError " when {
      "the rti toggle is set to false" in {

        lazy val stubbedRtiConfig = new RtiToggleConfig(inject[Configuration]) {
          override def rtiEnabled: Boolean = false
        }

        lazy val sutWithRTIDisabled = new RtiConnector(
          inject[HttpClient],
          inject[Metrics],
          inject[Auditor],
          inject[DesConfig],
          inject[RtiUrls],
          stubbedRtiConfig
        )

        Await.result(sutWithRTIDisabled.getPaymentsForYear(nino, taxYear), 5 seconds) mustBe
          Left(ServiceUnavailableError)
      }
    }
  }
}
