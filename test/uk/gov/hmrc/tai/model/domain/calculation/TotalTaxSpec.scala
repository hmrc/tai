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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsResultException, Json}
import uk.gov.hmrc.tai.model.domain.calculation.IncomeCategory.{incomeCategorySeqHipToggleOffReads, taxFreeAllowanceHipToggleOffReads}

class TotalTaxSpec extends PlaySpec {

  "incomeCategoriesReads" must {
    "return empty list" when {
      "totalLiabilities field is null" in {
        val json = Json.obj("totalLiability" -> JsNull)

        json.as[Seq[IncomeCategory]](incomeCategorySeqHipToggleOffReads) mustBe empty
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
          )
        )

        json.as[Seq[IncomeCategory]](incomeCategorySeqHipToggleOffReads) mustBe empty
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
          )
        )

        json.as[Seq[IncomeCategory]](incomeCategorySeqHipToggleOffReads) must contain theSameElementsAs
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
      "all of the income categories are provided with tax bands and income values greater than zero" in {
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
                  "bandType"  -> "PSR",
                  "taxCode"   -> "BR",
                  "income"    -> 500,
                  "tax"       -> JsNull,
                  "lowerBand" -> JsNull,
                  "upperBand" -> 5000,
                  "rate"      -> 0
                ),
                Json.obj(
                  "bandType"  -> "B",
                  "taxCode"   -> "BR",
                  "income"    -> 1000,
                  "tax"       -> 100,
                  "lowerBand" -> JsNull,
                  "upperBand" -> 10000,
                  "rate"      -> 10
                ),
                Json.obj(
                  "bandType"  -> "D",
                  "taxCode"   -> "D1",
                  "income"    -> 0,
                  "tax"       -> 0,
                  "lowerBand" -> 10000,
                  "upperBand" -> 30000,
                  "rate"      -> 15
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
                  "bandType"  -> "B",
                  "taxCode"   -> "BR",
                  "income"    -> 10000,
                  "tax"       -> 1000,
                  "lowerBand" -> 5000,
                  "upperBand" -> 20000,
                  "rate"      -> 10
                ),
                Json.obj(
                  "bandType"  -> "D",
                  "taxCode"   -> "D1",
                  "income"    -> 30000,
                  "tax"       -> 6000,
                  "lowerBand" -> 20000,
                  "upperBand" -> 50000,
                  "rate"      -> 20
                ),
                Json.obj(
                  "bandType"  -> "D",
                  "taxCode"   -> "D2",
                  "income"    -> 0,
                  "tax"       -> 0,
                  "lowerBand" -> 50000,
                  "upperBand" -> JsNull,
                  "rate"      -> 20
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
          )
        )

        json.as[Seq[IncomeCategory]](incomeCategorySeqHipToggleOffReads) must contain theSameElementsAs
          Seq(
            IncomeCategory(
              NonSavingsIncomeCategory,
              1000.12,
              1000.13,
              1000.14,
              Seq(
                TaxBand(
                  bandType = "PSR",
                  code = "BR",
                  income = 500,
                  tax = 0,
                  lowerBand = None,
                  upperBand = Some(5000),
                  rate = 0
                ),
                TaxBand(
                  bandType = "B",
                  code = "BR",
                  income = 1000,
                  tax = 100,
                  lowerBand = None,
                  upperBand = Some(10000),
                  rate = 10
                )
              )
            ),
            IncomeCategory(UntaxedInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(BankInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(
              UkDividendsIncomeCategory,
              0,
              0,
              0,
              Seq(
                TaxBand(
                  bandType = "B",
                  code = "BR",
                  income = 10000,
                  tax = 1000,
                  lowerBand = Some(5000),
                  upperBand = Some(20000),
                  rate = 10
                ),
                TaxBand(
                  bandType = "D",
                  code = "D1",
                  income = 30000,
                  tax = 6000,
                  lowerBand = Some(20000),
                  upperBand = Some(50000),
                  rate = 20
                )
              )
            ),
            IncomeCategory(ForeignInterestIncomeCategory, 0, 0, 0, Nil),
            IncomeCategory(ForeignDividendsIncomeCategory, 1000.23, 1000.24, 1000.25, Nil)
          )
      }
    }

    "throw a JsResult exception" when {
      "there is no tax code present in a tax band" in {
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
                  "bandType"  -> "B",
                  "taxCode"   -> "BR",
                  "income"    -> 1000,
                  "tax"       -> 100,
                  "lowerBand" -> JsNull,
                  "upperBand" -> 10000,
                  "rate"      -> 10
                ),
                Json.obj(
                  "bandType"  -> "D",
                  "taxCode"   -> "D1",
                  "income"    -> 0,
                  "tax"       -> 0,
                  "lowerBand" -> 10000,
                  "upperBand" -> 30000,
                  "rate"      -> 15
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
                  "bandType"  -> "B",
                  "taxCode"   -> JsNull,
                  "income"    -> 10000,
                  "tax"       -> 1000,
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
          )
        )
        val error = intercept[Exception] {
          json.as[Seq[IncomeCategory]](incomeCategorySeqHipToggleOffReads)
        }
        error mustBe a[JsResultException]
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
            )
          )

          json.as[BigDecimal](taxFreeAllowanceHipToggleOffReads) mustBe 0
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
            )
          )

          json.as[BigDecimal](taxFreeAllowanceHipToggleOffReads) mustBe 200
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
            )
          )

          json.as[BigDecimal](taxFreeAllowanceHipToggleOffReads) mustBe 200
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
            )
          )

          json.as[BigDecimal](taxFreeAllowanceHipToggleOffReads) mustBe 500
        }
      }
    }
  }
}
