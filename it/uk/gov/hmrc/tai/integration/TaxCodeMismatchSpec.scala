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

class TaxCodeMismatchSpec extends IntegrationSpec {

  override def beforeEach() = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabdsJson)))
    server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(taxCodeHistoryJson)))
  }

  val apiUrl = s"/tai/$nino/tax-account/tax-code-mismatch"
  def request = FakeRequest(GET, apiUrl).withHeaders("X-SESSION-ID" -> generateSessionId)

  "TaxCodeMismatch" must {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "for nps iabds failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

    "for nps tax account failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

    "for tax-code-history failures" must {

      List(500, 501, 502, 503, 504).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(BAD_GATEWAY)
        }
      }

      List(400, 401, 403, 409, 412).foreach { status =>

        s"return $status when we receive $status downstream" in {
          server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
          val result = route(fakeApplication(), request)
          result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
        }
      }

      "return 404 when we receive 404 from downstream" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }
    }

  }
}
