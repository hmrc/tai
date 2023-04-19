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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, ok, post, urlEqualTo}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.IabdUpdateExpensesRequest

class PostEmployeeExpensesSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/$year/expenses/employee-expenses/59"

  val postRequest = Json.toJson(IabdUpdateExpensesRequest(etag.toInt, 123456))

  def request = FakeRequest(POST, apiUrl).withJsonBody(postRequest)

  val iabdType = 59
  val desIabdsUrl = s"/pay-as-you-earn/individuals/$nino/iabds/$year/$iabdType"

  "Put Employee Expenses" must {
    "return an OK response for a valid user" in {
      server.stubFor(post(desIabdsUrl).willReturn(ok()))

      val result = route(fakeApplication(), request)

      result.map(getStatus) mustBe Some(NO_CONTENT)
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return OK for employment API failures with status code $httpStatus" in {
        server.stubFor(post(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }
  }
}
