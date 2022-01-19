/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.tai.model.domain.EmploymentIncome
import uk.gov.hmrc.tai.model.domain.income.{Live, TaxCodeIncome, Week1Month1BasisOperation}

object TaxCodeIncomeFactory {

  def create: TaxCodeIncome =
    TaxCodeIncome(
      EmploymentIncome,
      Some(1),
      0,
      "Test Desc",
      "K100",
      "Test Name",
      Week1Month1BasisOperation,
      Live,
      0,
      1,
      2)

  def createJson: JsObject =
    Json.obj(
      "componentType"                 -> EmploymentIncome.toString,
      "employmentId"                  -> 1,
      "amount"                        -> 0,
      "description"                   -> "Test Desc",
      "taxCode"                       -> "K100X",
      "name"                          -> "Test Name",
      "basisOperation"                -> Week1Month1BasisOperation.toString,
      "status"                        -> Live.toString,
      "inYearAdjustmentIntoCY"        -> 0,
      "totalInYearAdjustment"         -> 1,
      "inYearAdjustmentIntoCYPlusOne" -> 2
    )
}
