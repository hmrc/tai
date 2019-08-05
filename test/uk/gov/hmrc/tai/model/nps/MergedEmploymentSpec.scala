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

package uk.gov.hmrc.tai.model.nps

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.{Tax, TaxCodeIncomeSummary}
import uk.gov.hmrc.tai.util.TaiConstants

class MergedEmploymentSpec extends PlaySpec {

  "orderField" should {
    "return primary employment constant appended with string 'No Name Supplied'" when {
      "an empty NpsIncomeSource is supplied which defaults to a primary employment" in {
        val sut = createSUT(NpsIncomeSource(), None, None)
        val result = sut.orderField
        result mustBe s"${TaiConstants.PrimaryEmployment}-No Name Supplied"
      }
    }

    "return secondary employment constant appended with string 'No Name Supplied'" when {
      "an NpsIncomeSource with employment type set to None" in {
        val sut = createSUT(NpsIncomeSource(employmentType = None), None, None)
        val result = sut.orderField
        result mustBe s"${TaiConstants.SecondaryEmployment}-No Name Supplied"
      }
    }

    "return the supplied number set as employment type appended with a supplied income source name" when {
      "an NpsIncomeSource with employment type set to None" in {
        val incomeSourceName = "testIncomeSource"
        val employmentType = 123
        val sut =
          createSUT(NpsIncomeSource(name = Some(incomeSourceName), employmentType = Some(employmentType)), None, None)
        val result = sut.orderField
        result mustBe s"$employmentType-$incomeSourceName"
      }
    }
  }

  "toTaxCodeIncomeSummary" should {
    "return a TaxCodeIncomeSummary set to primary employment and is editable and is live is also set" when {
      "an empty NpsIncomeSource object is provided" in {
        val sut = createSUT(NpsIncomeSource(), None, None)
        val result = sut.toTaxCodeIncomeSummary
        result mustBe TaxCodeIncomeSummary(
          name = "",
          taxCode = "",
          tax = Tax(),
          employmentType = Some(TaiConstants.PrimaryEmployment),
          incomeType = Some(0),
          isEditable = true,
          isLive = true)
      }
    }
  }

  private def createSUT(
    incomeSource: NpsIncomeSource,
    employment: Option[NpsEmployment] = None,
    adjustedNetIncome: Option[BigDecimal] = None) =
    MergedEmployment(incomeSource, employment, adjustedNetIncome)
}
