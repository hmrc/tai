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
import com.github.tomakehurst.wiremock.client.WireMock.{status as _, *}
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, when}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.*
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.nps2.IabdType.hipMapping

import scala.concurrent.Future

class UpdateIncomeSpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(ok(taxAccountHipJson)))
    server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(hipIabdsJson)))
    server.stubFor(get(urlEqualTo(cidEtagUrl)).willReturn(ok(etagJson.toString)))

    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    ()
  }

  private val employmentId = 1
  private val apiUrl = s"/tai/$nino/tax-account/snapshots/$year/incomes/tax-code-incomes/$employmentId/estimated-pay"

  private val postRequest = Json.toJson(UpdateTaxCodeIncomeRequest(123456))

  private def request = FakeRequest(PUT, apiUrl)
    .withJsonBody(postRequest)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  private val iabdTypeArgument = hipMapping(27)
  val putNpsIabdsUrl = s"/v1/api/iabd/taxpayer/$nino/tax-year/$year/employment/1/type/$iabdTypeArgument"

  "Update Income" must {
    "return an OK response for a valid user" in {
      server.stubFor(put(putNpsIabdsUrl).willReturn(ok()))
      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(OK)
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR when the NPS IABDs POST fails with status code $httpStatus" in {
        server.stubFor(put(putNpsIabdsUrl).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)

        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for CID API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(cidEtagUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for Tax Account API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(hipTaxAccountUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for IABDs API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
