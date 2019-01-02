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
import uk.gov.hmrc.tai.model.nps2.{AllowanceType, DeductionType}
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.{Adjustment, LiabilityAdditions, LiabilityReductions, TaxCodeComponent, TaxCodeDetails, MarriageAllowance => MarriageAllowanceCC}
import uk.gov.hmrc.tai.util.TaiConstants

import scala.collection.immutable.Seq
import scala.util.Random

class NpsTotalLiabilitySpec extends PlaySpec {

  "grouping of IABD Types (income allowance benefits deductions)" when {

    "an NpsTotalLiability instance is created with no content" should {

      val SUT = NpsTotalLiability()

      "generate an empty 'allIADBIncomesTypes' property" in {
        SUT.allIADBIncomesTypes mustBe List.empty
      }

      "generate an empty 'allIADBReliefDeductsTypes' property" in {
        SUT.allIADBReliefDeductsTypes mustBe List.empty
      }

      "generate an empty 'allIADBTypes' property" in {
        SUT.allIADBTypes mustBe List.empty
      }
    }

    "an NpsTotalLiability instance is created with content" should {

      val npsIabdSummary = NpsIabdSummary(Some(100), Some(1), Some("Desc1"), Some(2), Some(3))
      val npsComponent = NpsComponent(iabdSummaries = Some(List(npsIabdSummary)))

      val SUT = NpsTotalLiability(
        nonSavings = Some(NpsTax(
          totalIncome = Some(npsComponent),
          allowReliefDeducts = Some(npsComponent)))
      )

      "generate an 'allIADBIncomesTypes' property with content" in {
        SUT.allIADBIncomesTypes mustBe List(npsIabdSummary)
      }

      "generate an 'allIADBReliefDeductsTypes' property with content" in {
        SUT.allIADBReliefDeductsTypes mustBe List(npsIabdSummary)
      }

      "generate an 'allIADBTypes' property with content" in {
        SUT.allIADBTypes mustBe List(npsIabdSummary, npsIabdSummary)
      }
    }
  }

  "getCodingAdjustment processing of TaxCodeDetails" when {

    val SUT = NpsTotalLiability()
    val taxCodeComponentAllow = TaxCodeComponent(Some("desc"), Some(BigDecimal(21.00)), Some(1))
    val taxCodeComponentDeduct = TaxCodeComponent(Some("desc"), Some(BigDecimal(32.30)), Some(2))
    val taxCodeComponentOther = TaxCodeComponent(Some("desc"), Some(BigDecimal(12.60)), Some(3))

    val taxCodeDetails = TaxCodeDetails(None, None,
      deductions = Some(List(taxCodeComponentDeduct, taxCodeComponentOther)),
      allowances = Some(List(taxCodeComponentAllow, taxCodeComponentOther)))

    "an allowance is requested " should {

      "generate an allowance adjustment" in {
        val adjustment = SUT.getCodingAdjustment(relief = BigDecimal(3434),
          taxCodeDetails = Some(taxCodeDetails), compType = 1, isAllow = true)

        adjustment mustBe Some(Adjustment(21.00,3434))
      }
    }

    "an allowance is not requested " should {

      "generate an deduction adjustment" in {
        val adjustment = SUT.getCodingAdjustment(relief = BigDecimal(3434),
          taxCodeDetails = Some(taxCodeDetails), compType = 2, isAllow = false)

        adjustment mustBe Some(Adjustment(32.30,3434))
      }
    }
  }

