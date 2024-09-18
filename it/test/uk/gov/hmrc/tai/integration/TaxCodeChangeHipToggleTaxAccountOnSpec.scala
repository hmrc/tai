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

package uk.gov.hmrc.tai.integration

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar.{mock, reset, when}
import play.api.Application
import play.api.http.Status._
import play.api.inject.bind
import play.api.libs.json.{JsArray, JsBoolean, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsJson, defaultAwaitTimeout, route, status => getStatus, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.integration.utils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.tai.model.admin.{HipToggleEmploymentDetails, HipToggleTaxAccount, RtiCallToggle, TaxCodeHistoryFromIfToggle}
import uk.gov.hmrc.tai.model.domain.income.BasisOperation.Week1Month1
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class TaxCodeChangeHipToggleTaxAccountOnSpec extends IntegrationSpec {
  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  val apiUrl = s"/tai/$nino/tax-account/tax-code-change"
  def request = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  val apiUrlHasChanged = s"/tai/$nino/tax-account/tax-code-change/exists"
  def requestHasChanged = FakeRequest(GET, apiUrlHasChanged)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  private val mockFeatureFlagService = mock[FeatureFlagService]
  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromIfToggle))).thenReturn(
      Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleTaxAccount))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = true))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleEmploymentDetails))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = false))
    )
  }

  override def fakeApplication(): Application =
    guiceAppBuilder
      .overrides(bind[FeatureFlagService].toInstance(mockFeatureFlagService))
      .build()

  "TaxCodeChange" must {
    "return an OK response for a valid user" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    List(500, 501, 502, 503, 504).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_GATEWAY)
      }
    }

    List(400, 401, 403, 409, 412).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    "return 404 when we receive 404 from downstream - desTaxCodeHistoryUrl" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(NOT_FOUND)
    }
  }

  "hasTaxCodeChanged" must {
    "return true" when {
      "There is a tax code change and no mismatch" in {
        val taxCodeHistory = FileHelper
          .loadFile("nino1/tax-code-history.json")
          .replace("<cyDate1>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyDate2>", TaxYear().start.plusMonths(2).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val taxAccount = FileHelper
          .loadFile("nino1/tax-account-hip.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val taxAccountNps = FileHelper
          .loadFile("nino1/tax-account.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val iabds = FileHelper
          .loadFile("nino1/iabds.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountNps)))
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabds)))

        val result = route(fakeApplication(), requestHasChanged).get
        getStatus(result) mustBe OK
        contentAsJson(result) mustBe JsBoolean(true)
      }

      "There is a tax code change and basisOfOperation is week1/month1" in {
        val taxCodeHistory = FileHelper
          .loadFile("nino1/tax-code-history.json")
          .replace("<cyDate1>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyDate2>", TaxYear().start.plusMonths(2).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)
          .replace("Cumulative", Week1Month1)

        val taxAccount = FileHelper
          .loadFile("nino1/tax-account-hip.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)
          .replace(""""basisOfOperation":"Cumulative",""", """"basisOfOperation":"Week1/Month1",""")

        val iabds = FileHelper
          .loadFile("nino1/iabds.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabds)))

        val result = route(fakeApplication(), requestHasChanged).get
        getStatus(result) mustBe OK
        contentAsJson(result) mustBe JsBoolean(true)
      }
    }

    "return false" when {
      "There is only 1 tax code in history" in {
        val taxCodeHistory = Json.parse(
          FileHelper
            .loadFile("nino1/tax-code-history.json")
            .replace("<cyDate1>", TaxYear().start.plusMonths(1).toString)
            .replace("<cyDate2>", TaxYear().start.plusMonths(2).toString)
            .replace("<cyYear>", TaxYear().start.getYear.toString)
        )

        val newTaxCodeHistory = Json
          .obj(
            "nino"          -> "NINO1",
            "taxCodeRecord" -> JsArray(Seq((taxCodeHistory \ "taxCodeRecord").get(0)))
          )
          .toString

        val taxAccount = FileHelper
          .loadFile("nino1/tax-account-hip.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val iabds = FileHelper
          .loadFile("nino1/iabds.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(newTaxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabds)))

        val result = route(fakeApplication(), requestHasChanged).get
        getStatus(result) mustBe OK
        contentAsJson(result) mustBe JsBoolean(false)
      }

      "There is no tax code in history" in {
        val taxCodeHistory = Json
          .obj(
            "nino"          -> "NINO1",
            "taxCodeRecord" -> JsArray(Seq.empty)
          )
          .toString

        val taxAccount = FileHelper
          .loadFile("nino1/tax-account-hip.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val iabds = FileHelper
          .loadFile("nino1/iabds.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabds)))

        val result = route(fakeApplication(), requestHasChanged).get
        getStatus(result) mustBe OK
        contentAsJson(result) mustBe JsBoolean(false)
      }

      "There is a tax code change and a mismatch" in {
        val taxCodeHistory = FileHelper
          .loadFile("nino1/tax-code-history.json")
          .replace("<cyDate1>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyDate2>", TaxYear().start.plusMonths(2).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        val taxAccount = FileHelper
          .loadFile("nino1/tax-account-hip.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).toString)
          .replace("<cyYear>", TaxYear().start.getYear.toString)
          .replace("1257L", "1000L")

        val iabds = FileHelper
          .loadFile("nino1/iabds.json")
          .replace("<cyDate>", TaxYear().start.plusMonths(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
          .replace("<cyYear>", TaxYear().start.getYear.toString)

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabds)))

        val result = route(fakeApplication(), requestHasChanged).get
        getStatus(result) mustBe OK
        contentAsJson(result) mustBe JsBoolean(false)
      }

    }
  }
}