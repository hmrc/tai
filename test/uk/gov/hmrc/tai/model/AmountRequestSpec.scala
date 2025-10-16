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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*

class AmountRequestSpec extends PlaySpec {

  "AmountRequest JSON serialization" must {
    "serialize and deserialize correctly" in {
      val amountRequest = AmountRequest(BigDecimal(1000.50))
      val json = Json.toJson(amountRequest)

      json.as[AmountRequest] mustBe amountRequest
    }

    "serialize correctly into JSON format" in {
      val amountRequest = AmountRequest(BigDecimal(2500))
      val expectedJson = Json.parse("""{"amount":2500}""")

      Json.toJson(amountRequest) mustBe expectedJson
    }

    "deserialize correctly from JSON format" in {
      val json = Json.parse("""{"amount":750.75}""")
      val expectedObject = AmountRequest(BigDecimal(750.75))

      json.as[AmountRequest] mustBe expectedObject
    }
  }
}
