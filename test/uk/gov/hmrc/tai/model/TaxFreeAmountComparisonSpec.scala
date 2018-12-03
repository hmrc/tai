/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsPath, Json}
import uk.gov.hmrc.tai.model.domain.CarBenefit
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent

class TaxFreeAmountComparisonSpec extends PlaySpec {

  "TaxFreeAmountComparison Writes" must {
    "write the previous and current coding component sequences" when {
      "previous and current are non empty" in {

        val codingComponent = CodingComponent(CarBenefit, Some(1), 1, "test", Some(1))

        val model = TaxFreeAmountComparison(Seq(codingComponent), Seq(codingComponent))

        val expectedJson = Json.obj(
          "previous" -> Json.arr(
            Json.obj(
              "componentType" -> "CarBenefit",
              "employmentId" -> 1,
              "amount" -> 1,
              "description" -> "test",
              "iabdCategory" -> "Benefit",
              "inputAmount" -> 1
            )
          ),
          "current" -> Json.arr(
            Json.obj(
              "componentType" -> "CarBenefit",
              "employmentId" -> 1,
              "amount" -> 1,
              "description" -> "test",
              "iabdCategory" -> "Benefit",
              "inputAmount" -> 1
            )
          )
        )

        Json.toJson(model) mustEqual expectedJson
      }

      "previous and current are empty" in {
        val model = TaxFreeAmountComparison(Seq.empty, Seq.empty)

        val expectedJson = Json.obj(
          "previous" -> Json.arr(),
          "current" -> Json.arr()
        )

        Json.toJson(model) mustEqual expectedJson
      }
    }
  }
}

