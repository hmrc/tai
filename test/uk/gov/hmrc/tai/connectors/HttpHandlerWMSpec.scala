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

import java.net.URL

import com.codahale.metrics.Timer
import com.github.tomakehurst.wiremock.client.WireMock.{get,post, ok, aResponse, urlEqualTo}
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status._
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class HttpHandlerWMSpec extends PlaySpec with MockitoSugar with WireMockHelper with BeforeAndAfterAll {

  val testNino = randomNino
  val taxYear = TaxYear(2017)

  def url = new URL(urlConfig.taxAccountUrl(testNino, taxYear))
  def getResponse = Await.result(handler.getFromApi(url.toString, APITypes.NpsTaxAccountAPI), 5 seconds)
  def postResponse = Await.result(handler.postToApi(url.toString, "user input", APITypes.NpsTaxAccountAPI), 5 seconds)

  "getFromAPI" should {
    "return valid json" when {
      "when data is successfully received from the http get call" in {

        val json = Json.obj()

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(ok(json.toString()))
        )

        getResponse mustBe Json.toJson(json)
      }
    }

    "result in a BadRequest exception" when {

      "when a BadRequest http response is received from the http get call" in {

        val errorMessage = "bad request"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(400).withBody(errorMessage))
        )

        val thrown = the[BadRequestException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in a NotFound exception" when {

      "when a NotFound http response is received from the http get call" in {

        val errorMessage = "not found"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(404).withBody(errorMessage))
        )

        val thrown = the[NotFoundException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in a InternalServerError exception" when {

      "when a InternalServerError http response is received from the http get call" in {

        val errorMessage = "internal server error"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(500).withBody(errorMessage))
        )

        val thrown = the[InternalServerException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage
      }
    }

    "result in a Locked exception" when {

      "when a Locked response is received from the http get call" in {

        val errorMessage = "locked"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(423).withBody(errorMessage))
        )

        val thrown = the[LockedException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in an HttpException" when {

      "when a unknown error http response is received from the http get call" in {

        val errorMessage = "unknown response"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(418).withBody(errorMessage))
        )

        val thrown = the[HttpException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }


 // "postToApi" should {
//    //val mockUrl = "mockUrl"
//    //val userInput = "userInput"
//
//    "return json which is coming from http post call" in {
//
//      val json = Json.obj()
//
//      server.stubFor(post(urlEqualTo(url.getPath)).willReturn(ok(json.toString()))
//      )
//
//      postResponse mustBe Json.toJson(json)


//      val mockHttp = mock[HttpClient]
//      when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
//        .thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(userInput)))))
//        .thenReturn(Future.successful(HttpResponse(CREATED, Some(Json.toJson(userInput)))))
//        .thenReturn(Future.successful(HttpResponse(ACCEPTED, Some(Json.toJson(userInput)))))
//        .thenReturn(Future.successful(HttpResponse(NO_CONTENT, Some(Json.toJson(userInput)))))
//
//      val SUT = createSUT(mock[Metrics], mockHttp)
//      val okResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//      val createdResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//      val acceptedResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//      val noContentResponse = Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//
//      okResponse.status mustBe OK
//      okResponse.json mustBe Json.toJson(userInput)
//
//      createdResponse.status mustBe CREATED
//      createdResponse.json mustBe Json.toJson(userInput)
//
//      acceptedResponse.status mustBe ACCEPTED
//      acceptedResponse.json mustBe Json.toJson(userInput)
//
//      noContentResponse.status mustBe NO_CONTENT
//      noContentResponse.json mustBe Json.toJson(userInput)
//    }
  }
//
//    "return Http exception" when{
//      "http response is NOT_FOUND"in {
//        val mockMetrics = mock[Metrics]
//        val mockHttp = mock[HttpClient]
//
//        val SUT = createSUT(mockMetrics, mockHttp)
//        when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
//          thenReturn(Future.successful(HttpResponse(NOT_FOUND)))
//
//        val result = the[HttpException] thrownBy Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//
//        result.responseCode mustBe NOT_FOUND
//      }
//
//      "http response is GATEWAY_TIMEOUT" in {
//        val mockMetrics = mock[Metrics]
//        val mockHttp = mock[HttpClient]
//
//        val SUT = createSUT(mockMetrics, mockHttp)
//
//        when(mockHttp.POST[String, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
//          thenReturn(Future.successful(HttpResponse(GATEWAY_TIMEOUT)))
//
//        val result = the[HttpException] thrownBy Await.result(SUT.postToApi[String](mockUrl, userInput, APITypes.RTIAPI), 5 seconds)
//
//        result.responseCode mustBe GATEWAY_TIMEOUT
//      }
//    }
//  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private case class ResponseObject(name: String, age: Int)
  private implicit val responseObjectFormat = Json.format[ResponseObject]
  private val responseBodyObject = ResponseObject("aaa", 24)

//  private val SuccessfulGetResponseWithObject: HttpResponse = HttpResponse(200, Some(Json.toJson(responseBodyObject)), Map("ETag" -> Seq("34")))
//  private val BadRequestHttpResponse = HttpResponse(400, Some(JsString("bad request")), Map("ETag" -> Seq("34")))
//  private val NotFoundHttpResponse: HttpResponse = HttpResponse(404, Some(JsString("not found")), Map("ETag" -> Seq("34")))
//  private val LockedHttpResponse: HttpResponse = HttpResponse(423, Some(JsString("locked")), Map("ETag" -> Seq("34")))
//  private val InternalServerErrorHttpResponse: HttpResponse = HttpResponse(500, Some(JsString("internal server error")), Map("ETag" -> Seq("34")))
//  private val UnknownErrorHttpResponse: HttpResponse = HttpResponse(418, Some(JsString("unknown response")), Map("ETag" -> Seq("34")))


  private lazy val handler = injector.instanceOf[HttpHandler]



  private lazy val urlConfig = injector.instanceOf[TaxAccountUrls]

  private def randomNino: Nino = new Generator(new Random).nextNino

}