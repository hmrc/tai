/*
 * Copyright 2020 HM Revenue & Customs
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

import java.net.URL

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock._
import org.joda.time.LocalDate
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.http.Status._
import play.api.libs.json.{JsString, Json, Reads}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.{DesConfig, RtiToggleConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.EmploymentHodFormatters.annualAccountHodReads
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.rti.{QaData, RtiData}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class RtiConnectorSpec extends ConnectorBaseSpec {

  implicit val reads: Reads[Seq[AnnualAccount]] = annualAccountHodReads
  val mockRtiToggle = mock[RtiToggleConfig]

  trait MockPaymentsForYearSetup {
    val mockTimerContext = mock[Timer.Context]
    val mockMetrics = mock[Metrics]
    val mockHttpConnector = mock[HttpClient]

    when(mockRtiToggle.rtiEnabled).thenReturn(true)
    when(mockMetrics.startTimer(APITypes.RTIAPI)).thenReturn(mockTimerContext)

    val mockRtiConnector =
      new RtiConnector(mockHttpConnector, mockMetrics, mock[Auditor], mock[DesConfig], mock[RtiUrls], mockRtiToggle)
  }

  trait PaymentsForYearSetup {
    val httpClient: HttpClient = injector.instanceOf[HttpClient]
    val metrics: Metrics = injector.instanceOf[Metrics]
    val audit: Auditor = mock[Auditor]
    val rtiConfig: DesConfig = injector.instanceOf[DesConfig]
    val rtiUrls: RtiUrls = injector.instanceOf[RtiUrls]
    val rtiToggle: RtiToggleConfig = mockRtiToggle

    val url = new URL(rtiUrls.paymentsForYearUrl(nino.withoutSuffix, taxyear)).getPath

    when(mockRtiToggle.rtiEnabled).thenReturn(true)

    val rtiConnector = new RtiConnector(httpClient, metrics, audit, rtiConfig, rtiUrls, rtiToggle)
  }

  "RtiConnector" should {

    "have withoutSuffix nino" when {
      "given a valid nino" in {
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mock[HttpClient], mockMetrics, mockAudit, mockConfig, mockUrls)

        sut.withoutSuffix(Nino(nino.nino)) mustBe nino.withoutSuffix
      }
    }

    "have createHeader" in {
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mock[Timer.Context])

      val mockConfig = mock[DesConfig]
      when(mockConfig.authorization)
        .thenReturn("auth")
      when(mockConfig.environment)
        .thenReturn("env")
      when(mockConfig.originatorId)
        .thenReturn("orgId")

      val sut = createSUT(mock[HttpClient], mockMetrics, mock[Auditor], mockConfig, mock[RtiUrls])

      val headers = sut.createHeader
      headers.extraHeaders mustBe List(
        ("Environment", "env"),
        ("Authorization", "auth"),
        ("Gov-Uk-Originator-Id", "orgId"))
    }

    "have get RTI" when {
      "given a valid Nino and TaxYear" in {
        val fakeRtiData = RtiData(nino.withoutSuffix, TaxYear(2017), "req123", Nil)
        val fakeResponse: HttpResponse = HttpResponse(200, Json.toJson(fakeRtiData), Map[String, Seq[String]]())

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(fakeResponse))

        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp = sut.getRTI(Nino(nino.nino), TaxYear(2017))
        val (rtiData, rtiStatus) = Await.result(resp, 5 seconds)

        rtiStatus.status mustBe 200
        rtiData mustBe Some(fakeRtiData)

      }
    }

    "getPaymentsForYear" should {
      "return a sequence of annual accounts" when {
        "a successful Http response is received from RTI" in new PaymentsForYearSetup {
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

          val result = Await.result(rtiConnector.getPaymentsForYear(nino, taxyear), 5 seconds)
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
          s"a ${error._1} status is returned from RTI" in new PaymentsForYearSetup {

            server.stubFor(
              get(urlEqualTo(url)).willReturn(
                aResponse()
                  .withStatus(error._1)
                  .withBody(error._2))
            )

            val result = Await.result(rtiConnector.getPaymentsForYear(nino, taxyear), 5 seconds)

            result mustBe Left(error._3)
          }
        }
      }

      "return a BadGateway error" when {

        "a BadGatewayException is received" in new MockPaymentsForYearSetup {
          when(mockHttpConnector.GET(any())(any(), any(), any()))
            .thenReturn(Future.failed(new BadGatewayException("Bad gateway")))

          val result = Await.result(mockRtiConnector.getPaymentsForYear(nino, taxyear), 5 seconds)
          result mustBe Left(BadGatewayError)

          verify(mockTimerContext, times(1)).stop()
        }
      }

      "return a TimeOutError " when {

        "a GatewayTimeout is received" in new MockPaymentsForYearSetup {

          when(mockHttpConnector.GET(any())(any(), any(), any()))
            .thenReturn(Future.failed(new GatewayTimeoutException("timeout")))

          val result = Await.result(mockRtiConnector.getPaymentsForYear(nino, taxyear), 5 seconds)
          result mustBe Left(TimeoutError)

          verify(mockTimerContext, times(1)).stop()
        }
      }

      "return an exception " when {
        "an exception is thrown whilst trying to contact RTI" in new MockPaymentsForYearSetup {

          val errorMessage = "An error has occurred"
          when(mockHttpConnector.GET(any())(any(), any(), any()))
            .thenReturn(Future.failed(new RuntimeException(errorMessage)))

          val exception = intercept[RuntimeException] {
            Await.result(mockRtiConnector.getPaymentsForYear(nino, taxyear), 5 seconds)
          }

          exception.getMessage mustBe errorMessage

          verify(mockTimerContext, times(1)).stop()
        }

      }

    }

    "return a ServiceUnavailableError " when {
      "the rti toggle is set to false" in {
        val mockRtiToggle = mock[RtiToggleConfig]
        when(mockRtiToggle.rtiEnabled).thenReturn(false)

        val testConnector = createSUT(rtiToggle = mockRtiToggle)
        val result = Await.result(testConnector.getPaymentsForYear(nino, TaxYear(2017)), 5 seconds)
        result mustBe Left(ServiceUnavailableError)
      }
    }
  }

  val taxyear = TaxYear(2017)

  lazy val BadRequestHttpResponse = HttpResponse(400, JsString("bad request"), Map("ETag" -> Seq("34")))
  lazy val UnknownErrorHttpResponse: HttpResponse =
    HttpResponse(418, JsString("unknown response"), Map("ETag" -> Seq("34")))
  lazy val InternalServerErrorHttpResponse: HttpResponse =
    HttpResponse(500, JsString("internal server error"), Map("ETag" -> Seq("34")))
  val NotFoundHttpResponse: HttpResponse =
    HttpResponse(404, JsString("not found"), Map("ETag" -> Seq("34")))

  private def createSUT(
    httpClient: HttpClient = mock[HttpClient],
    metrics: Metrics = mock[Metrics],
    audit: Auditor = mock[Auditor],
    rtiConfig: DesConfig = mock[DesConfig],
    rtiUrls: RtiUrls = mock[RtiUrls],
    rtiToggle: RtiToggleConfig = mockRtiToggle) = {

    val mockTimerContext = mock[Timer.Context]
    when(metrics.startTimer(any()))
      .thenReturn(mockTimerContext)
    when(mockRtiToggle.rtiEnabled).thenReturn(true)

    new RtiConnector(httpClient, metrics, audit, rtiConfig, rtiUrls, rtiToggle) {
      override val originatorId: String = "orgId"
    }
  }
}
