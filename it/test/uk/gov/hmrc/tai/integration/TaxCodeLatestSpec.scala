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
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{status as getStatus, *}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.tai.integration.utils.{IntegrationSpec, TaxCodeRecordFactory}
import uk.gov.hmrc.tai.model.api.{ApiResponse, TaxCodeSummary}
import uk.gov.hmrc.tai.model.tai.TaxYear

class TaxCodeLatestSpec extends IntegrationSpec {

  val apiUrl = s"/tai/$nino/tax-account/$year/tax-code/latest"

  def request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, apiUrl)
    .withHeaders(HeaderNames.xSessionId -> generateSessionId)
    .withHeaders(HeaderNames.authorisation -> bearerToken)

  "TaxCodeChange" must {
    "return an OK response for a valid user with the body containing the latest tax code summary" in {
      val record1 = TaxCodeRecordFactory.createPrimaryEmployment()
      val record1Json = TaxCodeRecordFactory.createPrimaryEmploymentJson()
      val summary = TaxCodeSummary.apply(record1, TaxYear().end)
      val historyJson = Json.obj("nino" -> nino.toString, "taxCodeRecord" -> Seq(record1Json))

      val expectedBody = ApiResponse(Seq(summary), Seq.empty)
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(ok(historyJson.toString())))
      val result = route(fakeApplication(), request)
      result.map { response =>
        contentAsJson(response) mustBe Json.toJson(expectedBody)
        getStatus(response) mustBe OK
      }

    }

    List(500, 501, 502, 503, 504).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(BAD_GATEWAY)
      }
    }

    List(400, 401, 403, 409, 412).foreach { status =>
      s"return $status when we receive $status downstream - desTaxCodeHistoryUrl" in {
        server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(status)))
        val result = route(fakeApplication(), request)
        result.map(getStatus) mustBe Some(INTERNAL_SERVER_ERROR)
      }
    }

    "return 404 when we receive 404 from downstream - desTaxCodeHistoryUrl" in {
      server.stubFor(get(urlEqualTo(desTaxCodeHistoryUrl)).willReturn(aResponse().withStatus(NOT_FOUND)))
      val result = route(fakeApplication(), request)
      result.map(getStatus) mustBe Some(NOT_FOUND)
    }
  }
}
