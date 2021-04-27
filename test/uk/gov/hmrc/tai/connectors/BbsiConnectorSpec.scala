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
import play.api.libs.json.Json
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TestMetrics

import scala.concurrent.Await
import scala.concurrent.duration._

class BbsiConnectorSpec extends ConnectorBaseSpec {

  private val taxYear = TaxYear()

  val mockHttp: HttpClient = mock[HttpClient]

  lazy val bbsiUrls: BbsiUrls = inject[BbsiUrls]
  lazy val desConfig: DesConfig = inject[DesConfig]

  lazy val url = s"/pre-population-of-investment-income/nino/${nino.withoutSuffix}/tax-year/${taxYear.year}"

  lazy val metrics: TestMetrics = new TestMetrics
  lazy val sut = new BbsiConnector(metrics, inject[HttpClient], bbsiUrls, desConfig)
  lazy val sutWithMockHttp = new BbsiConnector(metrics, mockHttp, bbsiUrls, desConfig)

  "BbsiConnector" when {

    "bankAccounts is called" must {

      "return a 200 response" in {

        val json = """{"message": "Success"}"""

        server.stubFor(
          get(urlEqualTo(url)).willReturn(aResponse().withStatus(OK).withBody(json))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe OK
        result.json mustBe Json.parse(json)
      }

      "return a 400 response" in {

        val exMessage = "Text from reason column"

        server.stubFor(
          get(urlEqualTo(url))
            .willReturn(badRequest().withBody(exMessage))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe BAD_REQUEST
        result.body mustBe exMessage
      }

      "return a 404 response" in {

        val exMessage = "Could not find bank accounts"

        server.stubFor(
          get(urlEqualTo(url)).willReturn(notFound().withBody(exMessage))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe NOT_FOUND
        result.body mustBe exMessage
      }

      "return a 500 response" in {

        val exMessage = "An error occurred"

        server.stubFor(
          get(urlEqualTo(url)).willReturn(serverError().withBody(exMessage))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe INTERNAL_SERVER_ERROR
        result.body mustBe exMessage
      }

      "return a 503 response" in {

        val exMessage = "Service is unavailable"

        server.stubFor(
          get(urlEqualTo(url)).willReturn(serviceUnavailable().withBody(exMessage))
        )

        val result = Await.result(sut.bankAccounts(nino, taxYear), 5.seconds)

        result.status mustBe SERVICE_UNAVAILABLE
        result.body mustBe exMessage
      }
    }
  }
}
