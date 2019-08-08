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

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.domain.EmploymentIncome
import uk.gov.hmrc.tai.model.domain.income.{Live, OtherBasisOperation, TaxCodeIncome, Week1Month1BasisOperation}

class TaxCodeIncomeSpec extends PlaySpec {

  "TaxCodeIncomeSource taxCodeWithEmergencySuffix" must {
    "return the taxCode WITH X suffix" when {
      "the basis operation is week1Month1" in {
        val sut = taxCodeIncomeSource
        sut.taxCodeWithEmergencySuffix mustBe "K100X"
      }
    }
    "return the taxCode WITHOUT X suffix" when {
      "the basis operation is NOT week1Month1" in {
        val sut = taxCodeIncomeSource.copy(basisOperation = OtherBasisOperation)
        sut.taxCodeWithEmergencySuffix mustBe "K100"
      }
    }
  }
  val taxCodeIncomeSource =
    TaxCodeIncome(EmploymentIncome, None, 0, "", "K100", "", Week1Month1BasisOperation, Live, 0, 0, 0)
}
