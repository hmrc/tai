/*
 * Copyright 2021 HM Revenue & Customs
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

object PotentialUnderpaymentCalculator {

  def calculatePotentialUnderpayment(
    totalTax: Option[BigDecimal] = None,
    taxBands: Option[List[TaxBand]] = None): Option[BigDecimal] = {

    //The difference between the taxbands total and the total tax is the potential underpayment
    val potentialUnderpayment = totalTax.map { totalTaxAmount =>
      val totalTaxFromTaxBands = taxBands
        .map(_.foldLeft(BigDecimal(0))((total, taxBand) => taxBand.tax.getOrElse(BigDecimal(0)) + total))
        .getOrElse(BigDecimal(0))
      if (totalTaxFromTaxBands > totalTaxAmount) {
        totalTaxFromTaxBands - totalTaxAmount
      } else {
        BigDecimal(0)
      }
    }

    //If the value is <= 0 return None
    potentialUnderpayment match {
      case Some(y) if y > BigDecimal(0) => Some(y)
      case _                            => None
    }
  }

}