  "otherIncome processing of NpsComponent" when {

    val foreignDividendSumm =
      NpsIabdSummary(Some(100), Some(ForeignDividendIncome.code), Some("Desc"), Some(2), Some(3))
    val otherIncomeNpsComponent = NpsComponent(iabdSummaries = Some(List(foreignDividendSumm)), `type` = Some(1))
    val bankInterestSumm = NpsIabdSummary(Some(100), Some(Profit.code), Some("Desc"), Some(2), Some(3))
    val profitNpsComponent = NpsComponent(iabdSummaries = Some(List(bankInterestSumm)), `type` = Some(2))
    val nonSavingsSumm = NpsIabdSummary(Some(20), Some(Loss.code), Some("Desc"), Some(2), Some(3))
    val lossNpsComponent = NpsComponent(iabdSummaries = Some(List(nonSavingsSumm)), `type` = Some(3))
    val ukDividendsSumm =
      NpsIabdSummary(Some(10), Some(LossBroughtForwardFromEarlierTaxYear.code), Some("Desc"), Some(2), Some(3))
    val lossBroughtForwardNpsComponent = NpsComponent(iabdSummaries = Some(List(ukDividendsSumm)), `type` = Some(3))


    "otherIncome is requested for profit and loss amounts that combine to produce a positive overall profit" should {

      "supply a new single NpsComponent reflecting a total other income amount, adjusted to reflect the " +
        "overall profit" in {

        val SUT = NpsTotalLiability(
          foreignDividends = Some(NpsTax(
            totalIncome = Some(otherIncomeNpsComponent))),
          bankInterest = Some(NpsTax(
            totalIncome = Some(profitNpsComponent))),
          nonSavings = Some(NpsTax(
            totalIncome = Some(lossNpsComponent))),
          ukDividends = Some(NpsTax(
            totalIncome = Some(lossBroughtForwardNpsComponent)))
        )

        val result = SUT.otherIncome()
        result.isDefined mustBe true
        val npsComponent = result.get
        npsComponent.amount mustBe Some(170)
        npsComponent.iabdSummaries mustBe
          Some(List(
            NpsIabdSummary(Some(70), Some(72), Some("Desc"), Some(2), Some(3)),
            NpsIabdSummary(Some(100), Some(62), Some("Desc"), Some(2), Some(3))))
      }
    }

    "otherIncome is requested for profit and loss amounts that combine to produce a negative overall profit" should {

      "supply a new single NpsComponent reflecting a total other income amount, excluding any profits" in {

        val nonSavingsIabdSummary = NpsIabdSummary(Some(100), Some(Loss.code), Some("Desc"), Some(2), Some(3))
        val lossNpsComponent = NpsComponent(iabdSummaries = Some(List(nonSavingsIabdSummary)), `type` = Some(3))

        val SUT = NpsTotalLiability(
          foreignDividends = Some(NpsTax(
            totalIncome = Some(otherIncomeNpsComponent))),
          bankInterest = Some(NpsTax(
            totalIncome = Some(profitNpsComponent))),
          nonSavings = Some(NpsTax(
            totalIncome = Some(lossNpsComponent))),
          ukDividends = Some(NpsTax(
            totalIncome = Some(lossBroughtForwardNpsComponent)))
        )

        val result = SUT.otherIncome()
        result.isDefined mustBe true
        val npsComponent = result.get
        npsComponent.amount mustBe Some(100)
        npsComponent.iabdSummaries mustBe
          Some(List(
            NpsIabdSummary(Some(100), Some(62), Some("Desc"), Some(2), Some(3))))
      }
    }
  }

