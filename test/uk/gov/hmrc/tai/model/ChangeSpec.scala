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

package uk.gov.hmrc.tai.model

import java.lang.IllegalArgumentException
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue, Json}

class ChangeSpec extends PlaySpec {
  "changeReads" should {
    "return a valid Change[A,B]" when {
      "given valid json" in {
        val response = validJson.as[Change[Int, String]]

        response.currentYear mustBe 12
        response.currentYearPlusOne mustBe "the value"
      }
    }
    "throw an exception" when {
      "given the wrong json" in {
        val exception = the[IllegalArgumentException] thrownBy invalidJson.as[Change[Int, String]]
        exception.getMessage mustBe "Expected a JsObject, found \"the value\""
      }
    }
  }
  "changeWrites" should {
    "return valid json" when {
      "given a valid Change[A,B]" in {
        val json = Json.toJson(Change[Int, String](12, "the value"))
        json mustBe validJson
      }
    }
  }

  val validJson = JsObject(Map("currentYear" -> JsNumber(12), "currentYearPlusOne" -> JsString("the value")))

  val invalidJson = JsString("the value")
}
