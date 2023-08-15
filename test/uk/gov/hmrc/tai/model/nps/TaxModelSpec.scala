/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.tai.model.enums.IncomeType._
import uk.gov.hmrc.tai.model.helpers.IncomeHelper
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live, PotentiallyCeased}
import uk.gov.hmrc.tai.util.TaiConstants

/*
  * Created by dev01 on 08/07/14.
  */
class TaxModelSpec extends PlaySpec {

  "totalIncome" must {
    "return the Estimated Income from NpsIncomeSource" in {

      val payAndTax = NpsTax(
        Some(NpsComponent(Some(BigDecimal(333.33)), Some(11), None, Some("npsDescription"))),
        None,
        Some(BigDecimal(222.22)),
        Some(BigDecimal(111.111)),
        None)
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        Some(1),
        None,
        None,
        Some(payAndTax),
        None,
        None,
        None,
        None,
        Some(false),
        Some(false))

      npsIncomeSource.toTaxCodeIncomeSummary().tax.totalIncome mustBe Some(BigDecimal(333.33))

    }

    "return the Estimated Income from NpsIncomeSource with No PayAndTax" in {

      val npsIncomeSource =
        new NpsIncomeSource(None, None, Some(1), None, None, None, None, None, None, None, Some(false), Some(false))

      npsIncomeSource.toTaxCodeIncomeSummary().tax.totalIncome mustBe None
      npsIncomeSource.toTaxCodeIncomeSummary().tax.totalTax mustBe None
      npsIncomeSource.toTaxCodeIncomeSummary().tax.totalTaxableIncome mustBe None

    }
  }

  "NpsComponent" must {
    "return the New TaxCompenent when the data is empty" in {
      val personalAllowance = NpsComponent().toTaxComponent(None)

      personalAllowance.amount mustBe BigDecimal(0)
      personalAllowance.componentType mustBe 0
      personalAllowance.description mustBe ""
      personalAllowance.iabdSummaries.size mustBe 0
    }

    "return the New TaxCompenent when we have data" in {
      val iadb = NpsIabdSummary(Some(BigDecimal(333.66)), Some(12), Some("npsIabdSummaryDesc"), Some(1))

      val personalAllowance =
        NpsComponent(Some(BigDecimal(333.33)), Some(11), Some(List(iadb)), Some("npsDescription")).toTaxComponent(None)

      personalAllowance.amount mustBe BigDecimal(333.33)
      personalAllowance.componentType mustBe 11
      personalAllowance.description mustBe "npsDescription"
      personalAllowance.iabdSummaries.size mustBe 1
      personalAllowance.iabdSummaries.lift(0).get.amount mustBe BigDecimal(333.66)
      personalAllowance.iabdSummaries.lift(0).get.iabdType mustBe 12
      personalAllowance.iabdSummaries.lift(0).get.description mustBe "npsIabdSummaryDesc"
    }
  }

  "income source type" must {
    "be set to employment if the income source is an employment" in {
      val npsIncomeSource =
        new NpsIncomeSource(None, None, None, None, None, None, None, None, None, None, Some(false), Some(false))
      npsIncomeSource.incomeType mustBe IncomeTypeEmployment.code
    }

    "be set to employment if the income source has no settings" in {
      val npsIncomeSource = new NpsIncomeSource(
        name = None,
        taxCode = None,
        employmentType = Some(1),
        allowances = None,
        deductions = None,
        payAndTax = None,
        employmentId = None,
        employmentStatus = Some(Live.code))
      npsIncomeSource.incomeType mustBe IncomeTypeEmployment.code
      IncomeHelper.isLive(npsIncomeSource.employmentStatus) mustBe true
    }

    "be set to ceased employment if the income source has no settings and is set to ceased" in {
      val npsIncomeSource = new NpsIncomeSource(
        name = None,
        taxCode = None,
        employmentType = Some(1),
        allowances = None,
        deductions = None,
        payAndTax = None,
        employmentId = None,
        employmentStatus = Some(Ceased.code))
      npsIncomeSource.incomeType mustBe IncomeTypeEmployment.code
      IncomeHelper.isLive(npsIncomeSource.employmentStatus) mustBe false
    }

    "be set to ceased employment if the income source has no settings and is set to potentially ceased" in {
      val npsIncomeSource = new NpsIncomeSource(
        name = None,
        taxCode = None,
        employmentType = Some(1),
        allowances = None,
        deductions = None,
        payAndTax = None,
        employmentId = None,
        employmentStatus = Some(PotentiallyCeased.code)
      )
      npsIncomeSource.incomeType mustBe IncomeTypeEmployment.code
      IncomeHelper.isLive(npsIncomeSource.employmentStatus) mustBe false
    }

    "be set to other pension if the income source is an other pension" in {
      val npsIncomeSource =
        new NpsIncomeSource(None, None, Some(1), None, None, None, None, None, None, None, Some(true), Some(false))
      npsIncomeSource.incomeType mustBe IncomeTypeOccupationalPension.code
    }

    "be set to other pension if the income source is a Pension: Financial Assistance Scheme" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        Some(TaiConstants.PrimaryEmployment),
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT),
        Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER),
        Some(false),
        Some(false)
      )

      npsIncomeSource.incomeType mustBe IncomeTypeOccupationalPension.code
    }

    "be set to ESA if this is an Employment Support Allowance" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        Some(TaiConstants.PrimaryEmployment),
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.ESA_TAX_DISTRICT),
        Some(TaiConstants.ESA_PAYE_NUMBER),
        Some(false),
        Some(false)
      )
      npsIncomeSource.incomeType mustBe IncomeTypeESA.code
    }

    "be set to IB if this is an Incapacity Benefit" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        Some(TaiConstants.PrimaryEmployment),
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT),
        Some(TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER),
        Some(false),
        Some(false)
      )
      npsIncomeSource.incomeType mustBe IncomeTypeIB.code
    }

    "be set to taxable benefit if this is job seekers allowance with the indicator set" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        Some(TaiConstants.PrimaryEmployment),
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        Some(false),
        Some(false),
        Some(true))
      npsIncomeSource.incomeType mustBe IncomeTypeJSA.code
    }

    "be set to taxable benefit if this is job seekers allowance" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.JSA_TAX_DISTRICT),
        Some(TaiConstants.JSA_PAYE_NUMBER),
        Some(false),
        Some(false),
        None)
      npsIncomeSource.incomeType mustBe IncomeTypeJSA.code
    }

    "be set to taxable benefit if this is job seekers allowance (Students)" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.JSA_STUDENTS_TAX_DISTRICT),
        Some(TaiConstants.JSA_STUDENTS_PAYE_NUMBER),
        Some(false),
        Some(false),
        None
      )
      npsIncomeSource.incomeType mustBe IncomeTypeJSA.code
    }

    "be set to taxable benefit if this is job seekers allowance (New DWP)" in {
      val npsIncomeSource = new NpsIncomeSource(
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        None,
        Some(TaiConstants.JSA_NEW_DWP_TAX_DISTRICT),
        Some(TaiConstants.JSA_NEW_DWP_PAYE_NUMBER),
        Some(false),
        Some(false),
        None
      )
      npsIncomeSource.incomeType mustBe IncomeTypeJSA.code
    }

  }

}
