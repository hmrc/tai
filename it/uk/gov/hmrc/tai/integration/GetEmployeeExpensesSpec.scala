/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.tai.integration.utils.{FileHelper, IntegrationSpec}

class GetEmployeeExpensesSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/$year/expenses/employee-expenses/59"
  def request = FakeRequest(GET, apiUrl)

  val iabdType = 59
  val desIabdsUrl = s"/pay-as-you-earn/individuals/$nino/iabds/tax-year/$year?type=$iabdType"

  "Get Employment" should {
    "return an OK response for a valid user" in {
      val iabdsType59Json = FileHelper.loadFile("iabdsType59.json")

      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(ok(iabdsType59Json)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(OK)
    }

    "return a BAD_REQUEST when iabds from DES returns a BAD_REQUEST" in {
      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(BAD_REQUEST)
    }

    "return a NOT_FOUND when iabds from DES returns a NOT_FOUND" in {
      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(NOT_FOUND)
    }

    "throws an InternalServerException when iabds from DES returns a INTERNAL_SERVER_ERROR" in {
      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(NOT_FOUND)
    }

    "throws an HttpException when iabds from DES returns a SERVICE_UNAVAILABLE" in {
      server.stubFor(get(urlEqualTo(desIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(NOT_FOUND)
    }
  }
}
