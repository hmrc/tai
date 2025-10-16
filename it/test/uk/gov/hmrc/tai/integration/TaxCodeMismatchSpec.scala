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
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.{HeaderNames, HttpException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.RtiCallToggle

class TaxCodeMismatchSpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))
    server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
    server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
    server.stubFor(get(urlEqualTo(rtiUrl)).willReturn(ok(rtiJson)))
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    ()

  }

  val apiUrl = s"/tai/$nino/tax-account/tax-code-mismatch"
  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  "TaxCodeMismatch" must {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "for nps tax account failures" must {
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

  }
}
