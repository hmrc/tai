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

import com.codahale.metrics.Timer
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.MustMatchers.convertToAnyMustWrapper
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import play.api.http.Status._
import play.api.libs.json.{JsString, Json}
import play.api.test.Injecting
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, equalTo, getRequestedFor, urlEqualTo}

class HttpHandlerSpec
    extends WordSpec with WireMockHelper with Matchers with MockitoSugar with Injecting with ScalaFutures
    with IntegrationPatience {

  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  lazy val httpHandler = inject[HttpHandler]

  lazy val testUrl = server.url("testUrl")
  private case class ResponseObject(name: String, age: Int)
  private implicit val responseObjectFormat = Json.format[ResponseObject]
  private val responseBodyObject = ResponseObject("aaa", 24)

  "getFromAPI" should {
    "return valid json" when {
      "when data is successfully received from the http get call" in {

//        val mockTimerContext = mock[Timer.Context]
//
//        val mockMetrics = mock[Metrics]
//        when(mockMetrics.startTimer(any()))
//          .thenReturn(mockTimerContext)

        //   val mockHttp = mock[HttpClient]

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(responseBodyObject).toString())))

        //  val SUT = createSUT(mockMetrics, mockHttp)
        val response = Await.result(httpHandler.getFromApi(testUrl, APITypes.RTIAPI), 5 seconds)

        response shouldBe Json.toJson(responseBodyObject)

        //  verify(mockHttp, times(1)).GET(meq(testUrl))(any(), any(), any())
//        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
//        verify(mockMetrics, times(1)).incrementSuccessCounter(APITypes.RTIAPI)
//        verify(mockMetrics, never()).incrementFailedCounter(any())
//        verify(mockTimerContext, times(1)).stop()
      }
    }

    "result in a BadRequest exception" when {

      "when a BadRequest http response is received from the http get call" in {
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockHttp = mock[HttpClient]
        when(mockHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.failed(new BadRequestException("bad request")))

        val SUT = createSUT(mockMetrics, mockHttp)
        val ex = the[BadRequestException] thrownBy Await.result(SUT.getFromApi("", APITypes.RTIAPI), 5 seconds)

        ex.message mustBe "bad request"

        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
        verify(mockMetrics, never()).incrementSuccessCounter(any())
        verify(mockMetrics, times(1)).incrementFailedCounter(APITypes.RTIAPI)
        verify(mockTimerContext, times(1)).stop()
      }
    }

    "result in a NotFound exception" when {

      "when a NotFound http response is received from the http get call" in {
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockHttp = mock[HttpClient]
        when(mockHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.failed(new NotFoundException("not found")))

        val SUT = createSUT(mockMetrics, mockHttp)
        val ex = the[NotFoundException] thrownBy Await.result(SUT.getFromApi("", APITypes.RTIAPI), 5 seconds)

        ex.message mustBe "not found"

        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
        verify(mockMetrics, never()).incrementSuccessCounter(any())
        verify(mockMetrics, times(1)).incrementFailedCounter(APITypes.RTIAPI)
        verify(mockTimerContext, times(1)).stop()
      }
    }

    "result in a InternalServerError exception" when {

      "when a InternalServerError http response is received from the http get call" in {
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockHttp = mock[HttpClient]
        when(mockHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.failed(new InternalServerException("internal server error")))

        val SUT = createSUT(mockMetrics, mockHttp)
        val ex = the[InternalServerException] thrownBy Await.result(SUT.getFromApi("", APITypes.RTIAPI), 5 seconds)

        ex.message mustBe "internal server error"

        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
        verify(mockMetrics, never()).incrementSuccessCounter(any())
        verify(mockMetrics, times(1)).incrementFailedCounter(APITypes.RTIAPI)
        verify(mockTimerContext, times(1)).stop()
      }
    }

    "result in a Locked exception" when {

      "when a Locked response is received from the http get call" in {
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockHttp = mock[HttpClient]
        when(mockHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.failed(new LockedException("locked")))

        val SUT = createSUT(mockMetrics, mockHttp)
        val ex = the[LockedException] thrownBy Await.result(SUT.getFromApi("", APITypes.RTIAPI), 5 seconds)

        ex.message mustBe "locked"

        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
        verify(mockMetrics, times(1)).incrementSuccessCounter(any())
        verify(mockMetrics, never()).incrementFailedCounter(APITypes.RTIAPI)
        verify(mockTimerContext, times(1)).stop()
      }
    }

    "result in an HttpException" when {

      "when a unknown error http response is received from the http get call" in {
        val mockTimerContext = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val mockHttp = mock[HttpClient]
        when(mockHttp.GET[HttpResponse](any())(any(), any(), any()))
          .thenReturn(Future.failed(new HttpException("unknown response", responseCode = 418)))

        val SUT = createSUT(mockMetrics, mockHttp)
        val ex = the[HttpException] thrownBy Await.result(SUT.getFromApi("", APITypes.RTIAPI), 5 seconds)

        ex.message mustBe "unknown response"

        verify(mockMetrics, times(1)).startTimer(APITypes.RTIAPI)
        verify(mockMetrics, never()).incrementSuccessCounter(any())
        verify(mockMetrics, times(1)).incrementFailedCounter(APITypes.RTIAPI)
        verify(mockTimerContext, times(1)).stop()
      }
    }
  }

  "postToApi" should {
    val mockUrl = "mockUrl"
    val userInput = "userInput"

    "return json which is coming from http post call" in {
      val mockHttp = mock[HttpClient]
      when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(OK, Json.toJson(userInput), Map[String, Seq[String]]())))
        .thenReturn(Future.successful(HttpResponse(CREATED, Json.toJson(userInput), Map[String, Seq[String]]())))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED, Json.toJson(userInput), Map[String, Seq[String]]())))
        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, Json.toJson(userInput), Map[String, Seq[String]]())))

      val SUT = createSUT(mock[Metrics], mockHttp)
      val okResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
      val createdResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
      val acceptedResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
      val noContentResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)

      okResponse.status mustBe OK
      okResponse.json mustBe Json.toJson(userInput)

      createdResponse.status mustBe CREATED
      createdResponse.json mustBe Json.toJson(userInput)

      acceptedResponse.status mustBe ACCEPTED
      acceptedResponse.json mustBe Json.toJson(userInput)

      noContentResponse.status mustBe NO_CONTENT
      noContentResponse.json mustBe Json.toJson(userInput)
    }

    "return Http exception" when {
      "http response is NOT_FOUND" in {
        val mockMetrics = mock[Metrics]
        val mockHttp = mock[HttpClient]

        val SUT = createSUT(mockMetrics, mockHttp)
        when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(NOT_FOUND, "")))

        val result = the[HttpException] thrownBy Await
          .result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)

        result.responseCode mustBe NOT_FOUND
      }

      "http response is GATEWAY_TIMEOUT" in {
        val mockMetrics = mock[Metrics]
        val mockHttp = mock[HttpClient]

        val SUT = createSUT(mockMetrics, mockHttp)

        when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(GATEWAY_TIMEOUT, "")))

        val result = the[HttpException] thrownBy Await
          .result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)

        result.responseCode mustBe GATEWAY_TIMEOUT
      }
    }
  }

  private val SuccessfulGetResponseWithObject: HttpResponse =
    HttpResponse(200, Some(Json.toJson(responseBodyObject)), Map("ETag"                      -> Seq("34")))
  private val BadRequestHttpResponse = HttpResponse(400, JsString("bad request"), Map("ETag" -> Seq("34")))
  private val NotFoundHttpResponse: HttpResponse =
    HttpResponse(404, JsString("not found"), Map("ETag"                                           -> Seq("34")))
  private val LockedHttpResponse: HttpResponse = HttpResponse(423, JsString("locked"), Map("ETag" -> Seq("34")))
  private val InternalServerErrorHttpResponse: HttpResponse =
    HttpResponse(500, JsString("internal server error"), Map("ETag" -> Seq("34")))
  private val UnknownErrorHttpResponse: HttpResponse =
    HttpResponse(418, JsString("unknown response"), Map("ETag" -> Seq("34")))

  private def createSUT(metrics: Metrics, http: HttpClient) = new HttpHandler(metrics, http)

}