  "The benefitsInKindRemovingTotalOrComponentParts method" when {

    val iabdBenFromEmp1 = NpsIabdSummary(amount = Some(101), `type` = Some(CarFuelBenefit.code), npsDescription = Some("Desc"), employmentId = Some(1), estimatedPaySource = Some(3))
    val iabdBenFromEmp2 = NpsIabdSummary(amount = Some(102), `type` = Some(MedicalInsurance.code), npsDescription = Some("Desc"), employmentId = Some(2), estimatedPaySource = Some(3))
    val iabdBenFromEmp3 = NpsIabdSummary(amount = Some(103), `type` = Some(CarBenefit.code), npsDescription = Some("Desc"), employmentId = Some(3), estimatedPaySource = Some(3))
    val iabdBenFromEmp4 = NpsIabdSummary(amount = Some(104), `type` = Some(Telephone.code), npsDescription = Some("Desc"), employmentId = Some(4), estimatedPaySource = Some(3))
    val iabdBenFromEmp5 = NpsIabdSummary(amount = Some(105), `type` = Some(ServiceBenefit.code), npsDescription = Some("Desc"), employmentId = Some(5), estimatedPaySource = Some(3))

    val iabdBenInKindTotal = NpsIabdSummary(amount = Some(999), `type` = Some(BenefitInKind.code), npsDescription = Some("Desc"), employmentId = Some(5), estimatedPaySource = Some(3))
    val iabdBenInKind1 = NpsIabdSummary(amount = Some(501), `type` = Some(Accommodation.code), npsDescription = Some("Desc"), employmentId = Some(5), estimatedPaySource = Some(3))
    val iabdBenInKind2 = NpsIabdSummary(amount = Some(502), `type` = Some(Entertaining.code), npsDescription = Some("Desc"), employmentId = Some(5), estimatedPaySource = Some(3))

    "individual benefits in kind do not sum to the same value as a supplied 'benefits in kind total' amount, " +
      "for a given employment id" should {

      val nonSavingsNpsComponent = NpsComponent(iabdSummaries = Some(
        List(iabdBenFromEmp1, iabdBenFromEmp2, iabdBenFromEmp3, iabdBenFromEmp4,
          iabdBenFromEmp5, iabdBenInKindTotal, iabdBenInKind1, iabdBenInKind2))
      )
      val SUT = NpsTotalLiability(nonSavings = Some(NpsTax(totalIncome = Some(nonSavingsNpsComponent))))

      "favour the 'benefits in kind total' iadb for this employment id, and disregard individual benefits in kind " +
        "iabd summaries" in {

        val benefitsNpsComponent = SUT.benefitsFromEmployment()
        benefitsNpsComponent.get.iabdSummaries mustBe
          Some(List(iabdBenFromEmp1, iabdBenFromEmp2, iabdBenFromEmp3,
            iabdBenFromEmp4, iabdBenFromEmp5, iabdBenInKindTotal))
        benefitsNpsComponent.get.amount mustBe Some(BigDecimal(1514))
        benefitsNpsComponent.get.`type` mustBe None
        benefitsNpsComponent.get.npsDescription mustBe None
        benefitsNpsComponent.get.sourceAmount mustBe None
      }
    }

    "individual benefits in kind sum to an equal value as the supplied 'benefits in kind total' amount, " +
      "for a given employment id" should {

      val adjustedIabdBenInKind2 = iabdBenInKind2.copy(amount=Some(BigDecimal(498)))
      val nonSavingsNpsComponent = NpsComponent(iabdSummaries = Some(
        List(iabdBenFromEmp1, iabdBenFromEmp2, iabdBenFromEmp3, iabdBenFromEmp4,
          iabdBenFromEmp5, iabdBenInKindTotal, iabdBenInKind1, adjustedIabdBenInKind2))
      )
      val SUT = NpsTotalLiability(nonSavings = Some(NpsTax(totalIncome = Some(nonSavingsNpsComponent))))

      "return the fine-grained, individual benefits in kind iabd summaries within the returned component, " +
        "and disregard the 'benefits in kind total' iadb summary" in {

        val benefitsNpsComponent = SUT.benefitsFromEmployment()
        benefitsNpsComponent.get.iabdSummaries mustBe
          Some(List(iabdBenFromEmp1, iabdBenFromEmp2, iabdBenFromEmp3, iabdBenFromEmp4,
            iabdBenFromEmp5, iabdBenInKind1, adjustedIabdBenInKind2))
        benefitsNpsComponent.get.amount mustBe Some(BigDecimal(1514))
        benefitsNpsComponent.get.`type` mustBe None
        benefitsNpsComponent.get.npsDescription mustBe None
        benefitsNpsComponent.get.sourceAmount mustBe None
      }
    }
  }

  "iabd extraction methods should" when {

    "processing an instance created with multiple iabd summary types" should {

      val SUT = randomSpreadTotalLiability(
        TaiConstants.IADB_TYPE_OTHER_PENSIONS ::: TaiConstants.IABD_TYPE_BLIND_PERSON ::: TaiConstants.IABD_TYPE_EXPENSES :::
        TaiConstants.IABD_TYPE_GIFT_RELATED ::: TaiConstants.IABD_TYPE_JOB_EXPENSES ::: TaiConstants.IABD_TYPE_MISCELLANEOUS :::
        TaiConstants.IABD_TYPE_PENSION_CONTRIBUTIONS ::: TaiConstants.IABD_TYPE_DIVIDENDS ::: TaiConstants.IABD_TYPE_BANK_INTEREST :::
        TaiConstants.IABD_TYPE_UNTAXED_INTEREST ::: List(Some(ForeignInterestAndOtherSavings.code), Some(ForeignDividendIncome.code))
      )

      "extract only those iabd's classified as IADB_TYPE_OTHER_PENSIONS" in {
        val result = SUT.otherPensions().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IADB_TYPE_OTHER_PENSIONS)
      }

      "extract only those iabd's classified as IABD_TYPE_BLIND_PERSON" in {
        val result = SUT.blindPerson().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_BLIND_PERSON)
      }

