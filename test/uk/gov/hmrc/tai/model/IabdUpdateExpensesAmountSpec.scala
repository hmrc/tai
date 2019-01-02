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

import play.api.libs.json.Json
import uk.gov.hmrc.play.test.UnitSpec

class IabdUpdateExpensesAmountSpec extends UnitSpec {
  "IabdUpdateExpensesAmount" should {
    "parse json correctly" in {
      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "grossAmount": 1234,
          |   "receiptDate": "01/01/2018"
          | }
        """.stripMargin)

      val employeeExpense: IabdUpdateExpensesAmount = employeeExpenseJson.as[IabdUpdateExpensesAmount]

      employeeExpense.grossAmount shouldBe 1234
      employeeExpense.receiptDate shouldBe Some("01/01/2018")
    }

    "parse json correctly when optional field is not present" in {
      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "grossAmount": 1234
          | }
        """.stripMargin)

      val employeeExpense: IabdUpdateExpensesAmount = employeeExpenseJson.as[IabdUpdateExpensesAmount]

      employeeExpense.grossAmount shouldBe 1234
      employeeExpense.receiptDate shouldBe None
    }

    "give error when grossAmount field is less than 0" in {
      val employeeExpenseJson = Json.parse(
        """
          | {
          |   "grossAmount": -1
          | }
        """.stripMargin)

      val parseError: IllegalArgumentException = intercept[IllegalArgumentException] {
        employeeExpenseJson.as[IabdUpdateExpensesAmount].grossAmount
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
        employeeExpenseJson.as[IabdUpdateExpensesAmount].grossAmount
      }

      parseError.getMessage shouldBe "requirement failed: grossAmount cannot be greater than 999999"
    }
  }
}
