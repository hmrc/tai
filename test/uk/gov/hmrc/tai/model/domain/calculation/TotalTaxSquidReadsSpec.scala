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
import play.api.libs.json.{JsNull, JsResultException, JsValue, Json}
import uk.gov.hmrc.tai.model.domain.calculation.TotalTaxSquidReads.{incomeCategorySeqReads, taxFreeAllowanceReads}

import scala.io.Source

class TotalTaxSquidReadsSpec extends PlaySpec {

  private val basePath = "test/resources/data/TaxAccount/TotalTax/nps/"
  private def readFile(fileName: String): JsValue = {
    val jsonFilePath = basePath + fileName
    val bufferedSource = Source.fromFile(jsonFilePath)
    val source = bufferedSource.mkString("")
    bufferedSource.close()
    Json.parse(source)
  }

  "incomeCategoriesReads" must {
    "return empty list" when {
      "totalLiabilities field is null" in {
        val payload = readFile("tc01.json")
        payload.as[Seq[IncomeCategory]](incomeCategorySeqReads) mustBe empty
      }
      "all the 6 income categories as null" in {
        val payload = readFile("tc02.json")
        payload.as[Seq[IncomeCategory]](incomeCategorySeqReads) mustBe empty
      }
    }

    "return the list of the 6 income categories without tax bands" when {
      "all of the income categories are provided without tax bands" in {
        val payload = readFile("tc03.json")
        payload.as[Seq[IncomeCategory]](incomeCategorySeqReads) must contain theSameElementsAs
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
        val payload = readFile("tc04.json")
        payload.as[Seq[IncomeCategory]](incomeCategorySeqReads) must contain theSameElementsAs
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
        val payload = readFile("tc05.json")
        val error = intercept[Exception] {
          payload.as[Seq[IncomeCategory]](incomeCategorySeqReads)
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

          json.as[BigDecimal](taxFreeAllowanceReads) mustBe 0
        }

        "some income categories have allowance relief deduct" in {
          val payload = readFile("tc07.json")
          payload.as[BigDecimal](taxFreeAllowanceReads) mustBe 200
        }

        "ignore untaxed interest income categories" in {
          val payload = readFile("tc08.json")
          payload.as[BigDecimal](taxFreeAllowanceReads) mustBe 200
        }

        "all income categories are present" in {
          val payload = readFile("tc09.json")
          payload.as[BigDecimal](taxFreeAllowanceReads) mustBe 500
        }
      }
    }
  }
}
