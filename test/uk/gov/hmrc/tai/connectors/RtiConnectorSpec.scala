/*
 * Copyright 2019 HM Revenue & Customs
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
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.rti.RtiData
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random


class RtiConnectorSpec extends PlaySpec
  with MockitoSugar {

  private implicit val hc = HeaderCarrier()

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
      headers.extraHeaders mustBe List(("Environment", "env"), ("Authorization", "auth"), ("Gov-Uk-Originator-Id", "orgId"))
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


    "return RTI Json" when {
      "given a valid Nino and TaxYear" in {
        val fakeRtiData = Json.toJson(RtiData(nino.nino, TaxYear(2017), "req123", Nil))
        val fakeResponse: HttpResponse = HttpResponse(200, Some(fakeRtiData))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(fakeResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp = sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val rtiData = Await.result(resp, 5 seconds)

        rtiData mustBe fakeRtiData
      }
    }

    "return a success response from RTI Details" when {
      "it returns a success Http response for GET transactions with matching nino data" in {
        val fakeRtiData = Json.toJson(RtiData (nino.nino, TaxYear(2017), "req123", Nil))
        val fakeResponse: HttpResponse = HttpResponse(200, Some(fakeRtiData))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(fakeResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp = sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val rtiData = Await.result(resp, 5 seconds)

        rtiData mustBe fakeRtiData

      }
    }
    "return an error response from RTI Details" when {
      "it returns a bad request Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(BadRequestHttpResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp =  sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"bad request\""
      }
      "it returns a not found Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(NotFoundHttpResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp =  sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"not found\""
      }
      "it returns a internal server error Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(InternalServerErrorHttpResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp =  sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"internal server error\""

      }
      "it returns an unknown Http response for GET transactions" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any(), any()))
          .thenReturn(Future.successful(UnknownErrorHttpResponse))

        val mockHttpPost = mock[HttpPost]
        val mockAudit = mock[Auditor]
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockUrls = mock[RtiUrls]
        val mockConfig = mock[DesConfig]
        val sut = createSUT(mockHttpClient, mockMetrics, mockAudit, mockConfig, mockUrls)

        val resp =  sut.getRTIDetails(Nino(nino.nino), TaxYear(2017))
        val ex = the[HttpException] thrownBy Await.result(resp, 5 seconds)

        ex.getMessage mustBe "\"unknown response\""

      }
    }
  }
  private val nino: Nino = new Generator(new Random).nextNino
  private val BadRequestHttpResponse = HttpResponse(400, Some(JsString("bad request")), Map("ETag" -> Seq("34")))
  private val UnknownErrorHttpResponse: HttpResponse = HttpResponse(418, Some(JsString("unknown response")), Map("ETag" -> Seq("34")))
  private val NotFoundHttpResponse: HttpResponse = HttpResponse(404, Some(JsString("not found")), Map("ETag" -> Seq("34")))
  private val InternalServerErrorHttpResponse: HttpResponse = HttpResponse(500, Some(JsString("internal server error")), Map("ETag" -> Seq("34")))

  private def createSUT(httpClient: HttpClient,
                        metrics: Metrics,
                        audit: Auditor,
                        rtiConfig: DesConfig,
                        rtiUrls: RtiUrls) =

    new RtiConnector(httpClient, metrics, audit, rtiConfig, rtiUrls) {
      override val originatorId: String = "orgId"
    }
}