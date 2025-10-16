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

/*
 * grossAmount:1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER
 * receiptDate:DD/MM/CCYY  THIS IS OPTIONAL - If populated it Must be in the format dd/mm/ccyy
 * @param grossAmount : 1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER
 */
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

case class IabdUpdateAmount(
  employmentSequenceNumber: Option[Int] = None,
  grossAmount: Int,
  netAmount: Option[Int] = None,
  receiptDate: Option[String] = None,
  source: Option[Int] = None,
  currentOptimisticLock: Option[Int] = None
) {
  require(grossAmount >= 0, "grossAmount cannot be less than 0")
}

object IabdUpdateAmount {

  private def empSeqNoFieldName = "employmentSequenceNumber"

  implicit val formats: Format[IabdUpdateAmount] =
    Format(
      Json.reads[IabdUpdateAmount],
      (
        (JsPath \ empSeqNoFieldName).writeNullable[Int] and
          (JsPath \ "grossAmount").write[Int] and
          (JsPath \ "netAmount").writeNullable[Int] and
          (JsPath \ "receiptDate").writeNullable[String] and
          (JsPath \ "source").writeNullable[Int] and
          (JsPath \ "currentOptimisticLock").writeNullable[Int]
      )(iadb =>
        (
          iadb.employmentSequenceNumber,
          iadb.grossAmount,
          iadb.netAmount,
          iadb.receiptDate,
          iadb.source,
          iadb.currentOptimisticLock
        )
      )
    )

  val writesHip: Writes[IabdUpdateAmount] =
    formats.transform(_.as[JsObject] + ("source" -> JsString("Cutover")))
}
