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

import uk.gov.hmrc.tai.model.TaxBand
import scala.annotation.tailrec

object TaxBandCalculator {

  def recreateTaxBandsNewTaxableIncome(
    newTaxableIncome: Option[BigDecimal] = None,
    oldTaxBands: Option[List[TaxBand]],
    startingPoint: BigDecimal = BigDecimal(0)): Option[List[TaxBand]] =
    if (oldTaxBands.isDefined && newTaxableIncome.isDefined) {
      val sortedTaxBandsOption = oldTaxBands.map(_.sortBy(_.rate))
      sortedTaxBandsOption.map(sortedTaxBands =>
        recreateTaxBands(newTaxableIncome, sortedTaxBands, Nil, startingPoint).flatten)
    } else {
      oldTaxBands
    }

  @tailrec
  private[calculators] def recreateTaxBands(
    remainingTaxableIncome: Option[BigDecimal] = None,
    oldTaxBands: List[TaxBand],
    newTaxBands: List[Option[TaxBand]],
    startingPoint: BigDecimal): List[Option[TaxBand]] =
    if (oldTaxBands.nonEmpty) {
      val oldRemaining = remainingTaxableIncome.getOrElse(BigDecimal(0))

      val newTaxBand = oldTaxBands.headOption.map { oldTaxBand =>
        val (newIncome, newTax) = recreateTaxBand(oldTaxBand, oldRemaining, startingPoint)
        oldTaxBand.copy(income = newIncome, tax = newTax)
      }

      val newList: List[Option[TaxBand]] = newTaxBands :+ newTaxBand
      val newRemaining = newTaxBand.map(newIncome => oldRemaining - newIncome.income.getOrElse(BigDecimal(0)))
      recreateTaxBands(newRemaining, oldTaxBands.drop(1), newList, startingPoint)

    } else {
      newTaxBands
    }

  private[calculators] def recreateTaxBand(
    oldTaxBand: TaxBand,
    remainingTaxableIncome: BigDecimal,
    startingPoint: BigDecimal): (Option[BigDecimal], Option[BigDecimal]) = {

    val remaining = getIncomeForThisBand(oldTaxBand, remainingTaxableIncome, startingPoint)

    if (remaining <= 0) {
      (Some(0), Some(0))
    } else if (oldTaxBand.lowerBand.isEmpty) {
      (oldTaxBand.income, oldTaxBand.tax)
    } else {
      //We have more remaining income than is allowed in this band, so just use the max amount
      val MaxPercentage: BigDecimal = 100
      val rate: BigDecimal = oldTaxBand.rate.getOrElse(0)
      (Some(remaining), Some((remaining * rate) / MaxPercentage))
    }
  }

  private[calculators] def getIncomeForThisBand(
    taxBand: TaxBand,
    remainingTaxableIncome: BigDecimal,
    startingPoint: BigDecimal): BigDecimal = {
    val lowerBand = taxBand.lowerBand.getOrElse(BigDecimal(0))
    val upperBand = taxBand.upperBand.getOrElse(BigDecimal(0))

    val maxIncome =
      taxBand.upperBand match {
        case Some(_) if startingPoint >= upperBand => BigDecimal(0)
        case Some(_) if startingPoint >= lowerBand => upperBand - startingPoint
        case _                                     => upperBand - lowerBand
      }

    //First adjust the maximum income we will use in this tax band based on the max income
    if (upperBand == 0 || maxIncome > remainingTaxableIncome) {
      remainingTaxableIncome
    } else {
      maxIncome
    }
  }

}
