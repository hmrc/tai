/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.formatters

import play.api.Logging
import play.api.libs.json._

import scala.language.postfixOps
import uk.gov.hmrc.tai.model.domain.calculation._

trait IncomeCategoryHodFormatters extends Logging {

  val taxFreeAllowanceReads = new Reads[BigDecimal] {
    override def reads(json: JsValue): JsResult[BigDecimal] = {
      val categoryNames = Seq("nonSavings", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
      val totalLiability = (json \ "totalLiability").as[JsValue]
      JsSuccess(
        categoryNames map (category =>
          (totalLiability \ category \ "allowReliefDeducts" \ "amount").asOpt[BigDecimal] getOrElse BigDecimal(0)) sum
      )
    }
  }

  val incomeCategorySeqReads = new Reads[Seq[IncomeCategory]] {
    override def reads(json: JsValue): JsResult[Seq[IncomeCategory]] = {
      val categoryNames =
        Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
      val incomeCategoryList = incomeCategories(json, categoryNames)
      JsSuccess(incomeCategoryList)
    }
  }

  def incomeCategories(json: JsValue, categories: Seq[String]): Seq[IncomeCategory] = {
    val categoryMap: Map[String, Option[JsObject]] =
      categories.map(category => (category, (json \ "totalLiability" \ category).asOpt[JsObject])).toMap

    categoryMap.collect {
      case (category, Some(jsObject)) =>
        val totalTax = (jsObject \ "totalTax").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val totalTaxableIncome = (jsObject \ "totalTaxableIncome").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val totalIncome = (jsObject \ "totalIncome" \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val taxBands = (jsObject \ "taxBands").asOpt[Seq[TaxBand]](Reads.seq[TaxBand](taxBandReads))
        val inComeCategory = categoryTypeFactory(category)
        IncomeCategory(inComeCategory, totalTax, totalTaxableIncome, totalIncome, taxBands.getOrElse(Seq()).filter(_.income > 0))
    }.toList
  }

  val taxBandReads = new Reads[TaxBand] {
    override def reads(json: JsValue): JsResult[TaxBand] = {
      val bandType = (json \ "bandType").as[String]
      val code = (json \ "taxCode").as[String]
      val income = (json \ "income").asOpt[BigDecimal]
      val tax = (json \ "tax").asOpt[BigDecimal]
      val lowerBand = (json \ "lowerBand").asOpt[BigDecimal]
      val upperBand = (json \ "upperBand").asOpt[BigDecimal]
      val rate = (json \ "rate").as[BigDecimal]
      (income, tax) match {
        case (Some(income), Some(tax)) => JsSuccess(TaxBand(bandType, code, income, tax, lowerBand, upperBand, rate))
        case (None, None) =>
          logger.info("Empty tax returned, no income or tax")
          JsSuccess(TaxBand(bandType, code, 0, 0, lowerBand, upperBand, rate))
        case (Some(income), None) => {
          logger.error(s"Income value was present but tax was not in tax band: $bandType, code: $code")
          JsSuccess(TaxBand(bandType, code, income, 0, lowerBand, upperBand, rate))
        }
        case (None, Some(_)) => {
          logger.error(s"Tax value was present at income was not in tax band: $bandType, code: $code")
          JsSuccess(TaxBand(bandType, code, 0, 0, lowerBand, upperBand, rate))
        }
      }
    }
  }

  private def categoryTypeFactory(category: String) =
    category match {
      case "nonSavings"       => NonSavingsIncomeCategory
      case "untaxedInterest"  => UntaxedInterestIncomeCategory
      case "bankInterest"     => BankInterestIncomeCategory
      case "ukDividends"      => UkDividendsIncomeCategory
      case "foreignInterest"  => ForeignInterestIncomeCategory
      case "foreignDividends" => ForeignDividendsIncomeCategory
      case _                  => throw new RuntimeException("Wrong income category type")
    }
}
