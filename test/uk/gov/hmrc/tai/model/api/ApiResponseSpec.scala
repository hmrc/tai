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

package uk.gov.hmrc.tai.model.api

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json

class ApiResponseSpec extends PlaySpec {

  implicit val testFormat = Json.format[TestObject]

  "ApiResponse" must {
    "produce a valid json message with a data section" when {
      "given a valid object" in {

        val sut = ApiResponse(TestObject("test name", 33), Nil)

        val resp = Json.toJson(sut)

        resp mustBe Json.obj("data" -> Json.obj("name" -> "test name", "age" -> 33), "links" -> Json.arr())
      }
    }
    "produce a valid json message with links" when {
      "given a single link" in {

        val sut = ApiResponse(3, List(ApiLink("/tai/tax-payer", "self")))

        val resp = Json.toJson(sut)

        resp mustBe Json.obj(
          "data"  -> 3,
          "links" -> Json.arr(Json.obj("uri" -> "/tai/tax-payer", "rel" -> "self", "method" -> "GET")))
      }

      "given multiple links" in {

        val sut =
          ApiResponse("hello", List(ApiLink("/tai/tax-payer", "self"), ApiLink("/tai/tax-payers", "create", "POST")))

        val resp = Json.toJson(sut)

        resp mustBe Json.obj(
          "data" -> "hello",
          "links" -> Json.arr(
            Json.obj("uri" -> "/tai/tax-payer", "rel"  -> "self", "method"   -> "GET"),
            Json.obj("uri" -> "/tai/tax-payers", "rel" -> "create", "method" -> "POST"))
        )
      }
    }
  }
}

case class TestObject(name: String, age: Int)
