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

package uk.gov.hmrc.tai.model.domain.calculation

import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.taxAdjustments.TaxAdjustment

case class TaxBand(bandType: String,
                   code: String,
                   income: BigDecimal,
                   tax: BigDecimal,
                   lowerBand: Option[BigDecimal] = None,
                   upperBand: Option[BigDecimal] = None,
                   rate: BigDecimal)

object TaxBand{
  implicit val formats = Json.format[TaxBand]
}

sealed trait IncomeCategoryType
case object NonSavingsIncomeCategory extends IncomeCategoryType
case object UntaxedInterestIncomeCategory extends IncomeCategoryType
case object BankInterestIncomeCategory extends IncomeCategoryType
case object UkDividendsIncomeCategory extends IncomeCategoryType
case object ForeignInterestIncomeCategory extends IncomeCategoryType
case object ForeignDividendsIncomeCategory extends IncomeCategoryType

object IncomeCategoryType{
  implicit val incomeCategoryTypeFormats = new Format[IncomeCategoryType] {
    override def reads(json: JsValue): JsResult[IncomeCategoryType] = ???
    override def writes(o: IncomeCategoryType): JsValue = JsString(o.toString)
  }
}

case class IncomeCategory(incomeCategoryType: IncomeCategoryType,
                          totalTax: BigDecimal,
                          totalTaxableIncome: BigDecimal,
                          totalIncome: BigDecimal,
                          taxBands: Seq[TaxBand]
                         )

object IncomeCategory{
  implicit val formats = Json.format[IncomeCategory]
}

case class TotalTax(amount: BigDecimal,
                    incomeCategories: Seq[IncomeCategory],
                    reliefsGivingBackTax: Option[TaxAdjustment],
                    otherTaxDue: Option[TaxAdjustment],
                    alreadyTaxedAtSource: Option[TaxAdjustment],
                    taxOnOtherIncome: Option[BigDecimal] = None,
                    taxReliefComponent: Option[TaxAdjustment] = None)

object TotalTax{
  implicit val formats = Json.format[TotalTax]
}
