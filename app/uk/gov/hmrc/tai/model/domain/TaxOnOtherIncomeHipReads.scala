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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json.{JsSuccess, JsValue, Reads}

import scala.annotation.tailrec

object TaxOnOtherIncomeHipReads {
  private val NonCodedIncome = 19

  val taxOnOtherIncomeTaxValueReads: Reads[Option[BigDecimal]] = (json: JsValue) =>
    JsSuccess(json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) map (_.tax))

  val taxAccountSummaryReads: Reads[BigDecimal] = (json: JsValue) => {
    val taxOnOtherIncome =
      json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) map (_.tax) getOrElse BigDecimal(0)
    val totalLiabilityTax =
      (json \ "totalLiabilityDetails" \ "totalLiability").asOpt[BigDecimal].getOrElse(BigDecimal(0))
    JsSuccess(totalLiabilityTax - taxOnOtherIncome)
  }

  private val taxOnOtherIncomeReads: Reads[Option[TaxOnOtherIncome]] = (json: JsValue) => {
    val iabdSummaries: Seq[NpsIabdSummary] =
      NpsIabdSummaryHipReads.totalLiabilityIabds(json, "totalIncomeDetails", Seq("nonSavings"))
    val nonCodedIncomeAmount = iabdSummaries.find(_.componentType == NonCodedIncome).map(_.amount)
    @tailrec
    def calculateTaxOnOtherIncome(
      incomeAndRateBands: Seq[RateBand],
      nonCodedIncome: BigDecimal,
      total: BigDecimal = 0
    ): BigDecimal =
      incomeAndRateBands match {
        case Nil => total
        case xs if nonCodedIncome > xs.head.income =>
          val newTotal = xs.head.income * (xs.head.rate / 100)
          calculateTaxOnOtherIncome(xs.tail, nonCodedIncome - xs.head.income, total + newTotal)
        case xs if nonCodedIncome <= xs.head.income =>
          val newTotal = nonCodedIncome * (xs.head.rate / 100)
          total + newTotal
        case _ => throw new RuntimeException("Incorrect rate band")
      }

    (nonCodedIncomeAmount, RateBandHipReads.incomeAndRateBands(json)) match {
      case (None, _)      => JsSuccess(None)
      case (Some(_), Nil) => JsSuccess(None)
      case (Some(amount), incomeAndRateBands) =>
        val remainingTaxOnOtherIncome = calculateTaxOnOtherIncome(incomeAndRateBands, amount)
        JsSuccess(Some(TaxOnOtherIncome(remainingTaxOnOtherIncome)))
    }

  }
}