      "extract only those iabd's classified as IABD_TYPE_EXPENSES" in {
        val result = SUT.expenses().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_EXPENSES)
      }

      "extract only those iabd's classified as IABD_TYPE_GIFT_RELATED" in {
        val result = SUT.giftRelated().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_GIFT_RELATED)
      }

      "extract only those iabd's classified as IABD_TYPE_JOB_EXPENSES" in {
        val result = SUT.jobExpenses().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_JOB_EXPENSES)
      }

      "extract only those iabd's classified as IABD_TYPE_MISCELLANEOUS" in {
        val result = SUT.miscellaneous().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_MISCELLANEOUS)
      }

      "extract only those iabd's classified as IABD_TYPE_PENSION_CONTRIBUTIONS" in {
        val result = SUT.pensionContributions().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_PENSION_CONTRIBUTIONS)
      }

      "extract only those iabd's classified as IABD_TYPE_DIVIDENDS" in {
        val result = SUT.dividends().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_DIVIDENDS)
      }

      "extract only those iabd's classified as IABD_TYPE_BANK_INTEREST" in {
        val result = SUT.bankinterest().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_BANK_INTEREST)
      }

      "extract only those iabd's classified as IABD_TYPE_UNTAXED_INTEREST" in {
        val result = SUT.untaxedinterest().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(TaiConstants.IABD_TYPE_UNTAXED_INTEREST)
      }

      "extract only those iabd's classified as ForeignInterestAndOtherSavings.code" in {
        val result = SUT.foreigninterest().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain only Some(ForeignInterestAndOtherSavings.code)
      }

      "extract only those iabd's classified as ForeignDividendIncome.code" in {
        val result = SUT.foreigndividends().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain only Some(ForeignDividendIncome.code)
      }

      "extract only those iabd's classified as 'other'" in {
        val result = SUT.other().get.iabdSummaries.get.map(_.`type`)
        result.toSet must contain theSameElementsAs(List(Some(BankOrBuildingSocietyInterest.code), Some(UkDividend.code), Some(UntaxedInterest.code)))
      }
    }
  }

  "conversion toTotalLiabilitySummary" when {

    "processing an instance with amounts that result in zero value totalPaidElsewhere, reductionsToLiability, and additionsToTotalLiability amounts" should {

      val SUT = NpsTotalLiability()

      "return an unadjusted totalTax value - the same as value that was suplied" in {
        val result = SUT.toTotalLiabilitySummary(
          underpaymentPreviousYear = BigDecimal(0),
          inYearAdjustment = BigDecimal(0),
          totalTax = BigDecimal(99.99))

        result.totalTax mustBe BigDecimal(99.99)
      }
    }

    "processing an instance with amounts that contribute to a non zero 'totalPaidElsewhere' amount (sum of non-coded income tax and " +
      "alreadyTaxedAtSource amounts)" should {

      val iabdNonCoded = NpsIabdSummary(
        amount = None,
        `type` = Some(NonCodedIncome.code)
      )

      val nonSavingNpsTax: Option[NpsTax] =
        Some(NpsTax(totalIncome =
                      Some(NpsComponent(
                          amount = None,
                          iabdSummaries = Some(List(iabdNonCoded)))),
                    totalTax = Some(BigDecimal(40.00))))

      val alreadyTaxedAtSource = Some(NpsAlreadyTaxedAtSource(
        taxOnBankBSInterest = Some(BigDecimal(10)),
        taxCreditOnUKDividends = Some(BigDecimal(10)),
        taxCreditOnForeignInterest = Some(BigDecimal(10)),
        taxCreditOnForeignIncomeDividends = Some(BigDecimal(10))
      ))

      val SUT = NpsTotalLiability(
        nonSavings = nonSavingNpsTax,
        alreadyTaxedAtSource = alreadyTaxedAtSource
      )

      "return a totalTax amount that is the supplied value, minus the 'totalPaidElsewhere' calculated amount" in {

        val result = SUT.toTotalLiabilitySummary(
          underpaymentPreviousYear = BigDecimal(0),
          inYearAdjustment = BigDecimal(0),
          totalTax = BigDecimal(99.99))

        result.totalTax mustBe BigDecimal(19.99)
      }
    }

    "processing an instance with amounts that contribute to a non zero 'reductionsToLiability' amount (sum of reliefsGivingBackTax " +
      "and marriageAllowanceRelief amounts)" should {

      val npsReliefsGivingBackTax = Some(NpsReliefsGivingBackTax(
        enterpriseInvestmentSchemeRelief = Some(BigDecimal(5)),
        concessionalRelief = Some(BigDecimal(5)),
        maintenancePayments = Some(BigDecimal(5)),
        doubleTaxationRelief = Some(BigDecimal(5))
      ))

      val SUT = NpsTotalLiability(
        reliefsGivingBackTax = npsReliefsGivingBackTax
      )

      "return a totalTax amount that is the supplied value, minus the 'reductionsToLiability' calculated amount" in {

        val allowances = List(
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.EnterpriseInvestmentSchemeRelief.id)),
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.ConcessionalRelief.id)),
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.MaintenancePayment.id)),
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.DoubleTaxationReliefAllowance.id))
        )
        val taxCodeDetails = TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = None,
          allowances = Some(allowances)
        )

        val result = SUT.toTotalLiabilitySummary(
          underpaymentPreviousYear = BigDecimal(0),
          inYearAdjustment = BigDecimal(0),
          marriageAllowance = Some(MarriageAllowanceCC(
            marriageAllowanceRelief = BigDecimal(18.00)
          )),
          taxCodeDetails = Some(taxCodeDetails),
          totalTax = BigDecimal(99.99))

        result.totalTax mustBe BigDecimal(61.99)
      }
    }

    "processing an instance with amounts that contribute to a non zero 'additionsToTotalLiabilty' amount (sum of otherTaxDue " +
      "and underpaymentPreviousYear, outstandingDebt, childBenefitTaxDue, and inYearAdjustment amounts)" should {

      val npsOtherTaxDue = Some(NpsOtherTaxDue(
        excessGiftAidTax = Some(BigDecimal(3)),
        excessWidowsAndOrphans = Some(BigDecimal(3)),
        pensionPaymentsAdjustment = Some(BigDecimal(3))
      ))

      val SUT = NpsTotalLiability(
        otherTaxDue = npsOtherTaxDue
      )

      "return a totalTax amount that is the supplied value, plus the 'additionsToTotalLiabilty' calculated amount" in {

        val deductions = List(
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.GiftAidAdjustment.id)),
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.WidowsAndOrphansAdjustment.id)),
          TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.Annuity.id))
        )
        val taxCodeDetails = TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(deductions),
          allowances = None
        )

        val result = SUT.toTotalLiabilitySummary(
          underpaymentPreviousYear = BigDecimal(4),
          outstandingDebt = BigDecimal(4),
          childBenefitTaxDue = BigDecimal(4),
          inYearAdjustment = BigDecimal(4),
          taxCodeDetails = Some(taxCodeDetails),
          totalTax = BigDecimal(99.99))

        result.totalTax mustBe BigDecimal(124.99)
      }
    }

    "processing amounts on the liability object" should {

      val iabdNonCoded = NpsIabdSummary(
        amount = None,
        `type` = Some(NonCodedIncome.code)
      )
      val nonSavingNpsTax: Option[NpsTax] =
        Some(NpsTax(totalIncome =
          Some(NpsComponent(
            amount = None,
            iabdSummaries = Some(List(iabdNonCoded)))),
          totalTax = Some(BigDecimal(40.00))))

      val npsAlreadyTaxedAtSource = Some(NpsAlreadyTaxedAtSource(
        taxOnBankBSInterest = Some(BigDecimal(6)),
        taxCreditOnUKDividends = Some(BigDecimal(7)),
        taxCreditOnForeignInterest = Some(BigDecimal(8)),
        taxCreditOnForeignIncomeDividends = Some(BigDecimal(9))
      ))

      val allowances = List(
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.EnterpriseInvestmentSchemeRelief.id)),
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.ConcessionalRelief.id)),
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.MaintenancePayment.id)),
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(AllowanceType.DoubleTaxationReliefAllowance.id))
      )
      val npsReliefsGivingBackTax = Some(NpsReliefsGivingBackTax(
        enterpriseInvestmentSchemeRelief = Some(BigDecimal(5)),
        concessionalRelief = Some(BigDecimal(5)),
        maintenancePayments = Some(BigDecimal(5)),
        doubleTaxationRelief = Some(BigDecimal(5))
      ))

      val deductions = List(
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.GiftAidAdjustment.id)),
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.WidowsAndOrphansAdjustment.id)),
        TaxCodeComponent(amount = Some(BigDecimal(2)), componentType = Some(DeductionType.Annuity.id))
      )
      val npsOtherTaxDue = Some(NpsOtherTaxDue(
        excessGiftAidTax = Some(BigDecimal(3)),
        excessWidowsAndOrphans = Some(BigDecimal(3)),
        pensionPaymentsAdjustment = Some(BigDecimal(3))
      ))

      val taxCodeDetails = TaxCodeDetails(
        employment = None,
        taxCode = None,
        deductions = Some(deductions),
        allowances = Some(allowances)
      )

      val SUT = NpsTotalLiability(
        nonSavings = nonSavingNpsTax,
        alreadyTaxedAtSource = npsAlreadyTaxedAtSource,
        reliefsGivingBackTax = npsReliefsGivingBackTax,
        otherTaxDue = npsOtherTaxDue
      )

      "return correctly categorised amounts" in {

        val result = SUT.toTotalLiabilitySummary(
          underpaymentPreviousYear = BigDecimal(1),
          inYearAdjustment = BigDecimal(2),
          outstandingDebt = BigDecimal(3),
          childBenefitAmount = BigDecimal(4),
          childBenefitTaxDue = BigDecimal(5),
          taxCodeDetails = Some(taxCodeDetails),
          totalTax = BigDecimal(99.99))

        result.nonCodedIncome.get.totalTax mustBe Some(BigDecimal(40))
        result.underpaymentPreviousYear mustBe BigDecimal(1)
        result.inYearAdjustment mustBe Some(BigDecimal(2))
        result.outstandingDebt mustBe BigDecimal(3)
        result.childBenefitAmount mustBe BigDecimal(4)
        result.childBenefitTaxDue mustBe BigDecimal(5)
        result.taxOnBankBSInterest mustBe Some(BigDecimal(6))
        result.taxCreditOnUKDividends mustBe Some(BigDecimal(7))
        result.taxCreditOnForeignInterest mustBe Some(BigDecimal(8))
        result.taxCreditOnForeignIncomeDividends mustBe Some(BigDecimal(9))
        result.liabilityReductions mustBe
          Some(LiabilityReductions(None,Some(Adjustment(2,5)),Some(Adjustment(2,5)),Some(Adjustment(2,5)),Some(Adjustment(2,5))))
        result.liabilityAdditions mustBe
          Some(LiabilityAdditions(Some(Adjustment(2,3)),Some(Adjustment(2,3)),Some(Adjustment(2,3))))

        result.totalTax mustBe BigDecimal(29.99)
      }
    }
  }


  private def randomSpreadTotalLiability(iabdCodes: List[Option[Int]]): NpsTotalLiability = {

    val grouped: Seq[List[Option[Int]]] = iabdCodes.grouped(iabdCodes.size/6).toList
    val overflow: List[Option[Int]] = if (grouped.size > 6) grouped.last else Nil

    NpsTotalLiability(
      nonSavings = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(0))))))),
      untaxedInterest = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(1))))))),
      bankInterest = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(2))))))),
      ukDividends = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(3))))))),
      foreignInterest = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(4))))))),
      foreignDividends = Some(NpsTax(totalIncome = Some(NpsComponent(iabdSummaries = Some(iadbList(grouped(5) ::: overflow))))))
    )
  }

  private def iadbList(range: List[Option[Int]]) : List[NpsIabdSummary] = {
    range.map(code => {
      NpsIabdSummary(amount = Some(BigDecimal(1+Random.nextInt(99))), `type` = code)
    })
  }
}
