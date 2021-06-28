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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import org.scalatest.concurrent.ScalaFutures
import play.api.Configuration
import play.api.http.Status._
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Await
import scala.concurrent.duration._

class IabdConnectorSpec extends ConnectorBaseSpec with ScalaFutures {

  class StubbedFeatureTogglesConfig(enabled: Boolean) extends FeatureTogglesConfig(inject[Configuration]) {
    override def desEnabled: Boolean = enabled
  }

  def sut(desEnabled: Boolean): IabdConnector = new IabdConnector(
    inject[NpsConfig],
    inject[DesConfig],
    inject[HttpHandler],
    inject[IabdUrls],
    new StubbedFeatureTogglesConfig(desEnabled)
  )

  val taxYear: TaxYear = TaxYear()

  val desUrl: String = s"/pay-as-you-earn/individuals/${nino.nino}/iabds/tax-year/${taxYear.year}"
  val npsUrl: String = s"/nps-hod-service/services/nps/person/${nino.nino}/iabds/${taxYear.year}"

  private val json = Json.arr(
    Json.obj(
      "nino"            -> nino.withoutSuffix,
      "taxYear"         -> 2017,
      "type"            -> 10,
      "source"          -> 15,
      "grossAmount"     -> JsNull,
      "receiptDate"     -> JsNull,
      "captureDate"     -> "10/04/2017",
      "typeDescription" -> "Total gift aid Payments",
      "netAmount"       -> 100
    )
  )

  "IABD Connector" when {

    "toggled to use NPS" must {
      "return IABD json" in {

        server.stubFor(
          get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
        )

        Await.result(sut(false).iabds(nino, taxYear), 5.seconds) mustBe json
      }

      "return empty json" when {
        "looking for next tax year" in {

          server.stubFor(
            get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          Await.result(sut(false).iabds(nino, taxYear.next), 5.seconds) mustBe Json.arr()
        }

        "looking for cy+2 year" in {

          server.stubFor(
            get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          Await.result(sut(false).iabds(nino, taxYear.next.next), 5.seconds) mustBe Json.arr()
        }
      }
    }

    "generate an exception" when {
      "a 400 occurs in the connector" in {

        server.stubFor(
          get(urlEqualTo(npsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST))
        )

        sut(false).iabds(nino, taxYear.next.next).failed.futureValue mustBe a[HttpException]

      }
    }

    "toggled to use DES" must {

      "return IABD json" in {

        server.stubFor(
          get(urlEqualTo(desUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
        )

        Await.result(sut(true).iabds(nino, taxYear), 5.seconds) mustBe json
      }

      "return empty json" when {
        "looking for next tax year" in {
          server.stubFor(
            get(urlEqualTo(desUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          Await.result(sut(true).iabds(nino, taxYear.next), 5.seconds) mustBe Json.arr()
        }

        "looking for cy+2 year" in {
          server.stubFor(
            get(urlEqualTo(desUrl)).willReturn(aResponse().withStatus(OK).withBody(json.toString()))
          )

          Await.result(sut(true).iabds(nino, taxYear.next.next), 5.seconds) mustBe Json.arr()
        }
      }
    }
  }
}
