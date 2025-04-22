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
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.RtiCallToggle

import scala.concurrent.Future

class TaxFreeAmountComparisonSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/tax-free-amount-comparison"
  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
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

  "TaxFreeAmountComparison" must {
    "return an OK response for a valid user" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    List(500, 501, 502, 503, 504).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_GATEWAY)
      }
    }

    List(400, 401, 403, 409, 412).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    "return 404 when we receive 404 from downstream - desTaxCodeHistoryUrl" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(NOT_FOUND)
    }

    List(500, 501, 502, 503, 504).foreach { status =>
      s"return $status when we receive $status downstream - npsTaxAccountUrl" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_GATEWAY)
      }
    }

    List(400, 401, 403, 409, 412).foreach { status =>
      s"return $status when we receive $status downstream - npsTaxAccountUrl" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    "return 404 when we receive 404 from downstream - npsTaxAccountUrl" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
      server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxCodeHistoryJson)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(NOT_FOUND)
    }
  }
}
