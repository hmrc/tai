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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsResultException, JsValue, Json, JsonValidationError}
import uk.gov.hmrc.tai.model.domain.AnnualAccount.annualAccountHodReads
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.io.File
import java.time.LocalDate
import scala.io.BufferedSource

class AdjustmentSpec extends PlaySpec {

  private def getAdjustmentJson(adjustmentType: AdjustmentType) =
    Json.obj(
      "type"   -> adjustmentType,
      "amount" -> 100
    )
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
        "type"   -> "INVALID",
        "amount" -> 100
      )

      val ex = the[IllegalArgumentException] thrownBy json.as[Adjustment]

      ex.getMessage mustBe "Invalid adjustment type"
    }

    "tax year is not valid" in {
      val ex = the[JsResultException] thrownBy Json.obj("year" -> "2017").as[TaxYear]
      ex.errors.flatMap(_._2) mustBe Seq(JsonValidationError("Expected JsNumber, found {\"year\":\"2017\"}"))
    }
  }
}
