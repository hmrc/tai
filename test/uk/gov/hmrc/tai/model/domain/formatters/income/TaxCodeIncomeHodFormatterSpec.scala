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

package uk.gov.hmrc.tai.model.domain.formatters.income

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income._

class TaxCodeIncomeHodFormatterSpec extends PlaySpec with TaxCodeIncomeHodFormatters {

  "taxCodeIncomeSourceReads should use totalTaxableIncome to read amount" must {
    "return a value for the total taxable income from NPS tax response" when {

      "estimated pay is available in iabds" in {

        val totalIncome = Json.obj(
          "npsDescription" -> JsNull,
          "amount"         -> JsNumber(1234),
          "iabdSummaries" -> JsArray(
            Seq(
              Json.obj(
                "amount"         -> JsNumber(111),
                "type"           -> JsNumber(27),
                "employmentId"   -> JsNumber(1),
                "npsDescription" -> JsString("New Estimated Pay")
              )))
        )

        val jsonNpsTax =
          Json.obj("totalIncome" -> totalIncome, "totalTaxableIncome" -> JsNumber(1111), "totalTax" -> JsNumber(222))

        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(true),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("PensionProvider1"),
          "basisOperation"             -> JsNumber(2),
          "employmentStatus"           -> JsNumber(1)
        )

        val result = json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        result.amount mustBe 111
      }

      "estimated pay is available in iabds for the correct employment id" in {

        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(true),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("PensionProvider1"),
          "basisOperation"             -> JsNumber(2),
          "employmentStatus"           -> JsNumber(1)
        )

        val result = json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        result.amount mustBe 1111
      }
    }

    "return 0 as amount" when {

      "pay and tax is not available" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(true),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("PensionProvider1"),
          "basisOperation"             -> JsNumber(2),
          "employmentStatus"           -> JsNumber(1)
        )

        val result = json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        result.amount mustBe 0
      }

      "employmentId does not match" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(3),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(true),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("PensionProvider1"),
          "basisOperation"             -> JsNumber(2),
          "employmentStatus"           -> JsNumber(1)
        )

        val result = json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        result.amount mustBe 0
      }
    }

  }

  "taxCodeIncomeSourceReads" must {
    "read taxCodeIncome" when {
      "all income source indicators are false" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("Employer1"),
          "basisOperation"             -> JsNumber(1),
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(1111),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      }

      "pension indicator is true" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(true),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsString("PensionProvider1"),
          "basisOperation"             -> JsNumber(2),
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          PensionIncome,
          Some(1),
          BigDecimal(1111),
          "PensionIncome",
          "1150L",
          "PensionProvider1",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }

      "jobSeekers indicator is true" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(true),
          "otherIncomeSourceIndicator" -> JsBoolean(false),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          JobSeekerAllowanceIncome,
          Some(1),
          BigDecimal(1111),
          "JobSeekerAllowanceIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      }

      "other income source indicator is true" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(1111),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }

      "employment id is not available" in {
        val json = Json.obj(
          "employmentId"               -> JsNull,
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          None,
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }

      "taxCode is not available" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsNull,
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(1111),
          "OtherIncome",
          "",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }

    "utilize totalTaxableIncomeReads to read amount" when {
      "provided with whole json" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> jsonNpsTax,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(1111),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }

      "provided with missing payAndTax element" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }
  }

  "taxAccountReads" must {
    "deserialize the tax account json into the case class with none values" when {
      "none of the fields are provided" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017)
        )
        json.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads) mustBe Nil
      }

      "fields provided with null values" in {
        val json = Json.obj(
          "taxYear"        -> JsNumber(2017),
          "totalLiability" -> JsNull,
          "incomeSources"  -> JsNull
        )
        json.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads) mustBe Nil
      }
    }

    "deserialize the tax account json into the case class with all values" when {
      "all of the fields are provided" in {
        val json = Json.obj(
          "taxYear" -> JsNumber(2017),
          "totalLiability" -> Json.obj(
            "untaxedInterest" -> Json.obj(
              "totalTaxableIncome" -> JsNumber(123)
            )
          ),
          "incomeSources" -> JsArray(
            Seq(
              Json.obj(
                "employmentId"     -> JsNumber(1),
                "taxCode"          -> JsString("1150L"),
                "name"             -> JsString("Employer1"),
                "basisOperation"   -> JsNumber(1),
                "employmentStatus" -> JsNumber(1)
              ),
              Json.obj(
                "employmentId"     -> JsNumber(2),
                "taxCode"          -> JsString("1100L"),
                "name"             -> JsString("Employer2"),
                "basisOperation"   -> JsNumber(2),
                "employmentStatus" -> JsNumber(1)
              )
            ))
        )

        json.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads) mustBe
          Seq(
            TaxCodeIncome(
              EmploymentIncome,
              Some(1),
              BigDecimal(0),
              "EmploymentIncome",
              "1150L",
              "Employer1",
              Week1Month1BasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0)
            ),
            TaxCodeIncome(
              EmploymentIncome,
              Some(2),
              BigDecimal(0),
              "EmploymentIncome",
              "1100L",
              "Employer2",
              OtherBasisOperation,
              Live,
              BigDecimal(0),
              BigDecimal(0),
              BigDecimal(0))
          )
      }
    }
  }

  "taxCodeIncomeSourceReads using employmentStatus" must {
    "read status as live" when {
      "provided with employmentStatus as 1" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(1)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }

    "read status as potentially ceased" when {
      "provided with employmentStatus as 2" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(2)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          PotentiallyCeased,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }

    "read status as ceased" when {
      "provided with employmentStatus as 3" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(3)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Ceased,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }

    "read in year adjustment amounts" when {
      "values are present within tax account JSON" in {
        val json = Json.obj(
          "employmentId"                  -> JsNumber(1),
          "taxCode"                       -> JsString("1150L"),
          "payAndTax"                     -> JsNull,
          "pensionIndicator"              -> JsBoolean(false),
          "jsaIndicator"                  -> JsBoolean(false),
          "otherIncomeSourceIndicator"    -> JsBoolean(true),
          "name"                          -> JsNull,
          "basisOperation"                -> JsNull,
          "employmentStatus"              -> JsNumber(3),
          "inYearAdjustmentIntoCY"        -> JsNumber(10.20),
          "totalInYearAdjustment"         -> JsNumber(30.40),
          "inYearAdjustmentIntoCYPlusOne" -> JsNumber(10.60)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Ceased,
          BigDecimal(10.20),
          BigDecimal(30.40),
          BigDecimal(10.60))
      }
    }

    "report zero value in year adjustment amounts" when {
      "values are absent from tax account JSON" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(3)
        )

        json.as[TaxCodeIncome](taxCodeIncomeSourceReads) mustBe TaxCodeIncome(
          OtherIncome,
          Some(1),
          BigDecimal(0),
          "OtherIncome",
          "1150L",
          "",
          OtherBasisOperation,
          Ceased,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0))
      }
    }

    "error out" when {
      "provided with employmentStatus as 4" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNumber(4)
        )

        val ex = the[RuntimeException] thrownBy json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        ex.getMessage mustBe "Invalid employment status"
      }

      "employmentStatus is not available" in {
        val json = Json.obj(
          "employmentId"               -> JsNumber(1),
          "taxCode"                    -> JsString("1150L"),
          "payAndTax"                  -> JsNull,
          "pensionIndicator"           -> JsBoolean(false),
          "jsaIndicator"               -> JsBoolean(false),
          "otherIncomeSourceIndicator" -> JsBoolean(true),
          "name"                       -> JsNull,
          "basisOperation"             -> JsNull,
          "employmentStatus"           -> JsNull
        )

        val ex = the[RuntimeException] thrownBy json.as[TaxCodeIncome](taxCodeIncomeSourceReads)
        ex.getMessage mustBe "Invalid employment status"
      }
    }
  }

  "newEstimatedPayTypeFilter" must {
    "return true" when {
      "new estimated pay is available" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        newEstimatedPayTypeFilter(iabd) mustBe true
      }
    }

    "return false" when {
      "new estimated pay is not available" in {
        val iabd = IabdSummary(28, Some(1), 11111)
        newEstimatedPayTypeFilter(iabd) mustBe false
      }
    }
  }

  "employmentFilter" must {
    "return true" when {
      "iabd employment match with nps employment" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        employmentFilter(iabd, Some(1)) mustBe true
      }
    }

    "return false" when {
      "iabd employment does not match nps employment" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        employmentFilter(iabd, Some(2)) mustBe false
      }

      "iabd employment is not available" in {
        val iabd = IabdSummary(27, None, 11111)
        employmentFilter(iabd, Some(2)) mustBe false
      }

      "nps employment is not available" in {
        val iabd = IabdSummary(27, Some(1), 11111)
        employmentFilter(iabd, None) mustBe false
      }

      "iabd employment and nps employment are not available" in {
        val iabd = IabdSummary(27, None, 11111)
        employmentFilter(iabd, None) mustBe false
      }
    }
  }

  val totalIncome: JsObject = Json.obj(
    "npsDescription" -> JsNull,
    "amount"         -> JsNumber(1234),
    "iabdSummaries" -> JsArray(
      Seq(
        Json.obj(
          "amount"         -> JsNumber(1111),
          "type"           -> JsNumber(27),
          "employmentId"   -> JsNumber(1),
          "npsDescription" -> JsString("New Estimated Pay")
        ),
        Json.obj(
          "amount"         -> JsNumber(1111),
          "type"           -> JsNumber(29),
          "employmentId"   -> JsNumber(1),
          "npsDescription" -> JsString("New Estimated Pay")
        ),
        Json.obj(
          "amount"         -> JsNumber(1111),
          "type"           -> JsNumber(27),
          "employmentId"   -> JsNumber(2),
          "npsDescription" -> JsString("New Estimated Pay")
        )
      ))
  )

  val jsonNpsTax: JsObject = Json.obj(
    "totalIncome"        -> totalIncome,
    "allowReliefDeducts" -> JsNull,
    "totalTaxableIncome" -> JsNumber(1111),
    "totalTax"           -> JsNumber(222),
    "taxBands"           -> JsNull)
}
