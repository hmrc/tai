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

import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.play.test.UnitSpec

class UpdateIabdFlatRateExpenseSpec extends UnitSpec {

  "IabdUpdateExpensesAmount" should {

    "parse json correctly" in {

      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "sequenceNumber": 201800001,
          |   "grossAmount": 1234
          | }
        """.stripMargin)

      val employeeExpense: UpdateIabdEmployeeExpense = employeeExpenseJson.as[UpdateIabdEmployeeExpense]

      employeeExpense.grossAmount shouldBe 1234
    }

    "give error when grossAmount field is empty" in {

      val employeeExpenseJson = Json.parse(
        """
          | {}
        """.stripMargin)

      val parseError: JsResultException = intercept[JsResultException] {
        employeeExpenseJson.as[UpdateIabdEmployeeExpense].grossAmount
      }

      parseError shouldBe an[JsResultException]

    }

    "give error when grossAmount field is less than 0" in {

      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "grossAmount": -1
          | }
        """.stripMargin)

      val parseError: IllegalArgumentException = intercept[IllegalArgumentException] {
        employeeExpenseJson.as[UpdateIabdEmployeeExpense].grossAmount
      }

      parseError.getMessage shouldBe "requirement failed: grossAmount cannot be less than 0"

    }

    "give error when grossAmount field is greater than 999999" in {

      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "grossAmount": 1000000
          | }
        """.stripMargin)

      val parseError: IllegalArgumentException = intercept[IllegalArgumentException] {
        employeeExpenseJson.as[UpdateIabdEmployeeExpense].grossAmount
      }

      parseError.getMessage shouldBe "requirement failed: grossAmount cannot be greater than 999999"
    }
  }
}
