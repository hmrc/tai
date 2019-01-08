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

package uk.gov.hmrc.tai.model.domain.formatters.income

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.{Live, OtherBasisOperation, TaxCodeIncome}

class TaxCodeIncomeAPIFormattersSpec extends PlaySpec with TaxCodeIncomeSourceAPIFormatters {

  "taxComponentTypeWrites" must {
    "write tax component type correctly to json" when {
      "the type is a valid value" in {
        Json.toJson(EmploymentIncome)(taxComponentTypeWrites) mustBe JsString("EmploymentIncome")
      }
    }
  }

  "taxCodeIncomeSourceWrites" must {
    "write tax component correctly to json" when {
      "only mandatory fields are provided and tax code income type is employment" in {
        Json.toJson(TaxCodeIncome(EmploymentIncome, Some(1), 1111, "employment", "1150L",
          "employment", OtherBasisOperation, Live, BigDecimal(200.22), BigDecimal(0), BigDecimal(0)))(taxCodeIncomeSourceWrites) mustBe
          Json.obj(
            "componentType" -> "EmploymentIncome",
            "employmentId" -> 1,
            "amount" -> 1111,
            "description" -> "employment",
            "taxCode" -> "1150L",
            "name" -> "employment",
            "basisOperation" -> "OtherBasisOperation",
            "status" -> "Live",
            "inYearAdjustmentIntoCY" -> 200.22,
            "totalInYearAdjustment" -> 0,
            "inYearAdjustmentIntoCYPlusOne" -> 0
          )
      }
    }
  }

}
