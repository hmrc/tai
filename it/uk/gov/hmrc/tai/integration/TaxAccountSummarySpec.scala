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

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, urlEqualTo}
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.http.{HttpException, InternalServerException}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import scala.concurrent.ExecutionContext.Implicits.global

class TaxAccountSummarySpec extends IntegrationSpec {

  override def beforeEach() = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabdsJson)))
    server.stubFor(get(urlEqualTo(npsEmploymentUrl)).willReturn(ok(employmentJson)))
  }

  val apiUrl = s"/tai/$nino/tax-account/$year/summary"
  def request = FakeRequest(GET, apiUrl).withHeaders("X-SESSION-ID" -> generateSessionId)

  "TaxAccountSummary" must {
    "return an OK response for a valid user" in {
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(OK)
    }

    "for nps iabds failures" must {
      "return a BAD_REQUEST when the NPS iabds API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_REQUEST)
      }

      "return a NOT_FOUND when the NPS iabds API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }

      "return a NOT_FOUND when the NPS iabds API returns a NOT_FOUND and NOT_FOUND response is cached" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
        val requestConst = request
        (for {
          _ <- route(app, requestConst).get
          _ <- route(app, requestConst).get
        } yield ()).futureValue

        server.verify(1, WireMock.getRequestedFor(urlEqualTo(npsIabdsUrl)))
      }

      "throws an InternalServerException when the NPS iabds API returns an INTERNAL_SERVER_ERROR" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[InternalServerException])
      }

      "throws a HttpException when the NPS iabds API returns a SERVICE_UNAVAILABLE" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }

      "throws a HttpException when the NPS iabds API returns a IM_A_TEAPOT" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(IM_A_TEAPOT)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }

    "for nps tax account failures" must {
      "return a BAD_REQUEST when the NPS tax account API returns a BAD_REQUEST" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(BAD_REQUEST)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_REQUEST)
      }

      "return a NOT_FOUND when the NPS tax account API returns a NOT_FOUND" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(NOT_FOUND)
      }

      "throws an InternalServerException when the NPS tax account API returns an INTERNAL_SERVER_ERROR" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[InternalServerException])
      }

      "throws a HttpException when the NPS tax account API returns a SERVICE_UNAVAILABLE" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }

      "throws a HttpException when the NPS tax account API returns a IM_A_TEAPOT" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(IM_A_TEAPOT)))

        val result = route(fakeApplication(), request)
        result.map(_.failed.futureValue mustBe a[HttpException])
      }
    }
  }
}
