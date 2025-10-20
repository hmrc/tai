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

import play.api.Logging
import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.calculation.*

object TotalTaxHipReads extends Logging {

  private def logInconsistencies(
    income: Option[BigDecimal],
    tax: Option[BigDecimal],
    bandType: String,
    code: String,
    rateOpt: Option[BigDecimal]
  ): Unit = {

    def logError(message: String): Unit = logger.error(message, new RuntimeException(message))

    (income, tax, rateOpt) match {
      case (Some(_), None, Some(rate)) if rate > 0 =>
        logError(s"Income present but tax missing in band: $bandType, code: $code, rate: $rateOpt")
      case (Some(_), None, _) =>
        logger.info(s"Income present but tax missing in band: $bandType, code: $code, rate: $rateOpt")
      case (None, Some(_), _) =>
        logError(s"Tax present but income missing in band: $bandType, code: $code, rate: $rateOpt")
      case (_, _, None) =>
        logError(s"Missing rate for band: $bandType, code: $code")
      case _ => ()
    }
  }

  private val taxBandReads: Reads[Option[TaxBand]] = Reads { json =>
    val bandType = (json \ "bandType").as[String]
    val code = (json \ "taxCode").as[String]
    val income = (json \ "income").asOpt[BigDecimal]
    val tax = (json \ "tax").asOpt[BigDecimal]
    val lowerBand = (json \ "lowerBand").asOpt[BigDecimal]
    val upperBand = (json \ "upperBand").asOpt[BigDecimal]
    val rate = (json \ "rate").asOpt[BigDecimal]

    logInconsistencies(income, tax, bandType, code, rate)

    (income, tax, rate) match {
      case (Some(i), Some(t), Some(r)) => JsSuccess(Some(TaxBand(bandType, code, i, t, lowerBand, upperBand, r)))
      case (Some(i), None, Some(r)) =>
        JsSuccess(Some(TaxBand(bandType, code, i, BigDecimal(0), lowerBand, upperBand, r)))
      case _ => JsSuccess(None)
    }
  }

  val incomeCategorySeqReads: Reads[Seq[IncomeCategory]] = Reads { json =>
    val categories =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    JsSuccess(extractIncomeCategories(json, categories))
  }

  val taxFreeAllowanceReads: Reads[BigDecimal] = Reads { json =>
    val categories = Seq("nonSavings", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val totalAllowance = categories.map { category =>
      (json \ "totalLiabilityDetails" \ category \ "allowanceReliefDeductionsDetails" \ "amount")
        .asOpt[BigDecimal]
        .getOrElse(BigDecimal(0))
    }.sum
    JsSuccess(totalAllowance)
  }

  private def extractIncomeCategories(json: JsValue, categories: Seq[String]): Seq[IncomeCategory] =
    categories.flatMap { category =>
      (json \ "totalLiabilityDetails" \ category).asOpt[JsObject].map { jsObj =>
        val totalTax = (jsObj \ "totalTax").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val totalTaxableIncome = (jsObj \ "totalTaxableIncome").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val totalIncome = (jsObj \ "totalIncomeDetails" \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))

        val taxBands = (jsObj \ "taxBandDetails")
          .validateOpt[Seq[Option[TaxBand]]](Reads.seq(taxBandReads))
          .getOrElse(None)
          .getOrElse(Seq.empty)
          .flatten
          .filter(_.income > 0)

        IncomeCategory(
          incomeCategoryType = categoryTypeFactory(category),
          totalTax = totalTax,
          totalTaxableIncome = totalTaxableIncome,
          totalIncome = totalIncome,
          taxBands = taxBands
        )
      }
    }

  private def categoryTypeFactory(category: String): IncomeCategoryType =
    category match {
      case "nonSavings"       => NonSavingsIncomeCategory
      case "untaxedInterest"  => UntaxedInterestIncomeCategory
      case "bankInterest"     => BankInterestIncomeCategory
      case "ukDividends"      => UkDividendsIncomeCategory
      case "foreignInterest"  => ForeignInterestIncomeCategory
      case "foreignDividends" => ForeignDividendsIncomeCategory
      case _                  => throw new RuntimeException(s"Unexpected income category type: $category")
    }
}
