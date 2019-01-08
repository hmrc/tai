/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.enums

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNumber, JsString}
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation

class APITypesSpec extends PlaySpec {

  "enumFormat" must {
    "read from JSON" when {
      "the value is a valid Int" in {
        val json = JsNumber(1)
        json.as[BasisOperation] mustBe BasisOperation.Week1Month1
      }
      "the value is a valid string representing the enum value" in {
        val json = JsString("Week1Month1")
        json.as[BasisOperation] mustBe BasisOperation.Week1Month1
      }
    }
    "throw Exception" when {
      "the JSON is invalid Int" in {
        val json = JsNumber(5)
        val exception = the[RuntimeException] thrownBy json.as[BasisOperation]
        exception.getMessage mustBe "Invalid BasisOperation Type"
      }
      "the JSON is invalid String" in {
        val json = JsString("AAA")
        the[NoSuchElementException] thrownBy json.as[BasisOperation]
      }
    }
  }
}
