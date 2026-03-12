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
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.{HeaderNames, HttpException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.tai.model.admin.RtiCallToggle

class TaxFreeAmountComparisonSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/tax-free-amount-comparison"

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, apiUrl)
      .withHeaders(HeaderNames.xSessionId -> generateSessionId)
      .withHeaders(HeaderNames.authorisation -> bearerToken)

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))

    ()
  }

  "TaxFreeAmountComparison" must {
    "return an OK response for a valid user" in {
      val expected = FileHelper.loadFile("expected/taxFreeAmountComparison-1.json")

      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountHistoryUrl(2))).willReturn(ok(taxAccountHistoryHipJson)))

      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(OK)
      result.map(contentAsJson) mustBe Some(Json.parse(expected))
    }

    "return an OK response when previous coding component is empty" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountHistoryUrl(2))).willReturn(ok(taxAccountHistoryEmptyHipJson)))

      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(OK)

      val json = result.map(contentAsJson).get

      (json \ "data" \ "previous").as[JsArray].value mustBe empty
      (json \ "data" \ "current").as[JsArray].value must not be empty
      (json \ "links").as[JsArray].value mustBe empty
    }

    "return an OK response when current coding component is empty" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountEmptyHipJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountHistoryUrl(2))).willReturn(ok(taxAccountHistoryHipJson)))

      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(OK)

      val json = result.map(contentAsJson).get

      (json \ "data" \ "previous").as[JsArray].value must not be empty
      (json \ "data" \ "current").as[JsArray].value mustBe empty
      (json \ "links").as[JsArray].value mustBe empty
    }

    "for tax-code-history failures" must {
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

    "for tax account failures" must {
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 500, internalServerException, BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 501, new HttpException("", 501), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 502, new HttpException("", 502), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 503, new HttpException("", 503), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 504, new HttpException("", 503), BAD_GATEWAY)

      behave like callWithErrorHandling(request, hipTaxAccountUrl, 400, badRequestException, BAD_REQUEST)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 401, new HttpException("", 401), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 403, new HttpException("", 403), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 409, new HttpException("", 409), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 412, new HttpException("", 412), BAD_GATEWAY)
      behave like callWithErrorHandling(request, hipTaxAccountUrl, 404, notFoundException, NOT_FOUND)
    }
  }
}
