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

package uk.gov.hmrc.tai.model.helpers

import uk.gov.hmrc.tai.model.enums.IncomeType._
import uk.gov.hmrc.tai.model.nps.{NpsIabdSummary, _}
import uk.gov.hmrc.tai.util.TaiConstants
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live}

trait IncomeHelper {

  lazy val fetchIncomeType: Map[(Option[Int], Option[String]), String] = Map(
    (Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT), Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER)) -> IncomeTypeStatePensionLumpSum.description,
    (
      Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT),
      Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER))                                          -> IncomeTypeOccupationalPension.description,
    (Some(TaiConstants.ESA_TAX_DISTRICT), Some(TaiConstants.ESA_PAYE_NUMBER))                               -> IncomeTypeESA.description,
    (Some(TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT), Some(TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)) -> IncomeTypeIB.description,
    (Some(TaiConstants.JSA_TAX_DISTRICT), Some(TaiConstants.JSA_PAYE_NUMBER))                               -> IncomeTypeJSA.description,
    (Some(TaiConstants.JSA_STUDENTS_TAX_DISTRICT), Some(TaiConstants.JSA_STUDENTS_PAYE_NUMBER))             -> IncomeTypeJSA.description,
    (Some(TaiConstants.JSA_NEW_DWP_TAX_DISTRICT), Some(TaiConstants.JSA_NEW_DWP_PAYE_NUMBER))               -> IncomeTypeJSA.description
  )

  def isEditableByAutoUpdateService(otherIncomeSourceIndicator: Option[Boolean], jsaIndicator: Option[Boolean]): Boolean
}

object IncomeHelper extends IncomeHelper {

  def getAllIncomes(
    npsEmployments: List[NpsEmployment],
    incomeSources: Option[List[NpsIncomeSource]] = None,
    adjustedNetIncome: Option[NpsComponent] = None): List[MergedEmployment] = {

    //First process the incomes adding any data from an employment if we find one
    val mergedIncomes = incomeSources.map(_.map { incomeSource =>
      {
        //Find the match in employments
        val foundEmployment = npsEmployments.find(emp => incomeSource.employmentId.contains(emp.sequenceNumber))

        val estimatedPay = {
          val incomeTypeDescription =
            fetchIncomeType.get((incomeSource.employmentTaxDistrictNumber, incomeSource.employmentPayeRef))
          lazy val isJsaPrimary = incomeSource.employmentId
            .contains(TaiConstants.PrimaryEmployment) && incomeSource.jsaIndicator.getOrElse(false)

          (incomeTypeDescription, incomeSource.employmentType) match {
            case (Some(IncomeTypeESA.description), Some(TaiConstants.SecondaryEmployment)) =>
              getESAFromAdjustedNetIncome(adjustedNetIncome)
            case (Some(IncomeTypeIB.description), Some(TaiConstants.SecondaryEmployment)) =>
              getIBFromAdjustedNetIncome(adjustedNetIncome)
            case (Some(IncomeTypeJSA.description), Some(TaiConstants.SecondaryEmployment)) =>
              getIBFromAdjustedNetIncome(adjustedNetIncome)
            case (None, Some(TaiConstants.SecondaryEmployment)) if isJsaPrimary =>
              getIBFromAdjustedNetIncome(adjustedNetIncome)
            case _ => getEstimatedPayFromAdjustedNetIncome(adjustedNetIncome, incomeSource.employmentId)
          }
        }

        MergedEmployment(incomeSource, foundEmployment, estimatedPay)
      }
    }.toList) match {
      case None    => Nil
      case Some(x) => x
    }

    //Now we need to worry about any employments that don't have matching incomes (this is a data issue we have to deal with)
    val mergedEmployments = npsEmployments
      .map { employment =>
        {
          val income = incomeSources.flatMap {
            _.find(_.employmentId.contains(employment.sequenceNumber))
          }

          if (income.isEmpty) {
            //No income found for this employment so lookup the estimated pay
            val estimatedPay = {

              val incomeTypeDescription =
                fetchIncomeType.get((Some(employment.taxDistrictNumber.toInt), Some(employment.payeNumber)))
              if (incomeTypeDescription.contains(IncomeTypeESA.description) && employment.employmentType == TaiConstants.SecondaryEmployment) {
                getESAFromAdjustedNetIncome(adjustedNetIncome)
              } else {
                getEstimatedPayFromAdjustedNetIncome(adjustedNetIncome, Some(employment.sequenceNumber))
              }
            }

            estimatedPay.map(emp => MergedEmployment(employment.toNpsIncomeSource(emp), Some(employment), Some(emp)))
          } else {
            None
          }
        }
      }
      .toList
      .flatten

    def filterNonIncomeTypeStatePensionLumpSum(mergedEmployment: MergedEmployment) =
      !fetchIncomeType
        .get(
          (mergedEmployment.incomeSource.employmentTaxDistrictNumber, mergedEmployment.incomeSource.employmentPayeRef))
        .contains(IncomeTypeStatePensionLumpSum.description)

    mergedIncomes.filter(mergedEmpl => filterNonIncomeTypeStatePensionLumpSum(mergedEmpl)) ::: mergedEmployments.filter(
      mergedEmpl => filterNonIncomeTypeStatePensionLumpSum(mergedEmpl))
  }

  private[helpers] def getEstimatedPayFromAdjustedNetIncome(
    adjustedNetIncome: Option[NpsComponent] = None,
    employmentId: Option[Int]): Option[BigDecimal] = {
    val iabdFilter = (npsIabdSummary: NpsIabdSummary) =>
      (npsIabdSummary.`type`.contains(NewEstimatedPay.code) && npsIabdSummary.employmentId == employmentId)
    getIabdAmountFromIncome(adjustedNetIncome, iabdFilter)
  }

