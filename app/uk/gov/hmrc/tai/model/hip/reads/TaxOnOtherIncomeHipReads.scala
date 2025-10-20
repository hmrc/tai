/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.*

import scala.annotation.tailrec

object TaxOnOtherIncomeHipReads {

  private val NonCodedIncome = 19

  private val taxOnOtherIncomeReads: Reads[Option[TaxOnOtherIncome]] = Reads { json =>
    val iabdSummaries = NpsIabdSummaryHipReads.totalLiabilityIabds(json, "totalIncomeDetails", Seq("nonSavings"))

    val nonCodedIncomeAmount = iabdSummaries.find(_.componentType == NonCodedIncome).map(_.amount)

    val incomeAndRateBands = RateBandHipReads.incomeAndRateBands(json)

    @tailrec
    def calculateTaxOnOtherIncome(
      bands: Seq[RateBand],
      remainingIncome: BigDecimal,
      accumulatedTax: BigDecimal = 0
    ): BigDecimal = bands match {
      case Nil => accumulatedTax
      case head :: tail =>
        if (remainingIncome > head.income) {
          calculateTaxOnOtherIncome(
            tail,
            remainingIncome - head.income,
            accumulatedTax + head.income * (head.rate / 100)
          )
        } else {
          accumulatedTax + remainingIncome * (head.rate / 100)
        }
    }

    (nonCodedIncomeAmount, incomeAndRateBands) match {
      case (None, _)      => JsSuccess(None)
      case (Some(_), Nil) => JsSuccess(None)
      case (Some(amount), bands) =>
        val calculatedTax = calculateTaxOnOtherIncome(bands, amount)
        JsSuccess(Some(TaxOnOtherIncome(calculatedTax)))
    }
  }

  val taxOnOtherIncomeTaxValueReads: Reads[Option[BigDecimal]] =
    taxOnOtherIncomeReads.map(_.map(_.tax))

  val taxAccountSummaryReads: Reads[BigDecimal] = Reads { json =>
    val taxOnOtherIncome =
      taxOnOtherIncomeReads.reads(json).getOrElse(None).map(_.tax).getOrElse(BigDecimal(0))

    val totalLiabilityTax =
      (__ \ "totalLiabilityDetails" \ "totalLiability")
        .readNullable[BigDecimal]
        .reads(json)
        .getOrElse(None)
        .getOrElse(BigDecimal(0))

    JsSuccess(totalLiabilityTax - taxOnOtherIncome)
  }
}
