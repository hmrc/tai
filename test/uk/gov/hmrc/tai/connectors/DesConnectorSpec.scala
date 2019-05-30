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
import org.joda.time.DateTime
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.{NpsIabdRoot, NpsTaxAccount}
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class DesConnectorSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "DesConnector" should {

    "create a valid des url with an additional path" when {
      "a valid nino and additional path are supplied" in {
        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.baseURL)
          .thenReturn("testServiceUrl")

        val sut = createSUT(mock[HttpClient],
          mock[Metrics],
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)

        val result = sut.desPathUrl(Nino(nino), "test")

        result mustBe s"testServiceUrl/pay-as-you-earn/individuals/$nino/test"
      }
    }

    "create a header carrier with extra headers populated correctly" when {
      "the required extra header information is provided" in {
        val extraHeaders = Seq(
          "Environment" -> "testEnvironment",
          "Authorization" -> "testAuthorization",
          "Content-Type" -> TaiConstants.contentType,
          "Originator-Id" -> "testOriginatorId",
          "ETag" -> "1"
        )

        val extraHeaders2 = Seq(
          "Environment" -> "testEnvironment",
          "Authorization" -> "testAuthorization",
          "Content-Type" -> TaiConstants.contentType,
          "Originator-Id" -> "testOriginatorId2",
          "ETag" -> "1"
        )

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.environment)
          .thenReturn("testEnvironment")
        when(mockDesConfig.authorization)
          .thenReturn("testAuthorization")
        when(mockDesConfig.originatorId)
          .thenReturn("testOriginatorId")
        when(mockDesConfig.daPtaOriginatorId)
          .thenReturn("testOriginatorId2")

        val sut = createSUT(mock[HttpClient],
          mock[Metrics],
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)

        val result = sut.headerForUpdate(1, mockDesConfig.originatorId)
        val result2 = sut.headerForUpdate(1, mockDesConfig.daPtaOriginatorId)

        result.extraHeaders mustBe extraHeaders
        result2.extraHeaders mustBe extraHeaders2
      }
    }

    "get IABD's from DES api" when {
      "supplied with a valid nino, year and IABD type" in {
        val jsonData = Json.toJson(List(NpsIabdRoot(nino = nino, `type` = iabdType)))
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.OK,
          responseString = Some("Success"),
          responseJson = Some(jsonData)))

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](urlCaptor.capture())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.baseURL)
          .thenReturn("testServiceUrl")

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)

        val response = Await.result(sut.getIabdsForTypeFromDes(Nino(nino), taxYear, iabdType)(hc), 5.seconds)

        response mustBe List(NpsIabdRoot(nino = nino, `type` = iabdType))
        urlCaptor.getValue mustBe s"testServiceUrl/pay-as-you-earn/individuals/$nino/iabds/tax-year/$taxYear?type=$iabdType"
      }
    }

    "recieve 404 Not-Found exception from DES API" when {
      "supplied with a valid nino, year and IABD type but an IABD cannot be found" in {
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.NOT_FOUND,
          responseString = Some("Not-Found"),
          responseJson = None))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mock[DesConfig])

        val response = sut.getIabdsForTypeFromDes(Nino(nino), taxYear, iabdType)(hc)

        val ex = the[NotFoundException] thrownBy Await.result(response, 5.seconds)
        ex.getMessage mustBe "Not-Found"
      }
    }

    "get IABDs from DES api" when {
      "supplied with a valid nino and year" in {
        val iabdList = List(NpsIabdRoot(nino = nino, `type` = iabdType), NpsIabdRoot(nino = nino, `type` = iabdType))
        val jsonData = Json.toJson(iabdList)
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.OK,
          responseString = Some("Success"),
          responseJson = Some(jsonData)))

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.baseURL)
          .thenReturn("testServiceUrl")

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](urlCaptor.capture())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)
        val response = Await.result(sut.getIabdsFromDes(Nino(nino), taxYear)(hc), 5.seconds)

        response mustBe iabdList
        urlCaptor.getValue mustBe s"testServiceUrl/pay-as-you-earn/individuals/$nino/iabds/tax-year/$taxYear"
      }
    }

    "recieve 404 Not-Found exception from DES API" when {
      "supplied with a valid nino and year but an IABD cannot be found" in {
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.NOT_FOUND,
          responseString = Some("Not-Found"),
          responseJson = None))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mock[DesConfig])

        val response = sut.getIabdsFromDes(Nino(nino), taxYear)(hc)

        val ex = the[NotFoundException] thrownBy Await.result(response, 5.seconds)
        ex.getMessage mustBe "Not-Found"
      }
    }

    "get TaxAccount from DES api" when {
      "supplied with a valid nino and year" in {
        val taxAccount = NpsTaxAccount(Some(nino), Some(taxYear))
        val jsonData = Json.toJson(taxAccount)
        val expectedResult: (NpsTaxAccount, Int, JsValue) = (taxAccount, -1, jsonData)
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.OK,
          responseString = Some("Success"),
          responseJson = Some(jsonData)))

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](urlCaptor.capture())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.baseURL)
          .thenReturn("testServiceUrl")

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)

        val response = Await.result(sut.getCalculatedTaxAccountFromDes(Nino(nino), taxYear)(hc), 5.seconds)

        response mustBe expectedResult
        urlCaptor.getValue mustBe s"testServiceUrl/pay-as-you-earn/individuals/$nino/tax-account/tax-year/$taxYear?calculation=true"
      }
    }

    "recieve 404 Not-Found exception from DES API" when {
      "supplied with a valid nino and year but the taxAccount cannot be found" in {
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.NOT_FOUND,
          responseString = Some("Not-Found"),
          responseJson = None))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mock[DesConfig])

        val response = sut.getCalculatedTaxAccountFromDes(Nino(nino), taxYear)(hc)

        val ex = the[NotFoundException] thrownBy Await.result(response, 5.seconds)
        ex.getMessage mustBe "Not-Found"
      }
    }

    "get TaxAccount Json from DES api" when {
      "supplied with a valid nino and year" in {
        val jsonData = Json.toJson(NpsTaxAccount(Some(nino), Some(taxYear)))
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.OK,
          responseString = Some("Success"),
          responseJson = Some(jsonData)))

        val urlCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](urlCaptor.capture())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockDesConfig = mock[DesConfig]
        when(mockDesConfig.baseURL)
            .thenReturn("testServiceUrl")

        val sut = createSUT(mockHttpClient,
          mock[Metrics],
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mockDesConfig)

        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(Nino(nino), taxYear)(hc), 5.seconds)

        response.json mustBe jsonData
        urlCaptor.getValue mustBe s"testServiceUrl/pay-as-you-earn/individuals/$nino/tax-account/tax-year/$taxYear?calculation=true"
      }
    }

    "recieve an HttpResponse with status of 404 Not-Found from DES API" when {
      "supplied with a valid nino and year but a taxAccount cannot be found" in {
        val jsonResponse = Future.successful(HttpResponse(
          responseStatus = Status.NOT_FOUND,
          responseString = Some("Not-Found"),
          responseJson = None))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(jsonResponse)

        val sut = createSUT(mockHttpClient, mock[Metrics], mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])
        val response = Await.result(sut.getCalculatedTaxAccountRawResponseFromDes(Nino(nino), taxYear)(hc), 5.seconds)

        response.status mustBe Status.NOT_FOUND
        response.body mustBe "Not-Found"
      }
    }

    "return a status of 200 OK" when {
      "updating employment data in DES using an empty update amount." in {
        val sut = createSUT(mock[HttpClient], mock[Metrics], mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])

        val response = Await.result(sut.updateEmploymentDataToDes(Nino(nino), taxYear, iabdType, 1, Nil)(hc), 5.seconds)

        response.status mustBe Status.OK
      }
    }

    "return a status of 200 OK" when {
      "updating employment data in DES using a valid update amount" in {
        val updateAmount = List(IabdUpdateAmount(1, 20000))
        val jsonResponse = Future.successful(HttpResponse(Status.OK))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[List[IabdUpdateAmount], HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mock[DesConfig])

        val response = Await.result(sut.updateEmploymentDataToDes(Nino(nino), taxYear, iabdType, 1, updateAmount)(hc), 5.seconds)

        response.status mustBe Status.OK
      }
    }

    "return a status of 500 INTERNAL_SERVER_ERROR" when {
      "updating employment data and the update fails in DES with an internal server error" in {
        val updateAmount = List(IabdUpdateAmount(1, 20000))
        val jsonResponse = Future.successful(HttpResponse(Status.INTERNAL_SERVER_ERROR))

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[List[IabdUpdateAmount], HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(jsonResponse)

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSUT(mockHttpClient,
          mockMetrics,
          mock[Auditor],
          mock[IabdUpdateAmountFormats],
          mock[DesConfig])
        
        val response = sut.updateEmploymentDataToDes(Nino(nino), taxYear, iabdType, 1, updateAmount)(hc)

        val ex = the[HttpException] thrownBy Await.result(response, 5.seconds)
        ex.responseCode mustBe Status.INTERNAL_SERVER_ERROR
      }
    }

    "returns the sessionId from a HeaderCarrier" when {
      "HeaderCarrier has an assigned sessionId" in {

        val sut = createSUT(mock[HttpClient], mock[Metrics], mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])
        val testSessionId = "testSessionId"
        val testHeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(testSessionId)))

        val result = sut.sessionOrUUID(testHeaderCarrier)

        result mustBe testSessionId
      }
    }

    "returns a randomly generated sessionId" when {
      "HeaderCarrier has None assigned as a sessionId" in {
        val testHeaderCarrier = HeaderCarrier(sessionId = None)

        val sut = createSUT(mock[HttpClient], mock[Metrics], mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])
        val result = sut.sessionOrUUID(testHeaderCarrier)

        result.length must be > 0
      }
    }


    "updateExpensesDataToDes" should {

      "return a OK" when {
        "updating expenses data to des" in {
          val mockHttpClient = mock[HttpClient]

          when(mockHttpClient.POST[UpdateIabdEmployeeExpense, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
            .thenReturn(Future.successful(HttpResponse(Status.OK)))

          val mockMetrics = mock[Metrics]
          when(mockMetrics.startTimer(any()))
            .thenReturn(mock[Timer.Context])

          val sut: DesConnector = createSUT(mockHttpClient, mockMetrics, mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])

          val result = Await.result(sut.updateExpensesDataToDes(
            nino = Nino(nino),
            year = taxYear,
            iabdType = iabdType,
            version = 1,
            expensesData = List(UpdateIabdEmployeeExpense(100)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
          )(hc), 5.seconds)

          result.status mustBe Status.OK
        }
      }

      "return a Internal Server Error" when {
        "updating expenses data to des" in {
          val mockHttpClient = mock[HttpClient]

          when(mockHttpClient.POST[UpdateIabdEmployeeExpense, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
            .thenReturn(Future.successful(HttpResponse(Status.INTERNAL_SERVER_ERROR)))

          val mockMetrics = mock[Metrics]
          when(mockMetrics.startTimer(any())).thenReturn(mock[Timer.Context])

          val sut: DesConnector = createSUT(mockHttpClient, mockMetrics, mock[Auditor], mock[IabdUpdateAmountFormats], mock[DesConfig])

          val result = sut.updateExpensesDataToDes(
            nino = Nino(nino),
            year = taxYear,
            iabdType = iabdType,
            version = 1,
            expensesData = List(UpdateIabdEmployeeExpense(100)),
            apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI

          )(hc)

          val ex: HttpException = the[HttpException] thrownBy Await.result(result, 5.seconds)
          ex.responseCode mustBe Status.INTERNAL_SERVER_ERROR

        }
      }

    }
  }

  private def createSUT(httpClient: HttpClient,
                        metrics: Metrics,
                        audit: Auditor,
                        formats: IabdUpdateAmountFormats,
                        config: DesConfig) =

    new DesConnector(httpClient, metrics, audit, formats, config) {
    override val originatorId: String = "testOriginatorId"
    override val daPtaOriginatorId: String = "testOriginatorId"
  }

  private implicit val hc = HeaderCarrier()

  private def randomNino: Nino = new Generator(new Random).nextNino
  private val nino: String = randomNino.nino

  private val taxYear: Int = DateTime.now().getYear
  private val iabdType: Int = 27
}
