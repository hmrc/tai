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

import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.{minLength, _}
import play.api.libs.json.{JsPath, Json, Reads, Writes}


case class TaxCodeHistory(nino: String, taxCodeRecord: Seq[TaxCodeRecord]) {
  def operatedTaxCodeRecords: Seq[TaxCodeRecord] = taxCodeRecord.filter(_.operatedTaxCode)
}

object TaxCodeHistory {

  implicit val reads: Reads[TaxCodeHistory] = (
      (JsPath \ "nino").read[String] and
      (JsPath \ "taxCodeRecord").read[Seq[TaxCodeRecord]](minLength[Seq[TaxCodeRecord]](1))
    )(TaxCodeHistory.apply _)

  implicit val writes: Writes[TaxCodeHistory] = Json.writes[TaxCodeHistory]
}


