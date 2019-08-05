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

import uk.gov.hmrc.tai.calculators.TaxCalculator
import uk.gov.hmrc.tai.model.Tax
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsTax}

trait TaxHelper {
  def taxCalculator: TaxCalculator
  def toTax(oldTax: NpsTax, taxPaidAtSource: Option[BigDecimal] = None): Tax = {

    val filteredTaxBands = oldTax.taxBands.map(_.sortBy(_.rate))

    val totalIncomeAmount = oldTax.totalIncome.flatMap(_.amount)

    Tax(
      totalIncome = totalIncomeAmount,
      totalTax = oldTax.totalTax,
      totalTaxableIncome = oldTax.totalTaxableIncome,
      taxBands = filteredTaxBands,
      allowReliefDeducts = oldTax.allowReliefDeducts.flatMap(_.amount),
      actualTaxDueAssumingBasicRateAlreadyPaid =
        taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(totalIncomeAmount, oldTax.totalTax, filteredTaxBands),
      actualTaxDueAssumingAllAtBasicRate = taxPaidAtSource
    )
  }

  def toAdjustedTax(
    oldTax: NpsTax,
    taxCode: Option[String] = None,
    allowances: Option[List[NpsComponent]] = None,
    deductions: Option[List[NpsComponent]] = None,
    totalInYearAdjustment: Option[BigDecimal] = None,
    inYearAdjustmentIntoCY: Option[BigDecimal] = None,
    inYearAdjustmentIntoCYPlusOne: Option[BigDecimal] = None,
    inYearAdjustmentFromPreviousYear: Option[BigDecimal] = None): Tax = {

    val (adjustedTaxBands, adjustedTax, adjustedTaxable, adjustedAllowReliefDeducts) =
      taxCalculator.adjustTaxData(taxCode, allowances, deductions, oldTax)
    val filteredTaxBands = adjustedTaxBands.map(_.toList.sortBy(_.rate))

    val totalIncomeAmount = oldTax.totalIncome.flatMap(_.amount)

    Tax(
      totalIncome = totalIncomeAmount,
      totalTax = adjustedTax,
      totalTaxableIncome = adjustedTaxable,
      totalInYearAdjustment = totalInYearAdjustment,
      inYearAdjustmentIntoCY = inYearAdjustmentIntoCY,
      inYearAdjustmentIntoCYPlusOne = inYearAdjustmentIntoCYPlusOne,
      inYearAdjustmentFromPreviousYear = inYearAdjustmentFromPreviousYear,
      taxBands = filteredTaxBands,
      allowReliefDeducts = adjustedAllowReliefDeducts,
      actualTaxDueAssumingBasicRateAlreadyPaid =
        taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(totalIncomeAmount, adjustedTax, filteredTaxBands)
    )
  }
}

object TaxHelper extends TaxHelper {
  override val taxCalculator: TaxCalculator = TaxCalculator
}
