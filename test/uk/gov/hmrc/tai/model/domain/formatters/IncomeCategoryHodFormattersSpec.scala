/*
 * Copyright 2020 HM Revenue & Customs
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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import uk.gov.hmrc.tai.model.domain.calculation._

class IncomeCategoryHodFormattersSpec extends PlaySpec with IncomeCategoryHodFormatters {

  "incomeCategoriesReads" must {
    "return empty list" when {
      "totalLiabilities field is null" in {
        val json = Json.obj("totalLiability" -> JsNull)

        json.as[Seq[IncomeCategory]](incomeCategorySeqReads) mustBe empty
      }
      "all the 6 income categories as null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings"       -> JsNull,
            "untaxedInterest"  -> JsNull,
            "bankInterest"     -> JsNull,
            "ukDividends"      -> JsNull,
            "foreignInterest"  -> JsNull,
            "foreignDividends" -> JsNull
          ))

        json.as[Seq[IncomeCategory]](incomeCategorySeqReads) mustBe empty
      }
    }

    "return the list of the 6 income categories without tax bands" when {
      "all of the income categories are provided without tax bands" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalTax"           -> 1000.12,
              "totalTaxableIncome" -> 1000.13,
              "totalIncome" -> Json.obj(
                "amount" -> 1000.14
              )
            ),
            "untaxedInterest" -> Json.obj(),
            "bankInterest" -> Json.obj(
              "totalTax"           -> JsNull,
              "totalTaxableIncome" -> JsNull,
              "totalIncome"        -> JsNull
            ),
            "ukDividends" -> Json.obj(
              "totalIncome" -> Json.obj()
            ),
            "foreignInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "amount" -> JsNull
              )
            ),
            "foreignDividends" -> Json.obj(
              "totalTax"           -> 1000.23,
              "totalTaxableIncome" -> 1000.24,
              "totalIncome" -> Json.obj(
                "amount" -> 1000.25
              )
            )
          ))

        json.as[Seq[IncomeCategory]](incomeCategorySeqReads) must contain theSameElementsAs
          Seq(
            IncomeCategory(NonSavingsIncomeCategory, 1000.12, 1000.13, 1000.14, Nil),
            IncomeCategory(UntaxedInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(BankInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(UkDividendsIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(ForeignInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(ForeignDividendsIncomeCategory, 1000.23, 1000.24, 1000.25, Nil)
          )
      }
    }

    "return the list of the 6 income categories with their tax bands" when {
      "all of the income categories are provided with tax bands" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalTax"           -> 1000.12,
              "totalTaxableIncome" -> 1000.13,
              "totalIncome" -> Json.obj(
                "amount" -> 1000.14
              ),
              "taxBands" -> Json.arr(
                Json.obj(
                  "bandType"  -> JsNull,
                  "code"      -> JsNull,
                  "income"    -> JsNull,
                  "tax"       -> JsNull,
                  "lowerBand" -> JsNull,
                  "upperBand" -> JsNull,
                  "rate"      -> JsNull
                )
              )
            ),
            "untaxedInterest" -> Json.obj(),
            "bankInterest" -> Json.obj(
              "totalTax"           -> JsNull,
              "totalTaxableIncome" -> JsNull,
              "totalIncome"        -> JsNull,
              "taxBands"           -> JsNull
            ),
            "ukDividends" -> Json.obj(
              "totalIncome" -> Json.obj(),
              "taxBands" -> Json.arr(
                Json.obj(
                  "bandType"  -> JsNull,
                  "code"      -> JsNull,
                  "income"    -> JsNull,
                  "tax"       -> JsNull,
                  "lowerBand" -> JsNull,
                  "upperBand" -> JsNull,
                  "rate"      -> JsNull
                ),
                Json.obj(
                  "bandType"  -> "B",
                  "code"      -> "BR",
                  "income"    -> 10000,
                  "tax"       -> 500,
                  "lowerBand" -> 5000,
                  "upperBand" -> 20000,
                  "rate"      -> 10
                )
              )
            ),
            "foreignInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "amount" -> JsNull
              )
            ),
            "foreignDividends" -> Json.obj(
              "totalTax"           -> 1000.23,
              "totalTaxableIncome" -> 1000.24,
              "totalIncome" -> Json.obj(
                "amount" -> 1000.25
              )
            )
          ))

        json.as[Seq[IncomeCategory]](incomeCategorySeqReads) must contain theSameElementsAs
          Seq(
            IncomeCategory(
              NonSavingsIncomeCategory,
              1000.12,
              1000.13,
              1000.14,
              Seq(
                TaxBand(bandType = "", code = "", income = 0, tax = 0, lowerBand = None, upperBand = None, rate = 0))),
            IncomeCategory(UntaxedInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(BankInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(
              UkDividendsIncomeCategory,
              0,
              0,
              0,
              Seq(
                TaxBand(bandType = "", code = "", income = 0, tax = 0, lowerBand = None, upperBand = None, rate = 0),
                TaxBand(
                  bandType = "B",
                  code = "BR",
                  income = 10000,
                  tax = 500,
                  lowerBand = Some(5000),
                  upperBand = Some(20000),
                  rate = 10)
              )
            ),
            IncomeCategory(ForeignInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(ForeignDividendsIncomeCategory, 1000.23, 1000.24, 1000.25, Nil)
          )
      }
    }
  }

  "taxFreeAllowanceReads" must {
    "return taxFreeAllowance" when {
      "all the 6 income categories as null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings"       -> JsNull,
            "untaxedInterest"  -> JsNull,
            "bankInterest"     -> JsNull,
            "ukDividends"      -> JsNull,
            "foreignInterest"  -> JsNull,
            "foreignDividends" -> JsNull
          ))

        json.as[BigDecimal](taxFreeAllowanceReads) mustBe 0
      }

      "some income categories have allowance relief deduct" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "untaxedInterest" -> JsNull,
            "bankInterest"    -> JsNull,
            "ukDividends" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "foreignInterest"  -> JsNull,
            "foreignDividends" -> JsNull
          ))

        json.as[BigDecimal](taxFreeAllowanceReads) mustBe 200
      }

      "ignore untaxed interest income categories" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "untaxedInterest" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "bankInterest" -> JsNull,
            "ukDividends" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "foreignInterest"  -> JsNull,
            "foreignDividends" -> JsNull
          ))

        json.as[BigDecimal](taxFreeAllowanceReads) mustBe 200
      }

      "all income categories are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "untaxedInterest" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "bankInterest" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "ukDividends" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "foreignInterest" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            ),
            "foreignDividends" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "amount" -> 100
              )
            )
          ))

        json.as[BigDecimal](taxFreeAllowanceReads) mustBe 500
      }
    }
  }
}
