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

import data.NpsData
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.calculators.TaxCalculator
import uk.gov.hmrc.tai.model.helpers.{IncomeHelper, TaxModelFactory}
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsIabdSummary, NpsTax, NpsTotalLiability}
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.util.TaiConstants

class TaxSummaryModelSpec extends UnitSpec {

  "Incomes" should {

    "Non coded Incomes" should {
      "not appear if we don't have one" in {

        val npsTaxAccount = NpsData.getNpsBasicRateExtnTaxAccount()
        val npsEmployment = NpsData.getNpsBasicRateExtnEmployment()

        // Sanity test the input data
        npsEmployment.head.worksNumber shouldBe Some("0000000")

        npsTaxAccount.totalLiability.isDefined shouldBe true
        npsTaxAccount.totalLiability.get.nonSavings.isDefined shouldBe true
        val nonSavings = npsTaxAccount.totalLiability.get.nonSavings.get
        val iabdSummary = nonSavings.totalIncome.get.iabdSummaries.get
        val noneCoded = iabdSummary.find(iabd => iabd.`type` == Some(IabdType.NonCodedIncome.code))
        noneCoded.isDefined shouldBe false

        val taiTaxDetails = npsTaxAccount.toTaxSummary(1, npsEmployment)
        taiTaxDetails.totalLiability.isDefined shouldBe true
        val totalLiability = taiTaxDetails.totalLiability.get

        // Test none coded tax calculation
        totalLiability.nonCodedIncome.isDefined shouldBe false

        taiTaxDetails.adjustedNetIncome shouldBe BigDecimal(53875)

      }
      "appear in total liability section" in {
        val npsTaxAccount = NpsData.getNpsNonCodedWithCeasedTaxAccount()
        val npsEmployment = NpsData.getNpsNonCodedWithCeasedEmployment()

        // Sanity test the input data
        npsEmployment.head.worksNumber shouldBe Some("0")

        npsTaxAccount.totalLiability.isDefined shouldBe true
        npsTaxAccount.totalLiability.get.nonSavings.isDefined shouldBe true
        val nonSavings = npsTaxAccount.totalLiability.get.nonSavings.get
        val iabdSummary = nonSavings.totalIncome.get.iabdSummaries.get
        val noneCoded = iabdSummary.find(iabd => iabd.`type` == Some(IabdType.NonCodedIncome.code))
        noneCoded.isDefined shouldBe true
        noneCoded.get.`type` shouldBe Some(IabdType.NonCodedIncome.code)

        val taiTaxDetails = npsTaxAccount.toTaxSummary(1, npsEmployment)

        taiTaxDetails.totalLiability.isDefined shouldBe true
        val totalLiability = taiTaxDetails.totalLiability.get

        // Test none coded tax calculation
        totalLiability.nonCodedIncome.isDefined shouldBe true
        totalLiability.nonCodedIncome.get.totalIncome shouldBe Some(BigDecimal(219))
        totalLiability.nonCodedIncome.get.totalTaxableIncome shouldBe Some(BigDecimal(219))
        totalLiability.nonCodedIncome.get.totalTax shouldBe Some(BigDecimal(87.6))

        //Now check to ensure that the non coded income is included in the increasesTax section
        taiTaxDetails.increasesTax.isDefined shouldBe true
        taiTaxDetails.increasesTax.get.total shouldBe BigDecimal(62219)

      }
    }

    "have in Year Adjustment " should {

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
        totalIYA shouldBe BigDecimal(272)
      }
      "include current year value " in {
        currentIYA shouldBe BigDecimal(12)
      }
      "include current year plus one value " in {
        currentIYAPlusOne shouldBe BigDecimal(34)
      }
      "include previous year value " in {
        previousIYA shouldBe BigDecimal(56)
      }
    }

    "Oustanding Debt" should {

      val npsData = data.NpsData.getNpsOutstandingDebt()
      val npsIabd = data.NpsData.getNpsChildBenefitIabds()
      val convertedTaxAccount = npsData.toTaxSummary(1, Nil, npsIabd)

      "be present" in {
        convertedTaxAccount.totalLiability.isEmpty shouldBe false
        convertedTaxAccount.totalLiability.map(_.outstandingDebt) shouldBe Some(200)
        convertedTaxAccount.gateKeeper.map(_.gateKeepered) shouldBe None

      }
    }
  }
  "withMciRule" should {
    "return GateKeeper with Manual Correspondence Indicator data" in {
      val gateKeeper = GateKeeper(
        true,
        List(
          GateKeeperRule(
            Some(TaiConstants.mciGateKeeperType),
            Some(TaiConstants.mciGatekeeperId),
            Some(TaiConstants.mciGatekeeperDescr))))
      GateKeeper.withMciRule shouldBe gateKeeper
    }
  }
}
