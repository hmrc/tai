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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, ok, put, urlEqualTo}
import org.mockito.ArgumentMatchersSugar.eqTo
import org.mockito.MockitoSugar.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.{HeaderNames, HttpException}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.IabdUpdateExpensesRequest
import uk.gov.hmrc.tai.model.admin.HipToggleIabds

import scala.concurrent.Future

class PutEmployeeExpensesSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/$year/expenses/employee-expenses/27"

  val putRequest = Json.toJson(IabdUpdateExpensesRequest(etag.toInt, 123456))

  override def beforeEach(): Unit = {
    super.beforeEach()

    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleIabds))).thenReturn(
      Future.successful(FeatureFlag(HipToggleIabds, isEnabled = true))
    )
  }

  def request = FakeRequest(POST, apiUrl)
    .withJsonBody(putRequest)
    .withHeaders(HeaderNames.authorisation -> bearerToken, HeaderNames.xSessionId -> "sessionId")

  val iabdType = "New-Estimated-Pay-(027)"
  val hipIabdsPutUrl = s"/v1/api/iabd/taxpayer/$nino/tax-year/$year/type/$iabdType"

  "Put Employee Expenses" must {
    "return an OK response for a valid user" in {
      server.stubFor(put(hipIabdsPutUrl).willReturn(ok()))

      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(NO_CONTENT)
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return OK for employment API failures with status code $httpStatus" in {
        server.stubFor(put(urlEqualTo(hipIabdsPutUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }
  }
}
