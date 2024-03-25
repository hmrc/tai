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

package uk.gov.hmrc.tai.model

import com.google.inject.Inject
import play.api.libs.functional.syntax._
import play.api.libs.json._

/*
  * grossAmount:1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER
  * receiptDate:DD/MM/CCYY  THIS IS OPTIONAL - If populated it Must be in the format dd/mm/ccyy"
  * @param grossAmount : 1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER
  */
case class IabdUpdateAmount(
  employmentSequenceNumber: Int,
  grossAmount: Int,
  netAmount: Option[Int] = None,
  receiptDate: Option[String] = None,
  source: Option[Int] = None
) {
  require(grossAmount >= 0, "grossAmount cannot be less than 0")
}

class IabdUpdateAmountFormats @Inject()() {

  def empSeqNoFieldName =
      "employmentSequenceNumber"

  def iabdUpdateAmountWrites: Writes[IabdUpdateAmount] =
    (
      (JsPath \ empSeqNoFieldName).write[Int] and
        (JsPath \ "grossAmount").write[Int] and
        (JsPath \ "netAmount").writeNullable[Int] and
        (JsPath \ "receiptDate").writeNullable[String] and
        (JsPath \ "source").writeNullable[Int]
    )(unlift(IabdUpdateAmount.unapply))

  implicit def formats: Format[IabdUpdateAmount] = Format(Json.reads[IabdUpdateAmount], iabdUpdateAmountWrites)

  implicit val formatList: Writes[IabdUpdateAmount] = new Writes[IabdUpdateAmount] {
    def writes(updateAmount: IabdUpdateAmount): JsValue =
      Json.arr(Json.toJson(updateAmount))
  }
}
