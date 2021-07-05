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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, post, urlEqualTo}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => getStatus, _}
import uk.gov.hmrc.tai.integration.utils.IntegrationSpec
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest

import java.util.UUID

class UpdateIncomeSpec extends IntegrationSpec {

  override def beforeEach(): Unit = {
    super.beforeEach()

    server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(ok(taxAccountJson)))
    server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(ok(iabdsJson)))
    server.stubFor(get(urlEqualTo(cidEtagUrl)).willReturn(ok(etagJson.toString)))
  }

  val employmentId = 1
  val apiUrl = s"/tai/$nino/tax-account/snapshots/$year/incomes/tax-code-incomes/$employmentId/estimated-pay"

  val amount = 123
  val postRequest = Json.toJson(UpdateTaxCodeIncomeRequest(123456))

  def request = FakeRequest(PUT, apiUrl).withJsonBody(postRequest).withHeaders("X-SESSION-ID" -> generateSessionId)

  val iabdType = 27
  val postNpsIabdsUrl = s"/nps-hod-service/services/nps/person/$nino/iabds/$year/employment/$iabdType"

  "Update Income" should {
    "return an OK response for a valid user" in {
      server.stubFor(post(postNpsIabdsUrl).willReturn(ok()))

      val result = route(fakeApplication(), request)

      result.map(getStatus) shouldBe Some(OK)
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR when the NPS IABDs POST fails with status code $httpStatus" in {
        server.stubFor(post(postNpsIabdsUrl).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)

        result.map(getStatus) shouldBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for CID API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(cidEtagUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for Tax Account API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsTaxAccountUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    List(BAD_REQUEST, NOT_FOUND, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { httpStatus =>
      s"return INTERNAL_SERVER_ERROR for IABDs API failures with status code $httpStatus" in {
        server.stubFor(get(urlEqualTo(npsIabdsUrl)).willReturn(aResponse().withStatus(httpStatus)))

        val result = route(fakeApplication(), request)
        result.map(getStatus) shouldBe Some(INTERNAL_SERVER_ERROR)
      }
    }
  }
}
