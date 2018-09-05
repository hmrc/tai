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

package uk.gov.hmrc.tai.model.des

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.tai.config.FeatureTogglesConfig

case class DesIabdUpdateAmount (
  employmentSequenceNumber: Int,
  grossAmount : Int,
  netAmount : Option[Int] = None,
  receiptDate : Option[String] = None,
  source : Option[Int]=None
  ) {
    require(grossAmount >= 0, "grossAmount cannot be less than 0")
    require(grossAmount <= 999999, "grossAmount cannot be greater than 999999")
    require(netAmount.forall(_ <= 999999))
  }

class DesIabdUpdateAmountFormats @Inject()(config: FeatureTogglesConfig) {

  def empSeqNoFieldName =
    if (config.desUpdateEnabled) "employmentSeqNo"
    else "employmentSequenceNumber"

  def npsIabdUpdateAmountWrites: Writes[DesIabdUpdateAmount] = (
    (JsPath \ empSeqNoFieldName).write[Int] and
      (JsPath \ "grossAmount").write[Int] and
      (JsPath \ "netAmount").writeNullable[Int] and
      (JsPath \ "receiptDate").writeNullable[String] and
      (JsPath \ "source").writeNullable[Int]
    )(unlift(DesIabdUpdateAmount.unapply))

  implicit def formats = Format(Json.reads[DesIabdUpdateAmount], npsIabdUpdateAmountWrites)

  implicit val formatList = new Writes[List[DesIabdUpdateAmount]] {
    def writes(updateAmounts: List[DesIabdUpdateAmount]) : JsValue = {
      Json.toJson(updateAmounts)
    }
  }
}