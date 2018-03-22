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

package uk.gov.hmrc.tai.model.nps

import com.google.inject.{Inject, Singleton}
import play.api.Play
import play.api.libs.json._
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import play.api.libs.functional.syntax._

case class NpsIabdRoot (nino: String, employmentSequenceNumber: Option[Int] = None, `type`: Int, grossAmount : Option[BigDecimal] = None,
                        netAmount : Option[BigDecimal] = None, source:Option[Int]=None, receiptDate : Option[NpsDate] = None,
                        captureDate : Option[NpsDate] = None)

object NpsIabdRoot {
  implicit val formats = Json.format[NpsIabdRoot]
}


/**
 * grossAmount:1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER NO GREATER THAN 999999*
 * netAmount:1000    THIS IS OPTIONAL - IF POPULATED MUST BE A POSITIVE WHOLE NUMBER NO GREATER THAN 999999*
 * receiptDate:DD/MM/CCYY  THIS IS OPTIONAL - If populated it Must be in the format dd/mm/ccyy"
 * @param grossAmount
 */
case class NpsIabdUpdateAmount (
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

class NpsIabdUpdateAmountFormats @Inject()(config: FeatureTogglesConfig) {

  def empSeqNoFieldName =
    if (config.desUpdateEnabled) "employmentSeqNo"
    else "employmentSequenceNumber"

  def npsIabdUpdateAmountWrites: Writes[NpsIabdUpdateAmount] = (
    (JsPath \ empSeqNoFieldName).write[Int] and
      (JsPath \ "grossAmount").write[Int] and
      (JsPath \ "netAmount").writeNullable[Int] and
      (JsPath \ "receiptDate").writeNullable[String] and
      (JsPath \ "source").writeNullable[Int]
  )(unlift(NpsIabdUpdateAmount.unapply))

  implicit def formats = Format(Json.reads[NpsIabdUpdateAmount], npsIabdUpdateAmountWrites)

  implicit val formatList = new Writes[List[NpsIabdUpdateAmount]] {
    def writes(updateAmounts: List[NpsIabdUpdateAmount]) : JsValue = {
      Json.toJson(updateAmounts)
    }
  }
}
