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

import play.api.Logging
import play.api.libs.json.*

import scala.language.postfixOps

object TotalTaxHipReads extends Logging {

  private def logging(
    income: Option[BigDecimal],
    tax: Option[BigDecimal],
    bandType: String,
    code: String,
    rateOpt: Option[BigDecimal]
  ): Unit = {
    def logException(message: String): Unit = logger.error(message, new RuntimeException(message))
    def logMessageIncomeNoTax: String =
      s"Income value was present but tax was not in tax band: $bandType, code: $code, rate: $rateOpt"
    def logMessageTaxNoIncome: String =
      s"Tax value was present at income was not in tax band: $bandType, code: $code, rate: $rateOpt"
    def logMessageMissingRate: String = s"Missing rate for tax band: $bandType, code: $code, rate: $rateOpt"

    (income, tax, rateOpt) match {
      case (Some(income), None, Some(rate)) if rate > BigDecimal(0) => logException(logMessageIncomeNoTax)
      case (Some(income), None, Some(rate))                         => logger.info(logMessageIncomeNoTax)
      case (None, Some(_), _)                                       => logException(logMessageTaxNoIncome)
      case (_, _, None)                                             => logException(logMessageMissingRate)
      case _                                                        => ()
    }

  }

  val taxBandReads: Reads[Option[TaxBand]] = (json: JsValue) => {
    val bandType = (json \ "bandType").as[String]
    val code = (json \ "taxCode").as[String]
    val income = (json \ "income").asOpt[BigDecimal]
    val tax = (json \ "tax").asOpt[BigDecimal]
    val lowerBand = (json \ "lowerBand").asOpt[BigDecimal]
    val upperBand = (json \ "upperBand").asOpt[BigDecimal]
    val rate = (json \ "rate").asOpt[BigDecimal]
    logging(income, tax, bandType, code, rate)

    def someTaxBand(income: BigDecimal, tax: BigDecimal, rate: BigDecimal): JsSuccess[Some[TaxBand]] =
      JsSuccess(Some(TaxBand(bandType, code, income, tax, lowerBand, upperBand, rate)))
    def noTaxBand: JsSuccess[Option[TaxBand]] = JsSuccess(None)

    (income, tax, rate) match {
      case (Some(income), Some(tax), Some(rate)) => someTaxBand(income, tax, rate)
      case (Some(income), None, Some(rate))      => someTaxBand(income, BigDecimal(0), rate)
      case _                                     => noTaxBand
    }
  }

  val incomeCategorySeqReads: Reads[Seq[IncomeCategory]] = (json: JsValue) => {
    val categoryNames =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val incomeCategoryList = incomeCategories(json, categoryNames)
    JsSuccess(incomeCategoryList)
  }

  val taxFreeAllowanceReads: Reads[BigDecimal] = (json: JsValue) => {
    val categoryNames = Seq("nonSavings", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    JsSuccess(
      categoryNames map (category =>
        (json \ "totalLiabilityDetails" \ category \ "allowanceReliefDeductionsDetails" \ "amount")
          .asOpt[BigDecimal] getOrElse BigDecimal(0)
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
      categories.map(category => (category, (json \ "totalLiabilityDetails" \ category).asOpt[JsObject])).toMap

    categoryMap.collect { case (category, Some(jsObject)) =>
      val totalTax = (jsObject \ "totalTax").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val totalTaxableIncome = (jsObject \ "totalTaxableIncome").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val totalIncome = (jsObject \ "totalIncomeDetails" \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      val taxBands =
        (jsObject \ "taxBandDetails").asOpt[Seq[TaxBand]](Reads.seq[Option[TaxBand]](taxBandReads).map(_.flatten))
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
