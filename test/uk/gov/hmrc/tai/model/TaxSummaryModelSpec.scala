/*
 * Copyright 2022 HM Revenue & Customs
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

import data.NpsData
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.util.TaiConstants

class TaxSummaryModelSpec extends PlaySpec {

  "Incomes" must {

    "Non coded Incomes" must {
      "not appear if we don't have one" in {

        val npsTaxAccount = NpsData.getNpsBasicRateExtnTaxAccount()
        val npsEmployment = NpsData.getNpsBasicRateExtnEmployment()

        // Sanity test the input data
        npsEmployment.head.worksNumber mustBe Some("0000000")

        npsTaxAccount.totalLiability.isDefined mustBe true
        npsTaxAccount.totalLiability.get.nonSavings.isDefined mustBe true
        val nonSavings = npsTaxAccount.totalLiability.get.nonSavings.get
        val iabdSummary = nonSavings.totalIncome.get.iabdSummaries.get
        val noneCoded = iabdSummary.find(iabd => iabd.`type` == Some(IabdType.NonCodedIncome.code))
        noneCoded.isDefined mustBe false

        val taiTaxDetails = npsTaxAccount.toTaxSummary(1, npsEmployment)
        taiTaxDetails.totalLiability.isDefined mustBe true
        val totalLiability = taiTaxDetails.totalLiability.get

        // Test none coded tax calculation
        totalLiability.nonCodedIncome.isDefined mustBe false

        taiTaxDetails.adjustedNetIncome mustBe BigDecimal(53875)

      }
      "appear in total liability section" in {
        val npsTaxAccount = NpsData.getNpsNonCodedWithCeasedTaxAccount()
        val npsEmployment = NpsData.getNpsNonCodedWithCeasedEmployment()

        // Sanity test the input data
        npsEmployment.head.worksNumber mustBe Some("0")

        npsTaxAccount.totalLiability.isDefined mustBe true
        npsTaxAccount.totalLiability.get.nonSavings.isDefined mustBe true
        val nonSavings = npsTaxAccount.totalLiability.get.nonSavings.get
        val iabdSummary = nonSavings.totalIncome.get.iabdSummaries.get
        val noneCoded = iabdSummary.find(iabd => iabd.`type` == Some(IabdType.NonCodedIncome.code))
        noneCoded.isDefined mustBe true
        noneCoded.get.`type` mustBe Some(IabdType.NonCodedIncome.code)

        val taiTaxDetails = npsTaxAccount.toTaxSummary(1, npsEmployment)

        taiTaxDetails.totalLiability.isDefined mustBe true
        val totalLiability = taiTaxDetails.totalLiability.get

        // Test none coded tax calculation
        totalLiability.nonCodedIncome.isDefined mustBe true
        totalLiability.nonCodedIncome.get.totalIncome mustBe Some(BigDecimal(219))
        totalLiability.nonCodedIncome.get.totalTaxableIncome mustBe Some(BigDecimal(219))
        totalLiability.nonCodedIncome.get.totalTax mustBe Some(BigDecimal(87.6))

        //Now check to ensure that the non coded income is included in the increasesTax section
        taiTaxDetails.increasesTax.isDefined mustBe true
        taiTaxDetails.increasesTax.get.total mustBe BigDecimal(62219)

      }
    }

    "have in Year Adjustment " must {

      val npsEmployment = NpsData.getNpsPotentialUnderpaymentEmployments()
      val npsTaxAccount = NpsData.getNpsPotentialUnderpaymentTaxAccount()

      val taiTaxDetails = npsTaxAccount.toTaxSummary(1, npsEmployment)

      val emps = taiTaxDetails.increasesTax.flatMap { increasesTax =>
        increasesTax.incomes.flatMap { income =>
          income.taxCodeIncomes.employments
        }
      }.get

      val tcIncomes = emps.taxCodeIncomes
      val totalIYA = tcIncomes.foldLeft(BigDecimal(0))(_ + _.tax.totalInYearAdjustment.getOrElse(BigDecimal(0)))
      val currentIYA = tcIncomes.foldLeft(BigDecimal(0))(_ + _.tax.inYearAdjustmentIntoCY.getOrElse(BigDecimal(0)))
      val currentIYAPlusOne =
        tcIncomes.foldLeft(BigDecimal(0))(_ + _.tax.inYearAdjustmentIntoCYPlusOne.getOrElse(BigDecimal(0)))
      val previousIYA =
        tcIncomes.foldLeft(BigDecimal(0))(_ + _.tax.inYearAdjustmentFromPreviousYear.getOrElse(BigDecimal(0)))

      "include total " in {
        totalIYA mustBe BigDecimal(272)
      }
      "include current year value " in {
        currentIYA mustBe BigDecimal(12)
      }
      "include current year plus one value " in {
        currentIYAPlusOne mustBe BigDecimal(34)
      }
      "include previous year value " in {
        previousIYA mustBe BigDecimal(56)
      }
    }

    "Oustanding Debt" must {

      val npsData = data.NpsData.getNpsOutstandingDebt()
      val npsIabd = data.NpsData.getNpsChildBenefitIabds()
      val convertedTaxAccount = npsData.toTaxSummary(1, Nil, npsIabd)

      "be present" in {
        convertedTaxAccount.totalLiability.isEmpty mustBe false
        convertedTaxAccount.totalLiability.map(_.outstandingDebt) mustBe Some(200)
        convertedTaxAccount.gateKeeper.map(_.gateKeepered) mustBe None

      }
    }
  }
  "withMciRule" must {
    "return GateKeeper with Manual Correspondence Indicator data" in {
      val gateKeeper = GateKeeper(
        true,
        List(
          GateKeeperRule(
            Some(TaiConstants.mciGateKeeperType),
            Some(TaiConstants.mciGatekeeperId),
            Some(TaiConstants.mciGatekeeperDescr))))
      GateKeeper.withMciRule mustBe gateKeeper
    }
  }
}
