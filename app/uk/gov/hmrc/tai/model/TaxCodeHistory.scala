/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads, Writes}
import uk.gov.hmrc.tai.model.tai.TaxYear


case class TaxCodeHistory(nino: String, taxCodeRecords: Seq[TaxCodeRecord]) {
  def applicableTaxCodeRecords: Seq[TaxCodeRecord] = inYearTaxCodeRecords(operatedTaxCodeRecords(taxCodeRecords))

  private def operatedTaxCodeRecords(records: Seq[TaxCodeRecord]): Seq[TaxCodeRecord] = {
    records.filter(_.operatedTaxCode)
  }

  private def inYearTaxCodeRecords(records: Seq[TaxCodeRecord]): Seq[TaxCodeRecord] = {
    records.filter(_.taxYear.compare(TaxYear()) == 0)
  }
}

object TaxCodeHistory {

  implicit val reads: Reads[TaxCodeHistory] = (
    (JsPath \ "nino").read[String] and
      (JsPath \ "taxCodeRecord").read[Seq[TaxCodeRecord]]
    )(TaxCodeHistory.apply _)

  implicit val writes: Writes[TaxCodeHistory] = Json.writes[TaxCodeHistory]
}
