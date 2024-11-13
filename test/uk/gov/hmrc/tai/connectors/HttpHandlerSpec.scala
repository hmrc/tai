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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import play.api.libs.json.{JsString, Json, OFormat}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.model.enums.APITypes

import java.net.URLEncoder

class HttpHandlerSpec extends ConnectorBaseSpec {

  private lazy val httpHandler: HttpHandler = inject[HttpHandler]
  private lazy val testUrl: String =
    server.url(s"/testUrl/${URLEncoder.encode("argument(test)", "UTF-8").replace("+", "%20")}")
  private case class ResponseObject(name: String, age: Int)
  private implicit val responseObjectFormat: OFormat[ResponseObject] = Json.format[ResponseObject]
  private val responseBodyObject = ResponseObject("aaa", 24)

  "getFromAPI" must {
    "return valid json" when {
      "when data is successfully received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(Json.toJson(responseBodyObject).toString())
            )
        )

        val response = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

        response mustBe Json.toJson(responseBodyObject)

        server.verify(
          getRequestedFor(anyUrl())
            .withHeader("test", equalTo("testHeader"))
        )
      }
    }

    "result in a BadRequest exception" when {

      "when a BadRequest http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
            )
        )

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[BadRequestException]

      }
    }

    "result in a NotFound exception" when {

      "when a NotFound http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
            )
        )

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[NotFoundException]

      }
    }

    "result in a InternalServerError exception" when {

      "when a InternalServerError http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
            )
        )

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[InternalServerException]

      }
    }

    "result in a Locked exception" when {

      "when a Locked response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(LOCKED)
            )
        )

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[LockedException]
      }
    }

    "result in an HttpException" when {

      "when a unknown error http response is received from the http get call" in {

        server.stubFor(
          WireMock
            .get(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(IM_A_TEAPOT)
            )
        )

        val result = httpHandler.getFromApi(testUrl, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]
      }
    }
  }

  "postToApi" must {

    val userInput = "userInput"

    "return json which is coming from http post call with OK response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(userInput).toString())
          )
      )

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status mustBe OK
      result.json mustBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader"))
      )
    }

    "return json which is coming from http post call with CREATED response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(
            aResponse()
              .withStatus(CREATED)
              .withBody(Json.toJson(userInput).toString())
          )
      )

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status mustBe CREATED
      result.json mustBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader"))
      )

    }

    "return json which is coming from http post call with ACCEPTED response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(
            aResponse()
              .withStatus(ACCEPTED)
              .withBody(Json.toJson(userInput).toString())
          )
      )

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

      result.status mustBe ACCEPTED
      result.json mustBe Json.toJson(userInput)

      server.verify(
        postRequestedFor(anyUrl())
          .withHeader("test", equalTo("testHeader"))
      )

    }

    "return json which is coming from http post call with NO_CONTENT response" in {

      server.stubFor(
        WireMock
          .post(anyUrl())
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).futureValue

      result.status mustBe NO_CONTENT
    }

    "return Http exception" when {
      "http response is NOT_FOUND" in {
        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
            )
        )

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]

      }

      "http response is GATEWAY_TIMEOUT" in {

        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(GATEWAY_TIMEOUT)
            )
        )

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]
      }

      "http response is INTERNAL_SERVER_ERROR" in {

        server.stubFor(
          WireMock
            .post(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
            )
        )

        val result = httpHandler.postToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]
      }
    }
  }

  "putToApi" must {

    val userInput = JsString("userInput")

    Seq(OK, CREATED, ACCEPTED, NO_CONTENT).foreach { responseCode =>
      s"return json which is coming from http post call with $responseCode response" in {
        server.stubFor(
          WireMock
            .put(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(responseCode)
                .withBody(Json.toJson(userInput).toString())
            )
        )

        val result = httpHandler.putToApi(testUrl, userInput, APITypes.RTIAPI, Seq("test" -> "testHeader")).futureValue

        result.status mustBe responseCode
        if (responseCode != NO_CONTENT) {
          result.json mustBe Json.toJson(userInput)
        }
        server.verify(
          putRequestedFor(anyUrl())
            .withHeader("test", equalTo("testHeader"))
        )
      }
    }

    "return Http exception" when {
      "http response is NOT_FOUND" in {
        server.stubFor(
          WireMock
            .put(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
            )
        )

        val result = httpHandler.putToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]

      }

      "http response is GATEWAY_TIMEOUT" in {

        server.stubFor(
          WireMock
            .put(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(GATEWAY_TIMEOUT)
            )
        )

        val result = httpHandler.putToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]
      }

      "http response is INTERNAL_SERVER_ERROR" in {

        server.stubFor(
          WireMock
            .put(anyUrl())
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
            )
        )

        val result = httpHandler.putToApi(testUrl, userInput, APITypes.RTIAPI, Seq.empty).failed.futureValue

        result mustBe a[HttpException]
      }
    }
  }

}