  private[helpers] def getESAFromAdjustedNetIncome(adjustedNetIncome: Option[NpsComponent] = None) = {
    val iabdFilter = (npsIabdSummary: NpsIabdSummary) =>
      npsIabdSummary.`type`.contains(EmploymentAndSupportAllowance.code)
    getIabdAmountFromIncome(adjustedNetIncome, iabdFilter)
  }

  private[helpers] def getIBFromAdjustedNetIncome(adjustedNetIncome: Option[NpsComponent] = None) = {
    val iabdFilter = (npsIabdSummary: NpsIabdSummary) => npsIabdSummary.`type`.contains(IncapacityBenefit.code)
    getIabdAmountFromIncome(adjustedNetIncome, iabdFilter)
  }

  private[helpers] def getIabdAmountFromIncome(
    adjustedNetIncome: Option[NpsComponent],
    iabdFilter: NpsIabdSummary => Boolean) =
    for {
      netIncome     <- adjustedNetIncome
      iabdSummaries <- netIncome.iabdSummaries
      iabd          <- iabdSummaries.find(iabdFilter)
      amount        <- iabd.amount
    } yield amount

  def filterTaxableStateBenefits(allIncomes: List[MergedEmployment]): (List[MergedEmployment], List[MergedEmployment]) =
    allIncomes.partition(
      x =>
        x.incomeSource.incomeType == IncomeTypeESA.code ||
          x.incomeSource.incomeType == IncomeTypeJSA.code ||
          x.incomeSource.incomeType == IncomeTypeIB.code)

  def filterPensions(allIncomes: List[MergedEmployment]): (List[MergedEmployment], List[MergedEmployment]) =
    allIncomes.partition(x => x.incomeSource.incomeType == IncomeTypeOccupationalPension.code)

  def filterLiveAndCeased(allIncomes: List[MergedEmployment]): (List[MergedEmployment], List[MergedEmployment]) =
    allIncomes.partition(x => isLive(x.incomeSource.employmentStatus))

  def isOccupationalPension(
    employmentTaxDistrictNumber: Option[Int],
    employmentPayeRef: Option[String],
    pensionIndicator: Option[Boolean]): Boolean =
    pensionIndicator.contains(true) ||
      (employmentTaxDistrictNumber.contains(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT) &&
        employmentPayeRef.contains(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER))

  def incomeType(
    employmentTaxDistrictNumber: Option[Int],
    employmentPayeRef: Option[String],
    employmentType: Option[Int],
    jsaIndicator: Option[Boolean],
    pensionIndicator: Option[Boolean]): Int = {
    lazy val isJsaPrimary = employmentType.contains(TaiConstants.PrimaryEmployment) && jsaIndicator.getOrElse(false)

    fetchIncomeType.get((employmentTaxDistrictNumber, employmentPayeRef)) match {
      case Some(IncomeTypeOccupationalPension.description) => IncomeTypeOccupationalPension.code
      case Some(IncomeTypeESA.description)                 => IncomeTypeESA.code
      case Some(IncomeTypeIB.description)                  => IncomeTypeIB.code
      case Some(IncomeTypeJSA.description)                 => IncomeTypeJSA.code
      case Some(IncomeTypeStatePensionLumpSum.description) => IncomeTypeStatePensionLumpSum.code
      case None if pensionIndicator.contains(true)         => IncomeTypeOccupationalPension.code
      case None if isJsaPrimary                            => IncomeTypeJSA.code
      case _                                               => IncomeTypeEmployment.code
    }
  }

  def isLive(employmentStatus: Option[Int]): Boolean = employmentStatus.isEmpty || employmentStatus.contains(Live.code)

  def isPrimary(employmentType: Option[Int]): Boolean =
    employmentType.isEmpty || employmentType.contains(TaiConstants.PrimaryEmployment)

  def isEditableByUser(
    otherIncomeSourceIndicator: Option[Boolean],
    cessationPayThisEmployment: Option[BigDecimal],
    jsaIndicator: Option[Boolean],
    employmentStatus: Option[Int]): Boolean =
    !otherIncomeSourceIndicator.contains(true) &&
      cessationPayThisEmployment.isEmpty &&
      !jsaIndicator.contains(true) &&
      !employmentStatus.contains(Ceased.code)

  override def isEditableByAutoUpdateService(
    otherIncomeSourceIndicator: Option[Boolean],
    jsaIndicator: Option[Boolean]): Boolean =
    !otherIncomeSourceIndicator.contains(true) && !jsaIndicator.contains(true)

  private[helpers] def getFromAdjustedNetIncome(
    adjustedNetIncome: Option[NpsComponent] = None,
    iabdType: Int): BigDecimal = {
    val iabdFilter = (npsIabdSummary: NpsIabdSummary) => npsIabdSummary.`type`.contains(iabdType)
    val amount = getIabdAmountFromIncome(adjustedNetIncome, iabdFilter)
    amount.getOrElse(0)
  }

  def getGiftFromAdjustedNetIncome(adjustedNetIncome: Option[NpsComponent] = None): BigDecimal = {
    val giftAidAmount = getFromAdjustedNetIncome(adjustedNetIncome, TotalGiftAidPayments.code)
    if (giftAidAmount > BigDecimal(0)) {
      giftAidAmount
    } else {
      getFromAdjustedNetIncome(adjustedNetIncome, GiftAidPayments.code) +
        getFromAdjustedNetIncome(adjustedNetIncome, OneOffGiftAidPayments.code) +
        getFromAdjustedNetIncome(adjustedNetIncome, GiftAidAfterEndOfTaxYear.code) -
        getFromAdjustedNetIncome(adjustedNetIncome, GiftAidTreatedAsPaidInPreviousTaxYear.code)
    }
  }

}
