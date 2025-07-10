/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.functional.syntax.*
import play.api.libs.json.{JsPath, Reads}
import uk.gov.hmrc.tai.model.domain.IabdSummary
import uk.gov.hmrc.tai.util.JsonHelper.readsTypeTuple

object IabdSummaryHipReads {

  implicit val iabdSummaryReads: Reads[IabdSummary] = (
    (JsPath \ "type").read[(String, Int)](readsTypeTuple) and
      (JsPath \ "employmentSequenceNumber").readNullable[Int] and
      (JsPath \ "amount").readNullable[BigDecimal].map(_.getOrElse(BigDecimal(0)))
  ).tupled.map { case ((_, typeKey), empSeqNo, amount) =>
    IabdSummary(
      componentType = typeKey,
      employmentId = empSeqNo,
      amount = amount
    )
  }
}
