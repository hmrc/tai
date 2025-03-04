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

package uk.gov.hmrc.tai.model.rti

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._

class RtiStatusSpec extends PlaySpec {

  "RtiStatus JSON serialization" must {

    "serialize and deserialize correctly" in {
      val rtiStatus = RtiStatus(status = 200, response = "Success")

      val json = Json.toJson(rtiStatus)
      json mustBe Json.parse("""{"status":200,"response":"Success"}""")

      json.as[RtiStatus] mustBe rtiStatus
    }

    "handle different statuses and responses" in {
      val rtiStatus = RtiStatus(status = 404, response = "Not Found")

      val json = Json.toJson(rtiStatus)
      json mustBe Json.parse("""{"status":404,"response":"Not Found"}""")

      json.as[RtiStatus] mustBe rtiStatus
    }

    "fail when required fields are missing" in {
      val invalidJson = Json.parse("""{"status":500}""")
      assertThrows[JsResultException] {
        invalidJson.as[RtiStatus]
      }
    }
  }
}
