/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.MarriageAllowance
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.helpers.TaxModelFactory
import uk.gov.hmrc.tai.model.nps2.{DeductionType, AllowanceType}
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.util.TaiConstants


case class NpsAlreadyTaxedAtSource(amount: Option[BigDecimal] = None, `type`: Option[Int] = None,
                                   iabdSummaries: Option[List[NpsIabdSummary]] = None,
                                   npsDescription :Option[String] = None,
                                   sourceAmount: Option[BigDecimal]=None,
                                   taxOnBankBSInterest: Option[BigDecimal] = None,
                                   taxCreditOnUKDividends: Option[BigDecimal] = None,
                                   taxCreditOnForeignInterest: Option[BigDecimal] = None,
                                   taxCreditOnForeignIncomeDividends: Option[BigDecimal] = None)

object NpsAlreadyTaxedAtSource {
  implicit val formats = Json.format[NpsAlreadyTaxedAtSource]
}

case class NpsOtherTaxDue(amount: Option[BigDecimal] = None, `type`: Option[Int] = None,
                          iabdSummaries: Option[List[NpsIabdSummary]] = None,
                          npsDescription :Option[String] = None,
                          sourceAmount: Option[BigDecimal]=None,
                          excessGiftAidTax: Option[BigDecimal]=None,
                          excessWidowsAndOrphans: Option[BigDecimal]=None,
                          pensionPaymentsAdjustment: Option[BigDecimal]=None,
                          childBenefit: Option[BigDecimal]=None)

object NpsOtherTaxDue {
  implicit val formats = Json.format[NpsOtherTaxDue]
}

case class NpsBasicRateExtensions(amount: Option[BigDecimal] = None, `type`: Option[Int] = None,
                                   iabdSummaries: Option[List[NpsIabdSummary]] = None,
                                   npsDescription :Option[String] = None,
                                   sourceAmount: Option[BigDecimal]=None,
                                   personalPensionPayment: Option[BigDecimal]=None,
                                   giftAidPayments: Option[BigDecimal]=None,
                                   personalPensionPaymentRelief: Option[BigDecimal]=None,
                                   giftAidPaymentsRelief: Option[BigDecimal]=None)

object NpsBasicRateExtensions {
  implicit val formats = Json.format[NpsBasicRateExtensions]
}


case class NpsReliefsGivingBackTax(amount: Option[BigDecimal] = None, `type`: Option[Int] = None,
                                  iabdSummaries: Option[List[NpsIabdSummary]] = None,
                                  npsDescription :Option[String] = None,
                                  sourceAmount: Option[BigDecimal]=None,
                                  marriedCouplesAllowance: Option[BigDecimal]=None,
                                  enterpriseInvestmentSchemeRelief : Option[BigDecimal]=None,
                                  concessionalRelief: Option[BigDecimal]=None,
                                  maintenancePayments: Option[BigDecimal]=None,
                                  doubleTaxationRelief: Option[BigDecimal]=None)

object NpsReliefsGivingBackTax {
  implicit val formats = Json.format[NpsReliefsGivingBackTax]
}



