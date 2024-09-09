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

package uk.gov.hmrc.tai.model.domain.calculation

import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.taxAdjustments.TaxAdjustment
import play.api.Logging
import uk.gov.hmrc.tai.model.domain.calculation.TaxBand.taxBandReads
import scala.language.postfixOps
case class TaxBand(
  bandType: String,
  code: String,
  income: BigDecimal,
  tax: BigDecimal,
  lowerBand: Option[BigDecimal] = None,
  upperBand: Option[BigDecimal] = None,
  rate: BigDecimal
)

object TaxBand extends Logging {
  implicit val formats: OFormat[TaxBand] = Json.format[TaxBand]

  val taxBandReads: Reads[Option[TaxBand]] = (json: JsValue) => {
    val bandType = (json \ "bandType").as[String]
    val code = (json \ "taxCode").as[String]
    val income = (json \ "income").asOpt[BigDecimal]
    val tax = (json \ "tax").asOpt[BigDecimal]
    val lowerBand = (json \ "lowerBand").asOpt[BigDecimal]
    val upperBand = (json \ "upperBand").asOpt[BigDecimal]
    val rate = (json \ "rate").as[BigDecimal]
    (income, tax) match {
      case (Some(income), Some(tax)) =>
        JsSuccess(Some(TaxBand(bandType, code, income, tax, lowerBand, upperBand, rate)))
      case (None, None) =>
        logger.info("Empty tax returned, no income or tax")
        JsSuccess(None)
      case (Some(income), None) =>
        logger.error(s"Income value was present but tax was not in tax band: $bandType, code: $code")
        JsSuccess(Some(TaxBand(bandType, code, income, 0, lowerBand, upperBand, rate)))
      case (None, Some(_)) =>
        val x = new RuntimeException(s"Tax value was present at income was not in tax band: $bandType, code: $code")
        logger.error(x.getMessage, x)
        JsSuccess(None)
    }
  }
}

sealed trait IncomeCategoryType
case object NonSavingsIncomeCategory extends IncomeCategoryType
case object UntaxedInterestIncomeCategory extends IncomeCategoryType
case object BankInterestIncomeCategory extends IncomeCategoryType
case object UkDividendsIncomeCategory extends IncomeCategoryType
case object ForeignInterestIncomeCategory extends IncomeCategoryType
case object ForeignDividendsIncomeCategory extends IncomeCategoryType

object IncomeCategoryType {
  implicit val incomeCategoryTypeFormats: Format[IncomeCategoryType] = new Format[IncomeCategoryType] {
    override def reads(json: JsValue): JsResult[IncomeCategoryType] = ???
    override def writes(o: IncomeCategoryType): JsValue = JsString(o.toString)
  }
}

case class IncomeCategory(
  incomeCategoryType: IncomeCategoryType,
  totalTax: BigDecimal,
  totalTaxableIncome: BigDecimal,
  totalIncome: BigDecimal,
  taxBands: Seq[TaxBand]
)

object IncomeCategory {
  implicit val formats: OFormat[IncomeCategory] = Json.format[IncomeCategory]

  // TODO: DDCNL-9376 Need version of tax-account toggled on
  val incomeCategorySeqReads: Reads[Seq[IncomeCategory]] = (json: JsValue) => {
    val categoryNames =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val incomeCategoryList = incomeCategories(json, categoryNames)
    JsSuccess(incomeCategoryList)
  }

  // TODO: DDCNL-9376 Need version of tax-account toggled on
  val taxFreeAllowanceReads: Reads[BigDecimal] = (json: JsValue) => {
    val categoryNames = Seq("nonSavings", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val totalLiability = (json \ "totalLiability").as[JsValue]
    JsSuccess(
      categoryNames map (category =>
        (totalLiability \ category \ "allowReliefDeducts" \ "amount").asOpt[BigDecimal] getOrElse BigDecimal(0)
      ) sum
    )
  }

  private def categoryTypeFactory(category: String): IncomeCategoryType =
    category match {
      case "nonSavings"       => NonSavingsIncomeCategory
      case "untaxedInterest"  => UntaxedInterestIncomeCategory
      case "bankInterest"     => BankInterestIncomeCategory
      case "ukDividends"      => UkDividendsIncomeCategory
      case "foreignInterest"  => ForeignInterestIncomeCategory
      case "foreignDividends" => ForeignDividendsIncomeCategory
      case _                  => throw new RuntimeException("Wrong income category type")
    }

  private def incomeCategories(json: JsValue, categories: Seq[String]): Seq[IncomeCategory] = {
    val categoryMap: Map[String, Option[JsObject]] =
      categories.map(category => (category, (json \ "totalLiability" \ category).asOpt[JsObject])).toMap

    categoryMap.collect { case (category, Some(jsObject)) =>
      val totalTax = (jsObject \ "totalTax").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val totalTaxableIncome = (jsObject \ "totalTaxableIncome").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val totalIncome = (jsObject \ "totalIncome" \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val taxBands =
        (jsObject \ "taxBands").asOpt[Seq[TaxBand]](Reads.seq[Option[TaxBand]](taxBandReads).map(_.flatten))
      val inComeCategory = categoryTypeFactory(category)
      IncomeCategory(
        inComeCategory,
        totalTax,
        totalTaxableIncome,
        totalIncome,
        taxBands.getOrElse(Seq()).filter(_.income > 0)
      )
    }.toList
  }
}

case class TotalTax(
  amount: BigDecimal,
  incomeCategories: Seq[IncomeCategory],
  reliefsGivingBackTax: Option[TaxAdjustment],
  otherTaxDue: Option[TaxAdjustment],
  alreadyTaxedAtSource: Option[TaxAdjustment],
  taxOnOtherIncome: Option[BigDecimal] = None,
  taxReliefComponent: Option[TaxAdjustment] = None
)

object TotalTax {
  implicit val formats: OFormat[TotalTax] = Json.format[TotalTax]
}
