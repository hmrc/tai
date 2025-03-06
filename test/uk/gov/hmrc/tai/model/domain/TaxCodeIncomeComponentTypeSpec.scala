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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.play.PlaySpec

class TaxCodeIncomeComponentTypeSpec extends PlaySpec {

  "TaxCodeIncomeComponentType" must {

    "return correct TaxCodeIncomeComponentType for valid inputs" in {
      TaxCodeIncomeComponentType("EmploymentIncome") mustBe EmploymentIncome
      TaxCodeIncomeComponentType("PensionIncome") mustBe PensionIncome
      TaxCodeIncomeComponentType("JobSeekerAllowanceIncome") mustBe JobSeekerAllowanceIncome
      TaxCodeIncomeComponentType("OtherIncome") mustBe OtherIncome
    }

    "throw IllegalArgumentException for invalid input" in {
      val exception = intercept[IllegalArgumentException] {
        TaxCodeIncomeComponentType("InvalidType")
      }
      exception.getMessage mustBe "Invalid Tax component type"
    }
  }
}
