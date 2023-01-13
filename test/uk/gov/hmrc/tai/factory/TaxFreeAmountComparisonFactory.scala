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

package uk.gov.hmrc.tai.factory

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.tai.model.TaxFreeAmountComparison
import uk.gov.hmrc.tai.model.domain.{CarBenefit, Mileage}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent

object TaxFreeAmountComparisonFactory {

  def create: TaxFreeAmountComparison = {
    val previous = Seq(CodingComponent(CarBenefit, Some(1), 1, "Car Benefit", Some(1)))
    val current = Seq(CodingComponent(Mileage, Some(2), 100, "Mileage", Some(100)))

    TaxFreeAmountComparison(
      previous,
      current
    )
  }

  def createJson: JsObject =
    Json.obj(
      "previous" -> Json.arr(
        Json.obj(
          "componentType" -> CarBenefit.toString,
          "employmentId"  -> 1,
          "amount"        -> 1,
          "description"   -> "Car Benefit",
          "iabdCategory"  -> "Benefit",
          "inputAmount"   -> 1
        )
      ),
      "current" -> Json.arr(
        Json.obj(
          "componentType" -> Mileage.toString,
          "employmentId"  -> 2,
          "amount"        -> 100,
          "description"   -> "Mileage",
          "iabdCategory"  -> "Benefit",
          "inputAmount"   -> 100
        )
      )
    )
}
