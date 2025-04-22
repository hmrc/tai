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
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, when}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.{HeaderNames, HttpException, InternalServerException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.{HipToggleIabds, RtiCallToggle}

import scala.concurrent.Future

class TaxAccountSummarySpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(npsIabdsJson)))
    server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
      Future.successful(FeatureFlag(HipToggleIabds, isEnabled = false))
    )
    ()
  }

  val apiUrl = s"/tai/$nino/tax-account/$year/summary"

  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  "TaxAccountSummary" must {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "return an OK response for a valid user with Iabds from HIP" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
        Future.successful(FeatureFlag(HipToggleIabds, isEnabled = true))
      )

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    case class APIInfo(name: String, url: String, toggleStatus: Boolean)
    List(
      APIInfo("nps", npsIabdsUrl, false),
      APIInfo("hip", hipIabdsUrl, true)
    ).foreach { api =>
      s"for ${api.name} iabds failures" must {
        s"return a BAD_REQUEST when the ${api.name} iabds API returns a BAD_REQUEST" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(BAD_REQUEST)))

          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_REQUEST)
        }

        s"return a NOT_FOUND when the ${api.name} iabds API returns a NOT_FOUND" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(NOT_FOUND)))

          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(NOT_FOUND)
        }

        s"return a NOT_FOUND when the ${api.name} iabds API returns a NOT_FOUND and NOT_FOUND response is cached" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(NOT_FOUND)))
          val requestConst = request
          (for {
            _ <- route(app, requestConst).get
            _ <- route(app, requestConst).get
          } yield ()).futureValue

          server.verify(1, WireMock.getRequestedFor(urlEqualTo(api.url)))
        }

        s"throws an InternalServerException when the ${api.name} iabds API returns an INTERNAL_SERVER_ERROR" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

          val result = route(fakeApplication(), request)
          result.map(_.failed.futureValue mustBe a[InternalServerException])
        }

        s"throws a HttpException when the ${api.name} iabds API returns a SERVICE_UNAVAILABLE" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

          val result = route(fakeApplication(), request)
          result.map(_.failed.futureValue mustBe a[HttpException])
        }

        s"throws a HttpException when the ${api.name} iabds API returns a IM_A_TEAPOT" in {
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
            Future.successful(FeatureFlag(HipToggleIabds, isEnabled = api.toggleStatus))
          )

          server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
          server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

          val apiUrl = s"/tai/$nino/tax-account/$year/summary"
          val request = FakeRequest(GET, apiUrl)
            .withHeaders(HeaderNames.xSessionId -> generateSessionId)
            .withHeaders(HeaderNames.authorisation -> bearerToken)
          server.stubFor(get(urlEqualTo(api.url)).willReturn(aResponse().withStatus(IM_A_TEAPOT)))

          val result = route(fakeApplication(), request)
          result.map(_.failed.futureValue mustBe a[HttpException])
        }
      }
    }

    "for nps tax account failures" must {
      "return a BAD_REQUEST when the NPS tax account API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_REQUEST)
      }

      "return a NOT_FOUND when the NPS tax account API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }

      "throws an InternalServerException when the NPS tax account API returns an INTERNAL_SERVER_ERROR" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[InternalServerException])
      }

      "throws a HttpException when the NPS tax account API returns a SERVICE_UNAVAILABLE" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }

      "throws a HttpException when the NPS tax account API returns a IM_A_TEAPOT" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(IM_A_TEAPOT)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }
  }
}
