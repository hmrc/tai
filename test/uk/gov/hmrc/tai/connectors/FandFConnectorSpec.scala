/*
 * Copyright 2026 HM Revenue & Customs
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
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.tai.model.TrustedHelper

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class FandFConnectorSpec extends ConnectorBaseSpec {

  private val trustedHelperNino = "AB123456Q"

  val trustedHelper: TrustedHelper =
    TrustedHelper("principal Name", "attorneyName", "returnLink", Some(trustedHelperNino))

  val fandfTrustedHelperResponse: String =
    s"""
       |{
       |   "principalName": "principal Name",
       |   "attorneyName": "attorneyName",
       |   "returnLinkUrl": "returnLink",
       |   "principalNino": "$trustedHelperNino"
       |}
       |""".stripMargin

  override implicit lazy val app: Application = {
    server.start()
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.fandf.port" -> server.port(),
        "microservice.services.fandf.host" -> "127.0.0.1"
      )
      .build()
  }

  lazy val connector: FandFConnector = app.injector.instanceOf[FandFConnector]

  "Calling FandFConnector.getTrustedHelper" must {

    "return as Some(trustedHelper) when trustedHelper json returned" in {
      server.stubFor(
        WireMock.get(urlEqualTo("/delegation/get")).willReturn(ok(fandfTrustedHelperResponse))
      )

      val result: Option[TrustedHelper] = Await.result(connector.getTrustedHelper(), Duration.Inf)

      result mustBe Some(trustedHelper)
    }

    "return as Some(trustedHelper) when invalid json returned" in {
      server.stubFor(
        WireMock.get(urlEqualTo("/delegation/get")).willReturn(ok("Nonsense response"))
      )

      val result: Option[TrustedHelper] = Await.result(connector.getTrustedHelper(), Duration.Inf)

      result mustBe None
    }

    "return as None when not found returned" in {
      server.stubFor(
        WireMock.get(urlEqualTo("/delegation/get")).willReturn(notFound())
      )

      val result: Option[TrustedHelper] = Await.result(connector.getTrustedHelper(), Duration.Inf)

      result mustBe None
    }

    "return None when error status returned" in {
      server.stubFor(
        WireMock.get(urlEqualTo("/delegation/get")).willReturn(serverError())
      )
      val result = Await.result(connector.getTrustedHelper(), Duration.Inf)

      result mustBe None
    }

    "return None when unexpected status returned" in {
      server.stubFor(
        WireMock.get(urlEqualTo("/delegation/get")).willReturn(noContent())
      )
      val result = Await.result(connector.getTrustedHelper(), Duration.Inf)

      result mustBe None
    }
  }
}
