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

import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.SensitiveHelper.formatSensitiveJsValue

case class TaxCodeHistory(nino: String, taxCodeRecord: Seq[TaxCodeRecord]) {

  private val logger: Logger = Logger(getClass.getName)

  def applicableTaxCodeRecords: Seq[TaxCodeRecord] = {
    val applicableRecords = inYearTaxCodeRecords(operatedTaxCodeRecords(taxCodeRecord))
    logger.debug(s"applicableRecords are \n $applicableRecords")
    applicableRecords
  }

  private def operatedTaxCodeRecords(records: Seq[TaxCodeRecord]): Seq[TaxCodeRecord] =
    records.filter(_.operatedTaxCode)

  private def inYearTaxCodeRecords(records: Seq[TaxCodeRecord]): Seq[TaxCodeRecord] =
    records.filter(_.taxYear.compare(TaxYear()) == 0)
}

object TaxCodeHistory {
  private def reads(implicit crypto: Encrypter with Decrypter): Reads[TaxCodeHistory] = {
    val reads: Reads[TaxCodeHistory] = (
      (JsPath \ "nino").read[String] and
        (JsPath \ "taxCodeRecord").read[Seq[TaxCodeRecord]]
    )(TaxCodeHistory.apply _)

    formatSensitiveJsValue[JsObject].map { x =>
      reads.reads(x.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }
  }

  private def writes(implicit crypto: Encrypter): Writes[TaxCodeHistory] = { tch: TaxCodeHistory =>
    val writes: Writes[TaxCodeHistory] = Json.writes[TaxCodeHistory]
    val jsObject = writes.writes(tch).as[JsObject]
    JsString(crypto.encrypt(PlainText(Json.stringify(jsObject))).value)
  }

  implicit def format(implicit
    crypto: Encrypter with Decrypter
  ): Format[TaxCodeHistory] =
    Format(reads, writes)

}
