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

import java.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{Format, JsObject, JsString, JsSuccess, JsValue, Json}
import play.api.libs.json.JodaWrites._
import uk.gov.hmrc.tai.factory.TaxCodeIncomeFactory
import uk.gov.hmrc.tai.model.domain.income.{BasisOperation, OtherBasisOperation}

class TaxCodeIncomeSpec extends PlaySpec {

  val taxCodeIncome = TaxCodeIncomeFactory.create

  "TaxCodeIncomeSource taxCodeWithEmergencySuffix" must {
    "return the taxCode WITH X suffix" when {
      "the basis operation is week1Month1" in {
        taxCodeIncome.taxCodeWithEmergencySuffix mustBe "K100X"
      }
    }
    "return the taxCode WITHOUT X suffix" when {
      "the basis operation is NOT week1Month1" in {
        val model = taxCodeIncome.copy(basisOperation = OtherBasisOperation)
        model.taxCodeWithEmergencySuffix mustBe "K100"
      }
    }
  }

  "TaxCodeIncome Writes" must {
    "write the taxCode correctly" when {
      "BasisOfOperation is Week1Month1" in {

        val expectedJson = TaxCodeIncomeFactory.createJson
        Json.toJson(taxCodeIncome) mustEqual expectedJson
      }
      "BasisOfOperation is Other" in {

        val model = taxCodeIncome.copy(basisOperation = OtherBasisOperation)

        val expectedJson = TaxCodeIncomeFactory.createJson
        val updatedJson = expectedJson
          .as[JsObject] + ("taxCode" -> Json.toJson("K100")) + ("basisOperation" -> Json.toJson(OtherBasisOperation)(
          BasisOperation.formatBasisOperationType.writes))

        Json.toJson(model) mustEqual updatedJson
      }
    }

    "Handle nulls correctly" when {
      "updateNotificationDate is not null" in {
        val date: Option[LocalDate] = Some(LocalDate.now())

        val model = taxCodeIncome.copy(basisOperation = OtherBasisOperation, updateNotificationDate = date)

        val expectedJson = TaxCodeIncomeFactory.createJson
        val updatedJson = expectedJson
          .as[JsObject] + ("taxCode" -> Json.toJson("K100")) + ("basisOperation" -> Json.toJson(OtherBasisOperation)(
          BasisOperation.formatBasisOperationType.writes)) + ("updateNotificationDate" -> Json
          .toJson(date))

        Json.toJson(model) mustEqual updatedJson
      }
    }
  }
}
