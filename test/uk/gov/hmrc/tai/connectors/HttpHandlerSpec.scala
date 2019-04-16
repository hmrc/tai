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

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.Json
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.WireMockHelper
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class HttpHandlerSpec extends PlaySpec with MockitoSugar with WireMockHelper with BeforeAndAfterAll {

  val testNino = randomNino
  val taxYear = TaxYear(2017)
  val json = Json.obj()

  implicit val hc: HeaderCarrier = HeaderCarrier()
  lazy val handler = injector.instanceOf[HttpHandler]
  lazy val urlConfig = injector.instanceOf[TaxAccountUrls]

  def randomNino: Nino = new Generator(new Random).nextNino

  def url = new URL(urlConfig.taxAccountUrl(testNino, taxYear))

  def getResponse = Await.result(handler.getFromApi(url.toString, APITypes.NpsTaxAccountAPI), 5 seconds)

  def postResponse = Await.result(handler.postToApi(url.toString, "user input", APITypes.NpsTaxAccountAPI), 5 seconds)

  "getFromAPI" should {
    "return valid json" when {
      "when data is successfully received from the http get call" in {


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
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.BAD_REQUEST).withBody(errorMessage))
        )

        val thrown = the[BadRequestException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in a NotFound exception" when {

      "when a NotFound http response is received from the http get call" in {

        val errorMessage = "not found"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.NOT_FOUND).withBody(errorMessage))
        )

        val thrown = the[NotFoundException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in a InternalServerError exception" when {

      "when a InternalServerError http response is received from the http get call" in {

        val errorMessage = "internal server error"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.INTERNAL_SERVER_ERROR).withBody(errorMessage))
        )

        val thrown = the[InternalServerException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage
      }
    }

    "result in a Locked exception" when {

      "when a Locked response is received from the http get call" in {

        val errorMessage = "locked"

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.LOCKED).withBody(errorMessage))
        )

        val thrown = the[LockedException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }

    "result in an HttpException" when {

      "when a unknown error http response is received from the http get call" in {

        val errorMessage = "unknown response"
        val unknownStatus = 418

        server.stubFor(
          get(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(unknownStatus).withBody(errorMessage))
        )

        val thrown = the[HttpException] thrownBy getResponse

        thrown.getMessage mustEqual errorMessage

      }
    }
  }


  "postToApi" should {
    "return valid json for an OK response" in {

      server.stubFor(post(urlEqualTo(url.getPath)).willReturn(ok(json.toString()))
      )

      postResponse.json mustBe Json.toJson(json)
    }

    "return valid json for an CREATED response" in {

      server.stubFor(post(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.CREATED).withBody(json.toString()))
      )

      postResponse.json mustBe Json.toJson(json)
    }

    "return valid json for an ACCEPTED response" in {

      server.stubFor(post(urlEqualTo(url.getPath)).willReturn(aResponse().withStatus(Status.ACCEPTED).withBody(json.toString()))
      )

      postResponse.json mustBe Json.toJson(json)
    }

    "return valid json for an NO_CONTENT response" in {

      server.stubFor(post(urlEqualTo(url.getPath)).willReturn(ok(json.toString()).withStatus(Status.NO_CONTENT))
      )

      postResponse.status mustBe Status.NO_CONTENT
    }

    "return Http exception" when {
      "http response is NOT_FOUND" in {

        server.stubFor(post(urlEqualTo(url.getPath)).willReturn(ok(json.toString()).withStatus(Status.NOT_FOUND))
        )

        val thrown = the[HttpException] thrownBy postResponse
        thrown.responseCode mustBe Status.NOT_FOUND

      }

      "http response is GATEWAY_TIMEOUT" in {

        server.stubFor(post(urlEqualTo(url.getPath)).willReturn(ok(json.toString()).withStatus(Status.GATEWAY_TIMEOUT))
        )

        val thrown = the[HttpException] thrownBy postResponse
        thrown.responseCode mustBe Status.GATEWAY_TIMEOUT

      }
    }
  }

}