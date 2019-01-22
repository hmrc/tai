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

package uk.gov.hmrc.tai.calculators

import org.joda.time.LocalDate
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsTax}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.math.BigDecimal.RoundingMode

trait TaxCalculator {

  def updateTax(tax: Tax, updateDifference: BigDecimal,  personalAllowanceDifference : BigDecimal): Tax = {
    val newTotalIncome = tax.totalIncome.map(amount => if ((amount + updateDifference) > 0) amount + updateDifference else BigDecimal(0))
    val newAllowedReliefDeducts = tax.allowReliefDeducts.map(amount =>amount + personalAllowanceDifference)

    val newTaxableIncome = {
      val newTotalIncomeVal = newTotalIncome.getOrElse(BigDecimal(0))
      val newAllowedReliefDeductsVal = newAllowedReliefDeducts.getOrElse(BigDecimal(0))
      if (newTotalIncomeVal > newAllowedReliefDeductsVal) newTotalIncomeVal - newAllowedReliefDeductsVal else BigDecimal(0)
    }

    val newTaxBands = TaxBandCalculator.recreateTaxBandsNewTaxableIncome (Some(newTaxableIncome), tax.taxBands)
    val newTotalTax = newTaxBands.map(_.foldLeft(BigDecimal(0))((totalIncome, taxBand) => taxBand.tax.getOrElse(BigDecimal(0)) + totalIncome))
    tax.copy(totalIncome = newTotalIncome,
      totalTaxableIncome = Some(newTaxableIncome),
      totalTax = newTotalTax,
      taxBands = newTaxBands,
      allowReliefDeducts = newAllowedReliefDeducts)
  }


  private[calculators] def adjustmentsTaxFreeAmount(allowances : Option[List[NpsComponent]] = None,
                                       deductions : Option[List[NpsComponent]] = None): BigDecimal = {
    val totalAllowance = allowances.map(_.foldLeft(BigDecimal(0))((total,component) => component.amount.getOrElse(BigDecimal(0))  + total))
    val totalDeduction = deductions.map(_.foldLeft(BigDecimal(0))((total,component) => component.amount.getOrElse(BigDecimal(0))  + total))

    totalAllowance.getOrElse(BigDecimal(0)) - totalDeduction.getOrElse(BigDecimal(0))
  }


  def adjustTaxData(taxCode : Option[String] = None,
                            allowances : Option[List[NpsComponent]] = None,
                            deductions : Option[List[NpsComponent]] = None, oldTax : NpsTax):
  (Option[List[TaxBand]], Option[BigDecimal], Option[BigDecimal], Option[BigDecimal]) = {

    if (isAdjustmentNeeded(taxCode, allowances, deductions, oldTax)) {
      val adjustmentsTotal = adjustmentsTaxFreeAmount(allowances, deductions)
      val adjustedTaxableIncome = oldTax.totalIncome.flatMap(_.amount.map(incomeAmount => incomeAmount - adjustmentsTotal))
      val adjustedTaxFreeAmount = oldTax.totalIncome.flatMap(_.amount.map(incomeAmount => incomeAmount - adjustedTaxableIncome.getOrElse(0)))

      val adjustedTaxBands = TaxBandCalculator.recreateTaxBandsNewTaxableIncome(adjustedTaxableIncome, oldTax.taxBands)

      val adjustedTax = adjustedTaxBands.map(_.foldLeft(BigDecimal(0))((total,taxBand) => taxBand.tax.getOrElse(BigDecimal(0))  + total))
      (adjustedTaxBands, adjustedTax, adjustedTaxableIncome, adjustedTaxFreeAmount)

    } else {
      (oldTax.taxBands, oldTax.totalTax, oldTax.totalTaxableIncome, oldTax.allowReliefDeducts.flatMap(_.amount))
    }
  }

  private[calculators] def isAdjustmentNeeded(taxCode : Option[String] = None,
                         allowances : Option[List[NpsComponent]] = None,
                         deductions : Option[List[NpsComponent]] = None, oldTax : NpsTax): Boolean = {

    if (taxCode.exists(taxCodeVal => taxCodeVal.startsWith("B") || taxCodeVal.startsWith("D"))) {
      false
    } else if (oldTax.totalTax.isEmpty || oldTax.totalTax.contains(0)) {
      false
    } else {
      val allowReliefDeductTotal = oldTax.allowReliefDeducts.flatMap(_.amount).getOrElse(BigDecimal(0))
      val adjustmentsTotal = adjustmentsTaxFreeAmount(allowances, deductions)

      !allowReliefDeductTotal.equals(adjustmentsTotal)
    }
  }

  def getStartDateInCurrentFinancialYear(startDate: LocalDate): LocalDate = {
    val startDateCY = TaxYear().start

    if (TaxYear().fallsInThisTaxYear(startDate)) {
      startDate
    } else {
      startDateCY
    }
  }

  def actualTaxDueAssumingBasicRateAlreadyPaid(totalIncome : Option[BigDecimal] = None, totalTax : Option[BigDecimal] =
    None, taxBands : Option[List[TaxBand]]): Option[BigDecimal] = {
    totalTax.flatMap{tax =>
      val result = tax - totalAtBasicRate(totalIncome, taxBands)
      if (result >= 0) Some(result) else None
    }
  }

  private[calculators] def totalAtBasicRate(totalIncome : Option[BigDecimal] = None, taxBands : Option[List[TaxBand]]): BigDecimal = {

    taxBands.flatMap{ band =>
      val rateBands = band.filter(!_.rate.contains(BigDecimal(0)))
      rateBands.headOption.map{basicRateBand =>
        totalIncome.getOrElse(BigDecimal(0)) * (basicRateBand.rate.getOrElse(BigDecimal(0)) / 100)
      }
    }.getOrElse(BigDecimal(0))
  }

  def calculateChildBenefit(childBenefitAmount : BigDecimal, adjustedNetIncome : BigDecimal): BigDecimal = {
    val percentEqualChildbenefitHigherThreshold = 99
    val percentGreaterChildbenefitHigherThreshold = 100
    val percentage = {

      if(adjustedNetIncome == TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD){
        BigDecimal(percentEqualChildbenefitHigherThreshold)
      } else if(adjustedNetIncome > TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD){
        BigDecimal(percentGreaterChildbenefitHigherThreshold)
      } else {
        ((adjustedNetIncome - TaiConstants.CHILDBENEFIT_LOWER_THRESHOLD) / BigDecimal(percentGreaterChildbenefitHigherThreshold))
        .setScale(0, RoundingMode.DOWN) * TaiConstants.CHILDBENEIT_PERC_STEP
      }
    }

    if(percentage > BigDecimal(0)){
      childBenefitAmount * (percentage/BigDecimal(percentGreaterChildbenefitHigherThreshold))
    } else {
      BigDecimal(0)
    }
  }

}

object TaxCalculator extends TaxCalculator