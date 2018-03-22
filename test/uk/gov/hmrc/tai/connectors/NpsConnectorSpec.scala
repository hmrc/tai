/*
 * Copyright 2018 HM Revenue & Customs
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
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model
import uk.gov.hmrc.tai.model.GateKeeperRule
import uk.gov.hmrc.tai.model.nps.{NpsEmployment, NpsIabdUpdateAmount, NpsIabdUpdateAmountFormats, NpsTaxAccount}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class NpsConnectorSpec extends PlaySpec
  with MockitoSugar
  with FakeTaiPlayApplication {

  "NpsConnector" should {
    "fetch the path url" when {
      "given a nino and path" in {
        val mockConfig = mock[NpsConfig]
        when(mockConfig.baseURL)
          .thenReturn("")

        val sut = createSUT(mock[Metrics], mock[HttpClient], mock[Auditor], mock[NpsIabdUpdateAmountFormats], mockConfig)
        sut.npsPathUrl(SuccessTestNino, "path") mustBe s"/person/${SuccessTestNino}/path"
      }
    }

    "return employments with success" when {
      "given a nino and a year" in {
        val expectedResult: (List[NpsEmployment], List[model.nps2.NpsEmployment], Int, List[GateKeeperRule]) = (Nil, Nil, 0, Nil)

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn((new Timer).time)

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[NpsIabdUpdateAmountFormats], mock[NpsConfig])
        Await.result(sut.getEmployments(SuccessTestNino, 2017)(HeaderCarrier()), Duration.Inf) mustBe expectedResult
      }
    }

    "return employments json with success" when {
      "given a nino and a year" in {
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn((new Timer).time)

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val sut = createSUT(mockMetrics, mockHttpClient, mock[Auditor], mock[NpsIabdUpdateAmountFormats], mock[NpsConfig])
        Await.result(sut.getEmploymentDetails(SuccessTestNino, 2017)(HeaderCarrier()), Duration.Inf) mustBe Json.parse("[]")
      }
    }

    "return iabds" when {
      "given a valid nino, a year and a type" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        when(mockMetrics.startTimer(any())).thenReturn((new Timer).time)
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))
        Await.result(sut.getIabdsForType(SuccessTestNino, 2017, 1)(HeaderCarrier()), Duration.Inf) mustBe Nil
      }

      "given a nino, a year" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        when(mockMetrics.startTimer(any()))
          .thenReturn((new Timer).time)
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        Await.result(sut.getIabds(SuccessTestNino, 2017)(HeaderCarrier()), Duration.Inf) mustBe Nil
      }
    }

    "return tax account" when {
      "given a nino and a year" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        when(mockMetrics.startTimer(any())).thenReturn((new Timer).time)
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))
        val expectedResult:(NpsTaxAccount, Int, JsValue) =
          (NpsTaxAccount(None,None,None,None,None,None,None,None,None,None,None,None,None), 0, Json.parse("[]"))

        Await.result(sut.getCalculatedTaxAccount(SuccessTestNino, 2017)(HeaderCarrier()), Duration.Inf) mustBe expectedResult
      }

      "given a nino and a year for raw response" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        when(mockMetrics.startTimer(any())).thenReturn((new Timer).time)
        when(mockHttpClient.GET[HttpResponse](any[String])(any(), any[HeaderCarrier], any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))
        Await.result(sut.getCalculatedTaxAccountRawResponse(SuccessTestNino, 2017)(HeaderCarrier()), Duration.Inf) mustBe (SuccesfulGetResponseWithObject)
      }
    }

    "update employment data" when {
      "given an empty updates amount return an OK response" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        implicit val hc = HeaderCarrier()
        when(mockMetrics.startTimer(any())).thenReturn((new Timer).time)
        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))
        val resp = sut.updateEmploymentData(nino = SuccessTestNino, 1, 1, 1, List())(HeaderCarrier())
        val result: HttpResponse = Await.result(resp, 5 seconds)
        result.status mustBe 200
      }

      "given a list of update amounts post to NPS" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockAudit = mock[Auditor]
        val mockFormats = mock[NpsIabdUpdateAmountFormats]
        val mockConfig = mock[NpsConfig]

        val sut = createSUT(mockMetrics, mockHttpClient, mockAudit, mockFormats, mockConfig)
        implicit val hc = HeaderCarrier()

        when(mockMetrics.startTimer(any()))
          .thenReturn((new Timer).time)
        when(mockHttpClient.POST[ResponseObject, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(SuccesfulGetResponseWithObject))

        val resp = sut.updateEmploymentData(nino = SuccessTestNino, 2016, 1, 25, List(NpsIabdUpdateAmount(1,200,Some(100),Some("10/4/2016"),Some(1))))(HeaderCarrier())
        val result: HttpResponse = Await.result(resp, 5 seconds)
        result.status mustBe 200
      }
    }
  }

  private case class ResponseObject(name: String, age: Int)
  private val SuccessTestNino = new Generator(new Random).nextNino
  private implicit val responseObjectFormat = Json.format[ResponseObject]

  implicit val formats = Json.format[(List[NpsEmployment], Int)]

  private val SuccesfulGetResponseWithObject: HttpResponse = HttpResponse(200, Some(Json.parse("[]")), Map("ETag" -> Seq("0")))
  private val mockHttpResponse = HttpResponse(
    responseStatus = 200,
    responseString = Some("Success"))

  private val SuccesfulPostResponseWithObject: HttpResponse = HttpResponse(200)
  private def createSUT(metrics: Metrics, httpClient: HttpClient, audit: Auditor, formats: NpsIabdUpdateAmountFormats, config: NpsConfig) =
    new NpsConnector(metrics, httpClient, audit, formats, config)
}