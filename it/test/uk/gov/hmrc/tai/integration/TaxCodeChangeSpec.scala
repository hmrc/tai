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
import cats.instances.future.*
import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo}
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, when}
import play.api.http.Status.*
import play.api.libs.json.{JsArray, JsBoolean, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{BAD_GATEWAY, BAD_REQUEST, GET, NOT_FOUND, contentAsJson, defaultAwaitTimeout, route, status as getStatus, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.http.{HeaderNames, HttpException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.tai.model.admin.RtiCallToggle
import uk.gov.hmrc.tai.model.domain.income.BasisOperation.Week1Month1
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.format.DateTimeFormatter
import scala.concurrent.Future

class TaxCodeChangeSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/tax-code-change"
  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  val apiUrlHasChanged = s"/tai/$nino/tax-account/tax-code-change/exists"
  def requestHasChanged: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrlHasChanged)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    ()

  }

  "TaxCodeChange" must {
    "return an OK response for a valid user" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 500, internalServerException, BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 501, new HttpException("", 501), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 502, new HttpException("", 502), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 503, new HttpException("", 503), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 504, new HttpException("", 503), BAD_GATEWAY)

    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 400, badRequestException, BAD_REQUEST)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 401, new HttpException("", 401), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 403, new HttpException("", 403), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 409, new HttpException("", 409), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 412, new HttpException("", 412), BAD_GATEWAY)
    behave like callWithErrorHandling(request, desTaxCodeHistoryUrl, 404, notFoundException, NOT_FOUND)
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

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountNps)))
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

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

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

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

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(newTaxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

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

        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistory)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccount)))
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

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
          .loadFile("nino1/iabdsNps.json")
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
