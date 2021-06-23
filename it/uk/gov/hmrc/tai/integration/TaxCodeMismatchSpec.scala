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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, findUnmatchedRequests, get, urlEqualTo}
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.{HttpException, InternalServerException}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec

class TaxCodeMismatchSpec extends IntegrationSpec {

  val url = s"/tai/$nino/tax-account/tax-code-mismatch"
  def request = FakeRequest(GET, url).withHeaders("X-SESSION-ID" -> "test-session-id")

  "TaxCodeMismatch" should {
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
      s"return OK for iabd API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(OK)
      }
    }

    "for tax-code-history failures" should {
      "return a BAD_REQUEST when the tax-code-history API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(taxCodeHistoryUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(BAD_REQUEST)
      }

      "return a NOT_FOUND when the tax-code-history API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(taxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(NOT_FOUND)
      }

      "throws an  InternalServerException when the tax-code-history API returns an INTERNAL_SERVER_ERROR" in {
        server.stubFor(get(urlEqualTo(taxCodeHistoryUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue shouldBe a[InternalServerException])
      }

      "throws a HttpException when the tax-code-history API returns a SERVICE_UNAVAILABLE" in {
        server.stubFor(get(urlEqualTo(taxCodeHistoryUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue shouldBe a[HttpException])
      }
    }
  }
}
