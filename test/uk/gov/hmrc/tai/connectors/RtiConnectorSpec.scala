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

import com.codahale.metrics.Timer
import org.joda.time.LocalDate
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json, Reads}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.{DesConfig, RtiToggleConfig}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.EmploymentHodFormatters.annualAccountHodReads
import uk.gov.hmrc.tai.model.rti.{QaData, RtiData}
import uk.gov.hmrc.tai.model.tai.TaxYear
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class RtiConnectorSpec extends PlaySpec with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val reads: Reads[Seq[AnnualAccount]] = annualAccountHodReads

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
        val fakeResponse: HttpResponse = HttpResponse(200, Some(Json.toJson(fakeRtiData)))

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

    "return a successful response from getPaymentsForYear" when {
      "a successful Http response is received from RTI" in {

        val taxYearRange = "16-17"
        val fileName = "1"
        val fakeRtiData = QaData.paymentDetailsForYear(taxYearRange)(fileName)
        val fakeResponse: HttpResponse = HttpResponse(200, Some(fakeRtiData))

        val expectedPayments = Seq(
          AnnualAccount(
            "267-000-000",
            TaxYear(2016),
            Available,
            List(
              Payment(new LocalDate(2016, 4, 30), 5000.00, 1500.00, 600.00, 5000.00, 1500.00, 600.00, Quarterly, None),
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
              Payment(new LocalDate(2017, 2, 28), 19000.00, 5250.00, 2280.00, 4000.00, 1000.00, 480.00, Quarterly, None)
            ),
            List()
          ))

        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(fakeResponse))

        val result = Await.result(createSUT().getPaymentsForYear(nino, TaxYear(2017)), 5 seconds)
        result mustBe Right(expectedPayments)
      }
    }

    "return a TemporarilyUnavailable response" when {
      //TODO BadRequest?
      val exceptionResponses = Seq(BadRequestHttpResponse, UnknownErrorHttpResponse, InternalServerErrorHttpResponse)

      exceptionResponses foreach { exceptionResponse =>
        s"the HTTP exception is $exceptionResponse" in {
          when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
            .thenReturn(Future.successful(exceptionResponse))

          val result = Await.result(createSUT().getPaymentsForYear(nino, TaxYear(2017)), 5 seconds)
          result mustBe Left(TemporarilyUnavailable)
        }
      }

      "the rti toggle is set to false" in {
        val mockRtiToggle = mock[RtiToggleConfig]
        when(mockRtiToggle.rtiEnabled).thenReturn(false)

        val result =
          Await.result(createSUT(rtiToggle = mockRtiToggle).getPaymentsForYear(nino, TaxYear(2017)), 5 seconds)
        result mustBe Left(TemporarilyUnavailable)
      }
    }

    "return an Unavailable response" when {
      "the HTTP exception is a NotFound exception" in {
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(NotFoundHttpResponse))

        val result = Await.result(createSUT().getPaymentsForYear(nino, TaxYear(2017)), 5 seconds)
        result mustBe Left(Unavailable)
      }
    }
  }

  val nino: Nino = new Generator(new Random).nextNino

  lazy val BadRequestHttpResponse = HttpResponse(400, Some(JsString("bad request")), Map("ETag" -> Seq("34")))
  lazy val UnknownErrorHttpResponse: HttpResponse =
    HttpResponse(418, Some(JsString("unknown response")), Map("ETag" -> Seq("34")))
  lazy val InternalServerErrorHttpResponse: HttpResponse =
    HttpResponse(500, Some(JsString("internal server error")), Map("ETag" -> Seq("34")))
  val NotFoundHttpResponse: HttpResponse =
    HttpResponse(404, Some(JsString("not found")), Map("ETag" -> Seq("34")))

  val mockHttpClient = mock[HttpClient]
  val mockRtiToggle = mock[RtiToggleConfig]

  private def createSUT(
    httpClient: HttpClient = mockHttpClient,
    metrics: Metrics = mock[Metrics],
    audit: Auditor = mock[Auditor],
    rtiConfig: DesConfig = mock[DesConfig],
    rtiUrls: RtiUrls = mock[RtiUrls],
    rtiToggle: RtiToggleConfig = mockRtiToggle) = {

    when(mockRtiToggle.rtiEnabled).thenReturn(true)

    val mockTimerContext = mock[Timer.Context]

    when(metrics.startTimer(any()))
      .thenReturn(mockTimerContext)

    new RtiConnector(httpClient, metrics, audit, rtiConfig, rtiUrls, rtiToggle) {
      override val originatorId: String = "orgId"
    }
  }
}
