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

import play.api.libs.json._

sealed trait AdjustmentType

case object NationalInsuranceAdjustment extends AdjustmentType

case object TaxAdjustment extends AdjustmentType

case object IncomeAdjustment extends AdjustmentType

object AdjustmentType extends AdjustmentType {
  implicit val formatAdjustmentType = new Format[AdjustmentType] {
    override def reads(json: JsValue): JsSuccess[AdjustmentType] = json.as[String] match {
      case "NationalInsuranceAdjustment" => JsSuccess(NationalInsuranceAdjustment)
      case "TaxAdjustment"               => JsSuccess(TaxAdjustment)
      case "IncomeAdjustment"            => JsSuccess(IncomeAdjustment)
      case _                             => throw new IllegalArgumentException("Invalid adjustment type")
    }

    override def writes(adjustmentType: AdjustmentType) = JsString(adjustmentType.toString)
  }
}
