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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.DeductionType
import uk.gov.hmrc.tai.model.nps2.Income.Live
import uk.gov.hmrc.tai.util.TaiConstants

@Singleton
class NextYearComparisonService @Inject()() {

  def stripCeasedFromNps(npsTaxAccount: NpsTaxAccount) = {

    val modifiedIncomeSources = npsTaxAccount.incomeSources.map(_.map { incomeSource =>
      incomeSource.employmentStatus match {
        case Some(Live.code) =>
          incomeSource
        case _ =>
          val payAndTax = incomeSource.payAndTax.flatMap(_.totalIncome)
          val payTax =
            incomeSource.payAndTax.map(x => x.copy(totalIncome = payAndTax.map(_.copy(amount = Some(BigDecimal(0))))))
          incomeSource.copy(payAndTax = payTax, taxCode = Some("Not applicable"))
      }
    })

    val totalTaxableIncome = npsTaxAccount.totalLiability
      .flatMap(
        _.nonSavings
          .flatMap(
            _.totalIncome
              .flatMap(_.iabdSummaries
                .map(_.foldLeft(BigDecimal(0))(_ + _.amount.getOrElse(BigDecimal(0)))))))

    val nonSavingsComp = npsTaxAccount.totalLiability
      .flatMap(_.nonSavings)
      .flatMap(_.totalIncome)
      .map(_.copy(amount = totalTaxableIncome))

    val totalLiability = npsTaxAccount.totalLiability.map { x =>
      val nonSavings = x.nonSavings.map(_.copy(totalIncome = nonSavingsComp))
      x.copy(nonSavings = nonSavings)
    }

    npsTaxAccount.copy(incomeSources = modifiedIncomeSources, totalLiability = totalLiability)
  }

  def proccessTaxSummaryWithCYPlusOne(
    taxSummaryDetails: TaxSummaryDetails,
    nextYear: TaxSummaryDetails): TaxSummaryDetails = {

    val cyPersonalAllowanceCheck = cyPlusOnePersonalAllowance(taxSummaryDetails, nextYear, CYPlusOneChange())
    val cyPlusOneUnderPaymentCheck = cyPlusOneUnderPayment(taxSummaryDetails, nextYear, cyPersonalAllowanceCheck)
    val cyPlusOneTotalTaxCheck = cyPlusOneTotalTax(taxSummaryDetails, nextYear, cyPlusOneUnderPaymentCheck)
    val cyPlusOneEmpBenCheck = cyPlusOneEmpBenefits(taxSummaryDetails, nextYear, cyPlusOneTotalTaxCheck)
    val cyPlusOnePersonalSavingsAllowanceCheck =
      cyPlusOnePersonalSavingsAllowance(taxSummaryDetails, nextYear, cyPlusOneEmpBenCheck)
    val cyPlusOneEmploymentTaxCodesCheck = cyPlusOneEmploymentTaxCodes(nextYear, cyPlusOnePersonalSavingsAllowanceCheck)
    val cyPlusOneScottishTaxCodesCheck = cyPlusOneScottishTaxCodes(nextYear, cyPlusOneEmploymentTaxCodesCheck)

    if (cyPlusOneCheckForAnyChanges(cyPlusOneScottishTaxCodesCheck)) {
      taxSummaryDetails.copy(cyPlusOneChange = Some(cyPlusOneScottishTaxCodesCheck), cyPlusOneSummary = Some(nextYear))
    } else {
      taxSummaryDetails
    }
  }

  def cyPlusOneEmploymentTaxCodes(
    cyPlusOneTaxSummaryDetails: TaxSummaryDetails,
    cyPlusOneChange: CYPlusOneChange): CYPlusOneChange =
    cyPlusOneChange.copy(employmentsTaxCode = cyPlusOneTaxSummaryDetails.taxCodeDetails.flatMap(_.employment))

  def cyPlusOneScottishTaxCodes(
    cyPlusOneTaxSummaryDetails: TaxSummaryDetails,
    cyPlusOneChange: CYPlusOneChange): CYPlusOneChange = {
    val isScottishTaxCodeExist = cyPlusOneTaxSummaryDetails.taxCodeDetails.flatMap(_.employment) match {
      case Some(employments) => Some(employments.exists(_.taxCode.getOrElse("").startsWith("S")))
      case _                 => None
    }

    cyPlusOneChange.copy(scottishTaxCodes = isScottishTaxCodeExist)
  }

  def cyPlusOneCheckForAnyChanges(cYPlusOneChange: CYPlusOneChange): Boolean =
    cYPlusOneChange.personalAllowance.isDefined ||
      cYPlusOneChange.underPayment.isDefined ||
      cYPlusOneChange.totalTax.isDefined ||
      cYPlusOneChange.employmentBenefits.isDefined ||
      cYPlusOneChange.personalSavingsAllowance.isDefined

