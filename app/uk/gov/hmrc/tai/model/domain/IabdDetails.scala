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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.Reads.localDateReads
import play.api.libs.json._
import play.api.{Logger, Logging}
import uk.gov.hmrc.tai.util.DateTimeHelper.formatLocalDateDDMMYYYY
import uk.gov.hmrc.tai.util.IabdTypeConstants
import uk.gov.hmrc.tai.util.JsonHelper.readsTypeTuple

import java.time.LocalDate

case class IabdDetails(
  nino: Option[String],
  employmentSequenceNumber: Option[Int],
  source: Option[Int],
  `type`: Option[Int],
  receiptDate: Option[LocalDate],
  captureDate: Option[LocalDate]
)

object IabdDetailsToggleOff extends IabdTypeConstants with Logging {
  private val dateReads: Reads[LocalDate] = localDateReads("dd/MM/yyyy")

  private val iabdReads: Reads[IabdDetails] =
    ((JsPath \ "nino").readNullable[String] and
      (JsPath \ "employmentSequenceNumber").readNullable[Int] and
      (JsPath \ "source").readNullable[Int] and
      (JsPath \ "type").readNullable[Int] and
      (JsPath \ "receiptDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "captureDate").readNullable[LocalDate](dateReads))(IabdDetails.apply _)

  implicit val reads: Reads[Seq[IabdDetails]] =
    __.read(Reads.seq(iabdReads))

  implicit val format: Format[IabdDetails] = {
    implicit val dateFormatter: Format[LocalDate] = formatLocalDateDDMMYYYY
    Json.format[IabdDetails]
  }
}

object IabdDetailsToggleOn extends IabdTypeConstants {
  private val logger: Logger = Logger(getClass.getName)
  private val dateReads: Reads[LocalDate] = localDateReads("yyyy-MM-dd")

  private def sourceReads(source: Option[String]) =
    source match {
      case Some("TELEPHONE CALL")     => Some(15)
      case Some("EMAIL")              => Some(17)
      case Some("AGENT CONTACT")      => Some(18)
      case Some("LETTER")             => Some(16)
      case Some("OTHER FORM")         => Some(24)
      case Some("Internet")           => Some(39)
      case Some("Information Letter") => Some(40)
      case None                       => None
      case error =>
        val ex = new RuntimeException(s"$error is an invalid source")
        logger.error(ex.getMessage, ex)
        None
    }

  private val iabdReads: Reads[IabdDetails] =
    ((JsPath \ "nationalInsuranceNumber").read[String] and
      (JsPath \ "employmentSequenceNumber").readNullable[Int] and
      (JsPath \ "source").readNullable[String].map(sourceReads) and
      (JsPath \ "type").read[(String, Int)](readsTypeTuple) and
      (JsPath \ "receiptDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "captureDate").readNullable[LocalDate](dateReads))(
      (nino, employmentSequenceNumber, source, iabdType, receiptDate, captureDate) =>
        IabdDetails
          .apply(Some(nino), employmentSequenceNumber, source, Some(iabdType._2), receiptDate, captureDate)
    )

  val reads: Reads[Seq[IabdDetails]] =
    (JsPath \ "iabdDetails").read(Reads.seq(iabdReads))

}
