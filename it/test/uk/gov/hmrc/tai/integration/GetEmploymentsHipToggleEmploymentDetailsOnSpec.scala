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
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.admin.{HipToggleEmploymentDetails, RtiCallToggle, TaxCodeHistoryFromIfToggle}

import scala.concurrent.{ExecutionContext, Future}

class GetEmploymentsHipToggleEmploymentDetailsOnSpec extends IntegrationSpec {
  implicit lazy val ec: ExecutionContext = inject[ExecutionContext]
  private val mockFeatureFlagService = mock[FeatureFlagService]

  override def beforeEach(): Unit = {
    reset(mockFeatureFlagService)
    super.beforeEach()

    when(mockFeatureFlagService.getAsEitherT(eqTo[FeatureFlagName](RtiCallToggle))).thenReturn(
      EitherT.rightT(FeatureFlag(RtiCallToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromIfToggle))).thenReturn(
      Future.successful(FeatureFlag(TaxCodeHistoryFromIfToggle, isEnabled = false))
    )
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleEmploymentDetails))).thenReturn(
      Future.successful(FeatureFlag(HipToggleEmploymentDetails, isEnabled = true))
    )

    server.stubFor(get(urlEqualTo(hipEmploymentUrl)).willReturn(ok(employmentHipJson)))
    server.stubFor(get(urlEqualTo(rtiUrl)).willReturn(ok(rtiJson)))
  }

  override def fakeApplication(): Application =
    guiceAppBuilder
      .overrides(bind[FeatureFlagService].toInstance(mockFeatureFlagService))
      .build()

  val apiUrl = s"/tai/$nino/employments/years/$year"
  def request = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  "Get Employment" must {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "for nps employments failures" must {
      List(
        BAD_REQUEST           -> BAD_REQUEST,
        NOT_FOUND             -> NOT_FOUND,
        IM_A_TEAPOT           -> INTERNAL_SERVER_ERROR,
        INTERNAL_SERVER_ERROR -> BAD_GATEWAY,
        SERVICE_UNAVAILABLE   -> BAD_GATEWAY
      ).foreach { case (httpStatus, error) =>
        s"return $error when the NPS employments API returns a $httpStatus" in {
          server.stubFor(get(urlEqualTo(hipEmploymentUrl)).willReturn(aResponse().withStatus(httpStatus)))

          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(error)
        }
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return OK for rti API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(rtiUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(OK)
      }
    }
  }
}