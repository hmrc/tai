/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.tai.util.BaseSpec

class RawResponseReadsSpec extends BaseSpec with RawResponseReads {

  "RawResponseReads" must {

    "return the same HttpResponse that was passed to it" in {
      val method = "GET"
      val url = "https://example.com/test"
      val response = HttpResponse(200, "Test Response")

      val result = httpReads.read(method, url, response)

      result mustBe response
    }

    "correctly handle different HTTP statuses" in {
      val response500 = HttpResponse(500, "Internal Server Error")
      val response400 = HttpResponse(400, "Bad Request")
      val response204 = HttpResponse(204, "")

      httpReads.read("GET", "https://example.com/500", response500) mustBe response500
      httpReads.read("POST", "https://example.com/400", response400) mustBe response400
      httpReads.read("DELETE", "https://example.com/204", response204) mustBe response204
    }
  }
}
