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
import uk.gov.hmrc.tai.model.admin.RtiCallToggle

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

class TaxAccountSummarySpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))
    server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
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
      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "for hip iabds failures" must {
      s"throw a BAD_REQUEST exception to be handled in error handler when the hip iabds API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

        val apiUrl = s"/tai/$nino/tax-account/$year/summary"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
        result.isFailure mustBe true
        result.failed.get.toString.contains("uk.gov.hmrc.http.BadRequestException") mustBe true
      }

      s"return a NOT_FOUND when the hip iabds API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

        val apiUrl = s"/tai/$nino/tax-account/$year/summary"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
        result.isFailure mustBe true
        result.failed.get.toString.contains("uk.gov.hmrc.http.NotFoundException") mustBe true
      }

      s"throws an InternalServerException when the hip iabds API returns an INTERNAL_SERVER_ERROR" in {

        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

        val apiUrl = s"/tai/$nino/tax-account/$year/summary"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[InternalServerException])
      }

      s"throws a HttpException when the hip iabds API returns a SERVICE_UNAVAILABLE" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

        val apiUrl = s"/tai/$nino/tax-account/$year/summary"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }

      s"throws a HttpException when the hip iabds API returns a IM_A_TEAPOT" in {

        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
        server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))

        val apiUrl = s"/tai/$nino/tax-account/$year/summary"
        val request = FakeRequest(GET, apiUrl)
          .withHeaders(HeaderNames.xSessionId -> generateSessionId)
          .withHeaders(HeaderNames.authorisation -> bearerToken)
        server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(IM_A_TEAPOT)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }

    "for nps tax account failures" must {
      "throws a BAD_REQUEST exception when the NPS tax account API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
        result.isFailure mustBe true
        result.failed.get.toString.contains("uk.gov.hmrc.http.BadRequestException") mustBe true

      }

      "return a NOT_FOUND when the NPS tax account API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
        result.isFailure mustBe true
        result.failed.get.toString.contains("uk.gov.hmrc.http.NotFoundException") mustBe true
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
