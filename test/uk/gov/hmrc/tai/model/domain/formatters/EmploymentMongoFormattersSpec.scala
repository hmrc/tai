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

package uk.gov.hmrc.tai.model.domain.formatters

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNumber, JsString, Json}
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear

class EmploymentMongoFormattersSpec extends PlaySpec with EmploymentMongoFormatters {

  "EmploymentMongoFormatter" should {

    "un-marshall adjustment json" when {
      "reading from a json with type NI" in {
        val adjustment = getAdjustmentJson(NationalInsuranceAdjustment).as[Adjustment]

        adjustment.`type` mustBe NationalInsuranceAdjustment
        adjustment.amount mustBe BigDecimal(100)
      }

      "reading from a json with type IncomeAdjustment" in {
        val adjustment = getAdjustmentJson(IncomeAdjustment).as[Adjustment]

        adjustment.`type` mustBe IncomeAdjustment
        adjustment.amount mustBe BigDecimal(100)
      }

      "reading from a json with type TaxAdjustment" in {
        val adjustment = getAdjustmentJson(TaxAdjustment).as[Adjustment]

        adjustment.`type` mustBe TaxAdjustment
        adjustment.amount mustBe BigDecimal(100)
      }

    }

    "marshall adjustment json" when {
      "NationalInsuranceAdjustment type has been passed" in {
        val adjustment = Adjustment(NationalInsuranceAdjustment, 100)

        val json = Json.toJson(adjustment)

        json mustBe getAdjustmentJson(NationalInsuranceAdjustment)
      }

      "TaxAdjustment type has been passed" in {
        val adjustment = Adjustment(TaxAdjustment, 100)

        val json = Json.toJson(adjustment)

        json mustBe getAdjustmentJson(TaxAdjustment)
      }

      "IncomeAdjustment type has been passed" in {
        val adjustment = Adjustment(IncomeAdjustment, 100)

        val json = Json.toJson(adjustment)

        json mustBe getAdjustmentJson(IncomeAdjustment)
      }
    }

    "throw an exception" when {
      "adjustment type doesn't match" in {

        val json = Json.obj(
          "type" -> "INVALID",
          "amount" -> 100
        )

        val ex = the[IllegalArgumentException] thrownBy json.as[Adjustment]

        ex.getMessage mustBe "Invalid adjustment type"
      }

      "tax year is not valid" in {
        val ex = the[IllegalArgumentException] thrownBy Json.obj("year" -> "2017").as[TaxYear]

        ex.getMessage mustBe "Invalid tax year"
      }
    }

    "un-marshall Tax year Json" when {
      "given a TaxYear object" in {
        Json.toJson(TaxYear(2017)) mustBe JsNumber(2017)
      }
    }

    "marshall valid TaxYear object" when {
      "given a valid json value" in {
        JsNumber(2017).as[TaxYear] mustBe TaxYear(2017)
      }
    }

    "un-marshall and marshal valid Employment json and object" when {
      "given a valid Employment object and json" in {
        val employmentDetails = List(Employment("TEST", Some("12345"), new LocalDate("2017-05-26"), None,
          List(AnnualAccount("", TaxYear(2017), Available,
            List(Payment(new LocalDate("2017-05-26"), 10, 10, 10, 10, 10, 10, Monthly, Some(true))),
            List(EndOfTaxYearUpdate(new LocalDate("2017-05-26"), List(Adjustment(NationalInsuranceAdjustment, 10)))))), "", "", 2, Some(100), false, false))

        val json = Json.arr(
          Json.obj(
            "name" -> "TEST",
            "payrollNumber" -> "12345",
            "startDate" -> "2017-05-26",
            "annualAccounts" -> Json.arr(
              Json.obj(
                "key" -> "",
                "taxYear" -> 2017,
                "realTimeStatus" -> "Available",
                "payments" -> Json.arr(
                  Json.obj(
                    "date" -> "2017-05-26",
                    "amountYearToDate" -> 10,
                    "taxAmountYearToDate" -> 10,
                    "nationalInsuranceAmountYearToDate" -> 10,
                    "amount" -> 10,
                    "taxAmount" -> 10,
                    "nationalInsuranceAmount" -> 10,
                    "payFrequency" -> "Monthly",
                    "duplicate" -> true
                  )
                ),
                "endOfTaxYearUpdates" -> Json.arr(
                  Json.obj(
                    "date" -> "2017-05-26",
                    "adjustments" -> Json.arr(
                      Json.obj(
                        "type" -> "NationalInsuranceAdjustment",
                        "amount" -> 10
                      )
                    )
                  )
                )
              )
            ),
            "taxDistrictNumber" -> "",
            "payeNumber" -> "",
            "sequenceNumber" -> 2,
            "cessationPay" -> 100,
            "hasPayrolledBenefit" -> false,
            "receivingOccupationalPension" -> false
          )
        )

        Json.toJson(employmentDetails) mustBe json

        json.as[Seq[Employment]] mustBe employmentDetails

      }
    }

  }

  private def getAdjustmentJson(adjustmentType: AdjustmentType) =
    Json.obj(
      "type" -> adjustmentType,
      "amount" -> 100
    )
}
