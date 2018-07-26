/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.{Configuration, Environment}
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.controllers.taxCodeChange.TaxCodeChangeController
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.service.TaxCodeChangeService
import uk.gov.hmrc.tai.util.WireMockHelper

import scala.util.Random

class TaxCodeChangeSpec extends PlaySpec with MockitoSugar with WireMockHelper with MockAuthenticationPredicate {

  "for a GET for a nino with a tax code change" should {
    "return true for hasTaxCodeChanged" in {

      val testNino = new Generator(new Random).nextNino
      val host = "localhost"
      val port = 9332
      val taxYearLow = 1
      val url = s"http://$host:$port/nps-json-service/nps/itmp/personal-tax-account/tax-code/history/api/v1/$testNino/$taxYearLow"

      val stubResponse = Json.obj(
        "nino" -> "",
        "taxHistoryList" -> Seq(
          Json.obj(
            "employmentId" -> 1234567890,
            "p2Issued" -> true
          )
        )
      )

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(stubResponse.toString))
      )

      val response = controller.hasTaxCodeChanged(testNino)(FakeRequest())

      contentAsJson(response) mustBe Json.obj("hasTaxCodeChanged" -> true)

    }
  }


  "for a GET for a nino without a tax code change" should {
    "return false for hasTaxCodeChanged" in {
      val testNino = new Generator(new Random).nextNino

      val host = "localhost"
      val port = 9332
      val taxYearLow = 1
      val url = s"http://$host:$port/nps-json-service/nps/itmp/personal-tax-account/tax-code/history/api/v1/$testNino/$taxYearLow"

      val p2NotIssuedFalse = Json.obj(
        "nino" -> "",
        "taxHistoryList" -> Seq(
          Json.obj(
            "employmentId" -> 1234567890,
            "p2Issued" -> false
          )
        )
      )

      server.stubFor(
        get(urlEqualTo(url)).willReturn(ok(p2NotIssuedFalse.toString))
      )

      val response = controller.hasTaxCodeChanged(testNino)(FakeRequest())

      contentAsJson(response) mustBe Json.obj("hasTaxCodeChanged" -> false)

    }
  }


  val p2IssuedNotPresent = Json.obj(
    "nino" -> "",
    "taxHistoryList" -> Seq(
      Json.obj("employmentId" -> 1234567890)
    )
  )

  val noTaxCodeChanges = Json.obj(
    "nino" -> "",
    "taxHistoryList" -> Seq.empty[JsObject]
  )

  implicit val hc = HeaderCarrier()

  val mockTaxCodeService = mock[TaxCodeChangeService]
  val featureToggler = new FeatureTogglesConfig(mock[Configuration], mock[Environment])
  private def controller = new TaxCodeChangeController(loggedInAuthenticationPredicate, mockTaxCodeService, featureToggler)
}
