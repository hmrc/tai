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
import org.mockito.MockitoSugar.{reset, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.{HipToggleEmploymentDetails, HipToggleIabds, HipToggleTaxAccount, RtiCallToggle}

import scala.concurrent.Future

class TaxCodeMismatchHipToggleTaxAccountOnSpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(npsIabdsJson)))
    server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
    server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
    server.stubFor(get(urlEqualTo(rtiUrl)).willReturn(ok(rtiJson)))
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleTaxAccount))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = true))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleEmploymentDetails))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
      Future.successful(FeatureFlag(HipToggleIabds, isEnabled = false))
    )

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

    "for nps iabds failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>
        s"return $status when we receive $status downstream" in {
          val npsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year"

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          val npsEmploymentUrl = s"/nps-hod-service/services/nps/person/$nino/employment/$year"
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
          val desTaxCodeHistoryUrl = s"/individuals/tax-code-history/list/$nino/$year?endTaxYear=$year"
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))

          val apiUrl = s"/tai/$nino/tax-account/tax-code-mismatch"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)

          server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>
        s"return $status when we receive $status downstream" in {
          val npsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year"

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          val npsEmploymentUrl = s"/nps-hod-service/services/nps/person/$nino/employment/$year"
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
          val desTaxCodeHistoryUrl = s"/individuals/tax-code-history/list/$nino/$year?endTaxYear=$year"
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))

          val apiUrl = s"/tai/$nino/tax-account/tax-code-mismatch"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)

          server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        val npsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year"

        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        val npsEmploymentUrl = s"/nps-hod-service/services/nps/person/$nino/employment/$year"
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
        val desTaxCodeHistoryUrl = s"/individuals/tax-code-history/list/$nino/$year?endTaxYear=$year"
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))

        val apiUrl = s"/tai/$nino/tax-account/tax-code-mismatch"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)

        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

    "for nps tax account failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>
        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>
        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

    "for tax-code-history failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>
        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>
        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

  }
}