  def cyPlusOneEmpBenefits(
    taxSummaryDetails: TaxSummaryDetails,
    cyPlusOneDetails: TaxSummaryDetails,
    cYPlusOneChange: CYPlusOneChange): CYPlusOneChange = {

    val cyPlusOneBenefits = cyPlusOneDetails.increasesTax.flatMap(_.benefitsFromEmployment)
    val cyBenefits = taxSummaryDetails.increasesTax.flatMap(_.benefitsFromEmployment)

    val totalBikCy =
      taxSummaryDetails.increasesTax.flatMap(_.benefitsFromEmployment.flatMap(_.iabdSummaries.find(iabd =>
        iabd.iabdType == TaiConstants.IADB_TYPE_BENEFITS_IN_KIND_TOTAL.getOrElse(0))))

    val totalBikNy = cyPlusOneDetails.increasesTax.flatMap(_.benefitsFromEmployment.flatMap(_.iabdSummaries.find(iabd =>
      iabd.iabdType == TaiConstants.IADB_TYPE_BENEFITS_IN_KIND_TOTAL.getOrElse(0))))

    (
      totalBikCy,
      totalBikNy,
      totalBikCy == totalBikNy,
      getBenefitsChangeWithoutBik(cyPlusOneBenefits, cyBenefits).isEmpty) match {
      case (Some(_), Some(_), true, _)  => cYPlusOneChange
      case (Some(_), Some(_), false, _) => cYPlusOneChange.copy(employmentBenefits = Some(true))
      case (_, _, _, true)              => cYPlusOneChange
      case (_, _, _, false)             => cYPlusOneChange.copy(employmentBenefits = Some(true))
      case _                            => cYPlusOneChange
    }
  }

  def getBenefitsChangeWithoutBik(
    cyPlusOneBenefitsFromEmployment: Option[TaxComponent],
    cyBenefitsFromEmployment: Option[TaxComponent]): List[Change[Some[IabdSummary], Option[IabdSummary]]] = {

    val currentEB = cyBenefitsFromEmployment match {
      case Some(curr) => curr.iabdSummaries
      case _          => List()
    }
    val cyPlusOneEB = cyPlusOneBenefitsFromEmployment match {
      case Some(cyp) =>
        cyp.iabdSummaries.filter(x => !TaiConstants.IADB_TYPE_BENEFITS_IN_KIND_TOTAL.contains(x.iabdType))
      case _ => List()
    }

    val compare = for (ce <- currentEB.filter(_.iabdType != DeductionType.EmployerBenefits.id)) yield {
      val cy = cyPlusOneEB.find(cy => cy.iabdType == ce.iabdType && cy.employmentId == ce.employmentId)
      Change(Some(ce), cy)
    }

    compare.filter(c => (c.currentYear != c.currentYearPlusOne) && c.currentYear.isDefined)
  }

  def cyPlusOneTotalTax(
    taxSummaryDetails: TaxSummaryDetails,
    cyPlusOneDetails: TaxSummaryDetails,
    cYPlusOneChange: CYPlusOneChange): CYPlusOneChange = {

    val currentTT = taxSummaryDetails.totalLiability.map(_.totalTax).getOrElse(BigDecimal(0))
    val cyPlusOneTT = cyPlusOneDetails.totalLiability.map(_.totalTax).getOrElse(BigDecimal(0))

    if (currentTT == cyPlusOneTT) {
      cYPlusOneChange
    } else {
      cYPlusOneChange.copy(totalTax = Some(Change(currentTT, cyPlusOneTT)))
    }
  }

  def cyPlusOneUnderPayment(
    taxSummaryDetails: TaxSummaryDetails,
    cyPlusOneDetails: TaxSummaryDetails,
    cYPlusOneChange: CYPlusOneChange): CYPlusOneChange = {

    val currentUP = taxSummaryDetails.totalLiability.map(_.underpaymentPreviousYear).getOrElse(BigDecimal(0))
    val cyPlusOneUP = cyPlusOneDetails.totalLiability.map(_.underpaymentPreviousYear).getOrElse(BigDecimal(0))

    if (currentUP == cyPlusOneUP) {
      cYPlusOneChange
    } else {
      cYPlusOneChange.copy(underPayment = Some(Change(currentUP, cyPlusOneUP)))
    }
  }

  def cyPlusOnePersonalAllowance(
    taxSummaryDetails: TaxSummaryDetails,
    cyPlusOneDetails: TaxSummaryDetails,
    cYPlusOneChange: CYPlusOneChange): CYPlusOneChange = {

    val currentPA =
      taxSummaryDetails.decreasesTax.map(_.personalAllowance.getOrElse(BigDecimal(0))).getOrElse(BigDecimal(0))
    val cyPlusOnePA =
      cyPlusOneDetails.decreasesTax.map(_.personalAllowance.getOrElse(BigDecimal(0))).getOrElse(BigDecimal(0))

    val cyPlusOneStandardPA = cyPlusOneDetails.decreasesTax
      .map(_.personalAllowanceSourceAmount.getOrElse(BigDecimal(0)))
      .getOrElse(BigDecimal(0))

    if (currentPA == cyPlusOnePA) {
      cYPlusOneChange
    } else {
      cYPlusOneChange
        .copy(personalAllowance = Some(Change(currentPA, cyPlusOnePA)), standardPA = Some(cyPlusOneStandardPA))
    }
  }

  def cyPlusOnePersonalSavingsAllowance(
    taxSummaryDetails: TaxSummaryDetails,
    cyPlusOneDetails: TaxSummaryDetails,
    cYPlusOneChange: CYPlusOneChange): CYPlusOneChange = {

    val currentValue =
      taxSummaryDetails.decreasesTax.flatMap(_.personalSavingsAllowance.map(_.amount)).getOrElse(BigDecimal(0))
    val nextYearValue =
      cyPlusOneDetails.decreasesTax.flatMap(_.personalSavingsAllowance.map(_.amount)).getOrElse(BigDecimal(0))

    if (currentValue == nextYearValue) {
      cYPlusOneChange
    } else {
      cYPlusOneChange.copy(personalSavingsAllowance = Some(Change(currentValue, nextYearValue)))
    }
  }
}
