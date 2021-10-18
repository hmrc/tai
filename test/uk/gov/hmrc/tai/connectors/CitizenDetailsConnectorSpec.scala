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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.ETag

class CitizenDetailsConnectorSpec extends ConnectorBaseSpec {

  lazy val sut: CitizenDetailsConnector = inject[CitizenDetailsConnector]

  val baseUrl: String = s"/citizen-details/${nino.nino}"
  val designatoryDetailsUrl: String = s"$baseUrl/designatory-details"
  val eTagUrl: String = s"$baseUrl/etag"

  val etag: String = "123"
  val etagJson: JsValue = Json.parse(s"""
                                        |{
                                        |   "etag":"$etag"
                                        |}
    """.stripMargin)

  "getEtag" must {
    "return an etag on success" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(etagJson.toString)
          )
      )

      sut.getEtag(nino).futureValue mustBe Some(ETag(etag))
    }

    "return a None for bad json" in {
      val badJson: JsValue = Json.parse(s"""
                                           |{
                                           |   "not an etag":"$etag"
                                           |}
    """.stripMargin)

      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(badJson.toString)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      IM_A_TEAPOT
    ).foreach { httpResponse =>
      s"return a None when a $httpResponse occurs" in {

        server.stubFor(
          get(urlEqualTo(eTagUrl))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
            )
        )

        sut.getEtag(nino).futureValue mustBe None
      }
    }

    "return None on an unrecoverable error, possibly bad data received from the upstream API" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record not found" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(NOT_FOUND)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }

    "return None when record was hidden, due to manual correspondence indicator flag being set" in {
      server.stubFor(
        get(urlEqualTo(eTagUrl))
          .willReturn(
            aResponse()
              .withStatus(LOCKED)
          )
      )

      sut.getEtag(nino).futureValue mustBe None
    }
  }
}
