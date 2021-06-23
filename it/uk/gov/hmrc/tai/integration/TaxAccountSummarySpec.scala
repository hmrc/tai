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
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec

class TaxAccountSummarySpec extends IntegrationSpec {

  override def beforeEach() = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabdsJson)))
  }

  val apiUrl = s"/tai/$nino/tax-account/$year/summary"
  def request = FakeRequest(GET, apiUrl).withHeaders("X-SESSION-ID" -> "test-session-id")

  "TaxAccountSummary" should {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) shouldBe Some(OK)
    }

    List(BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return OK for tax-account API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(OK)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return OK for iabds API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(OK)
      }
    }
  }
}
