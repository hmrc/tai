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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{route, status as getStatus, *}
import uk.gov.hmrc.http.{HeaderNames, HttpException, InternalServerException}
import uk.gov.hmrc.tai.integration.utils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.tai.model.admin.HipGetIabdsExpensesToggle
import org.mockito.ArgumentMatchers.eq as eqTo
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import org.mockito.Mockito.when

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.Try

class GetEmployeeExpensesSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/$year/expenses/employee-expenses/59"
  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  val iabdType = 59
  val desIabdsUrl = s"/pay-as-you-earn/individuals/$nino/iabds/tax-year/$year?type=$iabdType"
  override val hipIabdsUrl = s"/v1/api/iabd/taxpayer/$nino/tax-year/$year?type=Other+Expenses+(059)"

  "Get Employment via DES" must {
    "return an OK response for a valid user" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
      )

      val iabdsType59Json = FileHelper.loadFile("iabdsType59.json")

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(ok(iabdsType59Json)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
      result.map(contentAsString) mustBe Some(
        s"""[{"nino":"EE100000","type":59,"grossAmount":0,"source":51,"receiptDate":"26/12/2013","captureDate":"26/12/2013"}]"""
      )
    }

    "return a BAD_REQUEST when iabds from DES returns a BAD_REQUEST" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
      )

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
      result.isFailure mustBe true
      result.failed.get.toString.contains("uk.gov.hmrc.http.BadRequestException") mustBe true
    }

    "return a NOT_FOUND when iabds from DES returns a NOT_FOUND" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
      )

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
      result.isFailure mustBe true
      result.failed.get.toString.contains("uk.gov.hmrc.http.NotFoundException") mustBe true
    }

    "throws an InternalServerException when iabds from DES returns a INTERNAL_SERVER_ERROR" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
      )

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      val result = route(fakeApplication(), request)

      result.map(fResult =>
        whenReady(fResult.failed) { e =>
          e mustBe a[InternalServerException]
        }
      )
    }

    "throws an HttpException when iabds from DES returns a SERVICE_UNAVAILABLE" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
      )

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

      val result = route(fakeApplication(), request)

      result.map(fResult =>
        whenReady(fResult.failed) { e =>
          e mustBe a[HttpException]
        }
      )
    }
  }

  "Get Employment via HIP" must {
    "return an OK response for a valid user" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
      )

      val iabdsType59Json = FileHelper.loadFile("hipiabdsType59.json")

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(ok(iabdsType59Json)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
      result.map(contentAsString) mustBe Some(
        s"""[{"nino":"EE100000","type":59,"grossAmount":0,"source":51,"receiptDate":"26/12/2013","captureDate":"26/12/2013"}]"""
      )
    }

    "return a BAD_REQUEST when iabds from DES returns a BAD_REQUEST" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
      )

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
      result.isFailure mustBe true
      result.failed.get.toString.contains("uk.gov.hmrc.http.BadRequestException") mustBe true
    }

    "return a NOT_FOUND when iabds from HIP returns a NOT_FOUND" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
      )

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = Try(Await.result(route(fakeApplication(), request).get, Duration.Inf))
      result.isFailure mustBe true
      result.failed.get.toString.contains("uk.gov.hmrc.http.NotFoundException") mustBe true
    }

    "throws an InternalServerException when iabds from DES returns a INTERNAL_SERVER_ERROR" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
      )

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

      val result = route(fakeApplication(), request)

      result.map(fResult =>
        whenReady(fResult.failed) { e =>
          e mustBe a[InternalServerException]
        }
      )
    }

    "throws an HttpException when iabds from DES returns a SERVICE_UNAVAILABLE" in {
      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
        Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
      )

      server.stubFor(get(urlEqualTo(hipIabdsUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

      val result = route(fakeApplication(), request)

      result.map(fResult =>
        whenReady(fResult.failed) { e =>
          e mustBe a[HttpException]
        }
      )
    }
  }
}