case class NpsTotalLiability(nonSavings : Option[NpsTax] = None,
                             untaxedInterest : Option[NpsTax] = None,
                             bankInterest : Option[NpsTax] = None,
                             ukDividends : Option[NpsTax] = None,
                             foreignInterest : Option[NpsTax] = None,
                             foreignDividends : Option[NpsTax] = None,
                             basicRateExtensions : Option[NpsBasicRateExtensions] = None,
                             reliefsGivingBackTax : Option[NpsReliefsGivingBackTax] = None,
                             otherTaxDue : Option[NpsOtherTaxDue] = None,
                             alreadyTaxedAtSource : Option[NpsAlreadyTaxedAtSource] = None,
                             totalLiability : Option[BigDecimal] = None) {


  lazy val allIADBIncomesTypes: List[NpsIabdSummary] =
      nonSavings.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      untaxedInterest.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      bankInterest.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      ukDividends.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      foreignInterest.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      foreignDividends.flatMap(_.totalIncome.flatMap(_.iabdSummaries)).getOrElse(Nil)



  lazy val allIADBReliefDeductsTypes: List[NpsIabdSummary] =
      nonSavings.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      untaxedInterest.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      bankInterest.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      ukDividends.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      foreignInterest.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil) :::
      foreignDividends.flatMap(_.allowReliefDeducts.flatMap(_.iabdSummaries)).getOrElse(Nil)

  lazy val allIADBTypes: List[NpsIabdSummary] = allIADBIncomesTypes ::: allIADBReliefDeductsTypes

  def getCodingAdjustment(relief : BigDecimal = BigDecimal(0),
                          taxCodeDetails: Option[TaxCodeDetails] = None,
                          compType : Int,
                          isAllow : Boolean = false): Option[Adjustment] =
    taxCodeDetails.map{ code =>
      if (isAllow) {
        Adjustment(
          code.allowances.flatMap(allow => allow.find(_.componentType.contains(compType)))
            .flatMap(_.amount)
            .getOrElse(BigDecimal(0)),
          relief)
      } else {
        Adjustment(
          code.deductions.flatMap(allow => allow.find(_.componentType.contains(compType)))
            .flatMap(_.amount)
            .getOrElse(BigDecimal(0)),
          relief)
      }
  }

  def toTotalLiabilitySummary(underpaymentPreviousYear : BigDecimal,
                              inYearAdjustment: BigDecimal,
                              childBenefitAmount : BigDecimal= BigDecimal(0),
                              childBenefitTaxDue : BigDecimal= BigDecimal(0),
                              outstandingDebt: BigDecimal = BigDecimal(0),
                              marriageAllowance: Option[MarriageAllowance] = None,
                              taxCodeDetails: Option[TaxCodeDetails] = None,
                              totalTax: BigDecimal= BigDecimal(0)) : TotalLiability = {


    val reductions = LiabilityReductions(marriageAllowance = marriageAllowance,
      enterpriseInvestmentSchemeRelief = getCodingAdjustment(reliefsGivingBackTax.flatMap(_.enterpriseInvestmentSchemeRelief)
          .getOrElse(BigDecimal(0)),taxCodeDetails, AllowanceType.EnterpriseInvestmentSchemeRelief.id, isAllow = true),
      concessionalRelief = getCodingAdjustment(reliefsGivingBackTax.flatMap(_.concessionalRelief)
          .getOrElse(BigDecimal(0)),taxCodeDetails,AllowanceType.ConcessionalRelief.id, isAllow = true),
      maintenancePayments = getCodingAdjustment(reliefsGivingBackTax.flatMap(_.maintenancePayments)
          .getOrElse(BigDecimal(0)),taxCodeDetails,AllowanceType.MaintenancePayment.id, isAllow = true),
      doubleTaxationRelief =  getCodingAdjustment(reliefsGivingBackTax.flatMap(_.doubleTaxationRelief)
          .getOrElse(BigDecimal(0)),taxCodeDetails,AllowanceType.DoubleTaxationReliefAllowance.id, isAllow = true))

    val additions = LiabilityAdditions(
     excessGiftAidTax =  getCodingAdjustment(otherTaxDue.flatMap(_.excessGiftAidTax).getOrElse(BigDecimal(0)),
         taxCodeDetails,DeductionType.GiftAidAdjustment.id),
     excessWidowsAndOrphans =  getCodingAdjustment(otherTaxDue.flatMap(_.excessWidowsAndOrphans).getOrElse(BigDecimal(0)),
         taxCodeDetails,DeductionType.WidowsAndOrphansAdjustment.id),
     pensionPaymentsAdjustment =  getCodingAdjustment(otherTaxDue.flatMap(_.pensionPaymentsAdjustment).getOrElse(BigDecimal(0)),
         taxCodeDetails,DeductionType.Annuity.id))

    val nonCodedIncomeTax = TaxModelFactory.convertToNonCodedIncome(nonSavings).flatMap(_.totalTax)
    val taxOnBankBSInterest = alreadyTaxedAtSource.flatMap(ats => ats.taxOnBankBSInterest)
    val taxCreditOnUKDividends = alreadyTaxedAtSource.flatMap(ats => ats.taxCreditOnUKDividends)
    val taxCreditOnForeignInterest = alreadyTaxedAtSource.flatMap(ats => ats.taxCreditOnForeignInterest)
    val taxCreditOnForeignIncomeDividends = alreadyTaxedAtSource.flatMap(ats => ats.taxCreditOnForeignIncomeDividends)

    val totalPaidElseWhere = nonCodedIncomeTax.getOrElse(BigDecimal(0)) +
      taxOnBankBSInterest.getOrElse(BigDecimal(0)) +
      taxCreditOnUKDividends.getOrElse(BigDecimal(0)) +
      taxCreditOnForeignInterest.getOrElse(BigDecimal(0)) +
      taxCreditOnForeignIncomeDividends.getOrElse(BigDecimal(0))

    val liabilityReductions = Some(reductions)
    val liabilityAdditions = Some(additions)


    val reductionsToLiability =  liabilityReductions.flatMap(_.marriageAllowance.map(_.marriageAllowanceRelief)).getOrElse(BigDecimal(0)) +
      liabilityReductions.flatMap(_.enterpriseInvestmentSchemeRelief.map(_.amountInTermsOfTax)).
        getOrElse(BigDecimal(0)) +
      liabilityReductions.flatMap(_.concessionalRelief.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0)) +
      liabilityReductions.flatMap(_.maintenancePayments.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0)) +
      liabilityReductions.flatMap(_.doubleTaxationRelief.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0))

    val additionsToTotalLiabilty =  liabilityAdditions.flatMap(_.excessGiftAidTax.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0)) +
      liabilityAdditions.flatMap(_.excessWidowsAndOrphans.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0)) +
      liabilityAdditions.flatMap(_.pensionPaymentsAdjustment.map(_.amountInTermsOfTax)).getOrElse(BigDecimal(0)) +
      underpaymentPreviousYear +
      outstandingDebt +
      childBenefitTaxDue +
      inYearAdjustment

    val calculatedTotalTax = totalTax - totalPaidElseWhere - reductionsToLiability + additionsToTotalLiabilty

    val totalLiabilitySummary = new TotalLiability(
      nonCodedIncome = TaxModelFactory.convertToNonCodedIncome(nonSavings),
      totalTax = if(calculatedTotalTax < BigDecimal(0)) BigDecimal(0) else calculatedTotalTax,
      underpaymentPreviousYear = underpaymentPreviousYear,
      inYearAdjustment =Some(inYearAdjustment),
      outstandingDebt = outstandingDebt,
      childBenefitAmount = childBenefitAmount,
      childBenefitTaxDue = childBenefitTaxDue,
      taxOnBankBSInterest = taxOnBankBSInterest,
      taxCreditOnUKDividends = taxCreditOnUKDividends ,
      taxCreditOnForeignInterest = taxCreditOnForeignInterest,
      taxCreditOnForeignIncomeDividends = taxCreditOnForeignIncomeDividends,
      liabilityReductions = liabilityReductions,
      liabilityAdditions = liabilityAdditions
    )

    totalLiabilitySummary
  }


  def otherIncome() : Option[NpsComponent] = {
    val otherIncomes = allIADBTypes.filter(x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_OTHER_INCOME))
    val profit = allIADBTypes.find(_.`type`.contains(Profit.code)).flatMap(_.amount)
    val loss = allIADBTypes.find(_.`type`.contains(Loss.code)).flatMap(_.amount)
    val lossBroughtForward = allIADBTypes.find(_.`type`.contains(LossBroughtForwardFromEarlierTaxYear.code)).flatMap(_.amount)
    val newProfitAmount = profit.getOrElse(BigDecimal(0)) -
      loss.getOrElse(BigDecimal(0)) -
      lossBroughtForward.getOrElse(BigDecimal(0))

    //Remove the profit if it's <= 0, else replace with the new value
    val adjustedIncomes = if (newProfitAmount <= 0) {
      otherIncomes.filter(!_.`type`.contains(Profit.code))
    } else {
      otherIncomes.map(x => if (x.`type`.contains(Profit.code)) x.copy(amount = Some(newProfitAmount)) else x).toList
    }

    //Remove
    createIADBGroup(adjustedIncomes)

  }

  private def benefitsInKindRemovingTotalOrComponentParts() : List[NpsIabdSummary] = {
    val allTotalBenefitsInKind  = allIADBTypes.filter(_.`type` == TaiConstants.IADB_TYPE_BENEFITS_IN_KIND_TOTAL)
    val allBenefitsInKind :  List[NpsIabdSummary] = allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IADB_TYPE_BENEFITS_IN_KIND))
    val allEmploymentIds = (allTotalBenefitsInKind ::: allBenefitsInKind).map(_.employmentId).distinct.toList


    allEmploymentIds.flatMap { employmentId =>
      val totalBenefitsInKind = allTotalBenefitsInKind.find(_.employmentId == employmentId)
      val benefitsInKind: List[NpsIabdSummary] = allBenefitsInKind.filter(_.employmentId == employmentId)
      val calculatedTotalBenefitsInKind = benefitsInKind.foldLeft(BigDecimal(0))((total, detail) => detail.amount.getOrElse(BigDecimal(0)) + total)
      totalBenefitsInKind match {
        case Some(x) if x.amount.exists(amountVal => amountVal > 0 && amountVal != calculatedTotalBenefitsInKind) =>
          List(x)
        case _ => benefitsInKind
      }
    }
  }

  def benefitsFromEmployment() : Option[NpsComponent] = {
    val basicBenefits :  List[NpsIabdSummary] = allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_BENEFITS_FROM_EMPLOYMENT))
    createIADBGroup(basicBenefits ::: benefitsInKindRemovingTotalOrComponentParts)
  }

  def otherPensions() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IADB_TYPE_OTHER_PENSIONS) ))
  }

  def blindPerson() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_BLIND_PERSON) ))
  }

  def expenses() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_EXPENSES) ))
  }

  def giftRelated() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_GIFT_RELATED) ))
  }

  def jobExpenses() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_JOB_EXPENSES) ))
  }

  def miscellaneous() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_MISCELLANEOUS) ))
  }

  def pensionContributions() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_PENSION_CONTRIBUTIONS) ))
  }

  def dividends() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_DIVIDENDS)))
  }

  def bankinterest() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_BANK_INTEREST)))
  }

  def untaxedinterest() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, TaiConstants.IABD_TYPE_UNTAXED_INTEREST)))
  }

  def foreigninterest() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, List(Some(ForeignInterestAndOtherSavings.code)))))
  }



  def foreigndividends() : Option[NpsComponent] = {
    createIADBGroup(allIADBTypes.filter( x => filterTypeAndZero(x, List(Some(ForeignDividendIncome.code)))))
  }

  def other() : Option[NpsComponent] = {
    val excludeList = TaiConstants.IADB_TYPE_BENEFITS_IN_KIND_TOTAL ::
                      TaiConstants.IADB_TYPE_BENEFITS_IN_KIND :::
                      TaiConstants.IABD_TYPE_BENEFITS_FROM_EMPLOYMENT :::
                      TaiConstants.IABD_TYPE_OTHER_INCOME :::
                      TaiConstants.IABD_TYPE_BLIND_PERSON :::
                      TaiConstants.IABD_TYPE_EXPENSES :::
                      TaiConstants.IABD_TYPE_GIFT_RELATED :::
                      TaiConstants.IADB_TYPE_OTHER_PENSIONS :::
                      TaiConstants.IABD_TYPE_JOB_EXPENSES :::
                      TaiConstants.IABD_TYPE_MISCELLANEOUS :::
                      TaiConstants.IABD_TYPE_PENSION_CONTRIBUTIONS :::
                      TaiConstants.IADB_TYPES_TO_NOT_GROUP
    createIADBGroup(allIADBTypes.filter( x => reverseFilterTypeAndZero(x, excludeList) ))
  }

  private def filterTypeAndZero(iadbSummary : NpsIabdSummary, filterTypes : List[Option[Int]]) : Boolean = {
    filterTypes.contains(iadbSummary.`type`) && iadbSummary.amount.getOrElse(BigDecimal(0))!=BigDecimal(0)
  }

  private def reverseFilterTypeAndZero(iadbSummary : NpsIabdSummary, filterTypes : List[Option[Int]]) : Boolean = {
    !filterTypes.contains(iadbSummary.`type`) && iadbSummary.amount.getOrElse(BigDecimal(0))!=BigDecimal(0)
  }

  /**
   * Create a new group of components based on the list of iadbtypes
   * Order the returned list by amount, highest first
   */
  private def createIADBGroup(iadbComponents : List[NpsIabdSummary]) : Option[NpsComponent] = {
    iadbComponents match {
      case Nil => None
      case x => Some(NpsComponent(Some(x.foldLeft(BigDecimal(0))((total,detail) => detail.amount.getOrElse(BigDecimal(0))  + total)),
        None, Some(x), None))
    }
  }

}
object NpsTotalLiability {
  implicit val formats= Json.format[NpsTotalLiability]
}