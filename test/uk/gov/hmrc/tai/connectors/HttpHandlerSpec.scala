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
import uk.gov.hmrc.tai.util.{TaiConstants, WireMockHelper}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, equalTo, getRequestedFor, matching, postRequestedFor, urlEqualTo}

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

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(responseBodyObject).toString())))

        val response = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

        response shouldBe Json.toJson(responseBodyObject)

        server.verify(
          getRequestedFor(anyUrl())
            .withHeader("test", equalTo("testHeader")))
      }
    }

    "result in a BadRequest exception" when {

      "when a BadRequest http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(BAD_REQUEST)))

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[BadRequestException]

      }
    }

    "result in a NotFound exception" when {

      "when a NotFound http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(NOT_FOUND)))

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[NotFoundException]

      }
    }

    "result in a InternalServerError exception" when {

      "when a InternalServerError http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)))

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[InternalServerException]

      }
    }

    "result in a Locked exception" when {

      "when a Locked response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(LOCKED)))

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[LockedException]
      }
    }

    "result in an HttpException" when {

      "when a unknown error http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(aResponse()
              .withStatus(IM_A_TEAPOT)))

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[HttpException]
      }
    }
  }

  "postToApi" should {

    val userInput = "userInput"

    "return json which is coming from http post call with OK response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(userInput).toString())))

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status shouldBe OK
      result.json shouldBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader")))
    }

    "return json which is coming from http post call with CREATED response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(aResponse()
            .withStatus(CREATED)
            .withBody(Json.toJson(userInput).toString())))

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status shouldBe CREATED
      result.json shouldBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader")))

    }

    "return json which is coming from http post call with ACCEPTED response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(aResponse()
            .withStatus(ACCEPTED)
            .withBody(Json.toJson(userInput).toString())))

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status shouldBe ACCEPTED
      result.json shouldBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader")))

    }

    "return json which is coming from http post call with NO_CONTENT response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(aResponse()
            .withStatus(NO_CONTENT)))

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).futureValue

      result.status shouldBe NO_CONTENT
    }

    "return Http exception" when {
      "http response is NOT_FOUND" in {
        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(aResponse()
              .withStatus(NOT_FOUND)))

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[HttpException]

      }

      "http response is GATEWAY_TIMEOUT" in {

        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(aResponse()
              .withStatus(GATEWAY_TIMEOUT)))

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[HttpException]
      }

      "http response is INTERNAL_SERVER_ERROR" in {

        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)))

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result shouldBe a[HttpException]
      }
    }
  }
}
