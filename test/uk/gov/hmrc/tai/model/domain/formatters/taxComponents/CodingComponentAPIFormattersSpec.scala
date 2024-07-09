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

package uk.gov.hmrc.tai.model.domain.formatters.taxComponents

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.tai.model.domain.TaxComponentType.codingComponentTypeWrites
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent.codingComponentWrites

class CodingComponentAPIFormattersSpec extends PlaySpec {

  "codingComponentTypeWrites" must {
    "write tax component type correctly to json" when {
      "tax component type is having valid type" in {
        Json.toJson(GiftAidPayments)(codingComponentTypeWrites.writes(_)) mustBe JsString("GiftAidPayments")
      }
    }
  }

  "codingComponentWrites" must {
    "write tax component correctly to json" when {
      "only mandatory fields are provided and codingComponent is Allowance" in {
        Json.toJson(CodingComponent(GiftAidPayments, None, 1232, "Some Desc"))(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "GiftAidPayments",
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Allowance"
          )
      }
      "all the fields are provided and codingComponent is Allowance" in {
        Json.toJson(
          CodingComponent(GiftAidPayments, Some(111), 1232, "Some Desc", Some(12500))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "GiftAidPayments",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Allowance",
            "inputAmount"   -> 12500
          )
      }
      "all the fields are provided and codingComponent is Benefit" in {
        Json.toJson(
          CodingComponent(AssetTransfer, Some(111), 1232, "Some Desc", Some(BigDecimal("13200.01")))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "AssetTransfer",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Benefit",
            "inputAmount"   -> BigDecimal("13200.01")
          )
      }
      "all the fields are provided and codingComponent is Deduction" in {
        Json.toJson(
          CodingComponent(BalancingCharge, Some(111), 1232, "Some Desc", Some(12500))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "BalancingCharge",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Deduction",
            "inputAmount"   -> 12500
          )
      }
      "all the fields are provided and codingComponent is NonTaxCodeIncomeType" in {
        Json.toJson(CodingComponent(NonCodedIncome, Some(111), 1232, "Some Desc", Some(12500)))(
          codingComponentWrites
        ) mustBe
          Json.obj(
            "componentType" -> "NonCodedIncome",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "NonTaxCodeIncome",
            "inputAmount"   -> 12500
          )
      }
    }
    "throw a runtime exception" when {
      "the component type is not as expected" in {
        val ex = the[RuntimeException] thrownBy
          Json.toJson(CodingComponent(EmploymentIncome, Some(111), 1232, "Some Desc"))(codingComponentWrites)

        ex.getMessage mustBe "Unrecognised coding Component type"
      }
    }
  }
}
