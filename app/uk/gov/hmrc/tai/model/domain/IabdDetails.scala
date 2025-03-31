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

import play.api.Logging
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.*
import play.api.libs.json.Reads.localDateReads
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
  captureDate: Option[LocalDate],
  grossAmount: Option[BigDecimal] = None
)

object IabdDetailsToggleOff extends IabdTypeConstants with Logging {
  private val dateReads: Reads[LocalDate] = localDateReads("dd/MM/yyyy")

  private val iabdReads: Reads[IabdDetails] =
    ((JsPath \ "nino").readNullable[String] and
      (JsPath \ "employmentSequenceNumber").readNullable[Int] and
      (JsPath \ "source").readNullable[Int] and
      (JsPath \ "type").readNullable[Int] and
      (JsPath \ "receiptDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "captureDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "grossAmount").readNullable[BigDecimal])(IabdDetails.apply _)

  implicit val reads: Reads[Seq[IabdDetails]] =
    __.read(Reads.seq(iabdReads))

  implicit val format: Format[IabdDetails] = {
    implicit val dateFormatter: Format[LocalDate] = formatLocalDateDDMMYYYY
    Json.format[IabdDetails]
  }
}

object IabdDetailsToggleOn extends IabdTypeConstants with Logging {
  private val dateReads: Reads[LocalDate] = localDateReads("yyyy-MM-dd")

  private def sourceReads: Reads[Option[Int]] = {
    case JsString(n) =>
      mapIabdSource.get(n) match {
        case Some(iabdSource) => JsSuccess(Some(iabdSource))
        case _ =>
          val errorMessage = s"Unknown iabd source: $n"
          logger.error(errorMessage, new RuntimeException(errorMessage))
          JsSuccess(None)

      }
    case e =>
      JsError(s"Invalid iabd source: $e")
  }

  private lazy val mapIabdSource: Map[String, Int] = Map(
    "Cutover"               -> 0,
    "P161"                  -> 1,
    "P161W"                 -> 2,
    "P9D"                   -> 3,
    "P50 CESSATION"         -> 4,
    "P50 UNEMPLOYMENT"      -> 5,
    "P52"                   -> 6,
    "P53A"                  -> 7,
    "P53B"                  -> 8,
    "P810"                  -> 9,
    "P85"                   -> 10,
    "P87"                   -> 11,
    "R27"                   -> 12,
    "R40"                   -> 13,
    "575T"                  -> 14,
    "TELEPHONE CALL"        -> 15,
    "LETTER"                -> 16,
    "EMAIL"                 -> 17,
    "AGENT CONTACT"         -> 18,
    "P11D (ECS)"            -> 19,
    "P46(CAR) (ECS)"        -> 20,
    "P11D (MANUAL)"         -> 21,
    "P46(CAR) (MANUAL)"     -> 22,
    "SA"                    -> 23,
    "OTHER FORM"            -> 24,
    "CALCULATION ONLY"      -> 25,
    "Annual Coding"         -> 26,
    "DWP Uprate"            -> 27,
    "Assessed P11D"         -> 28,
    "P11D/P9D Amended"      -> 29,
    "BULK EXPENSES"         -> 30,
    "ESA"                   -> 31,
    "Budget Coding"         -> 32,
    "SA Autocoding"         -> 33,
    "SPA AUTOCODING"        -> 34,
    "P46(DWP)"              -> 35,
    "P46(DWP) Uprated"      -> 36,
    "P46(PEN)"              -> 37,
    "ChB Online Service"    -> 38,
    "Internet"              -> 39,
    "Information Letter"    -> 40,
    "DWP Estimated JSA"     -> 41,
    "Payrolling BIK"        -> 42,
    "P53 (IYC)"             -> 43,
    "R40 (IYC)"             -> 44,
    "Lump Sum (IYC)"        -> 45,
    "Internet Calculated"   -> 46,
    "FPS(RTI)"              -> 47,
    "BBSI DI"               -> 48,
    "CALCULATED"            -> 49,
    "FWKS"                  -> 50,
    "Home Working Expenses" -> 51,
    "T&TSP"                 -> 52
  )

  private val iabdReads: Reads[IabdDetails] =
    ((JsPath \ "nationalInsuranceNumber").read[String] and
      (JsPath \ "employmentSequenceNumber").readNullable[Int] and
      (JsPath \ "source").readNullable[Option[Int]](sourceReads) and
      (JsPath \ "type").read[(String, Int)](readsTypeTuple) and
      (JsPath \ "receiptDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "captureDate").readNullable[LocalDate](dateReads) and
      (JsPath \ "grossAmount").readNullable[BigDecimal])(
      (nino, employmentSequenceNumber, source, iabdType, receiptDate, captureDate, grossAmount) =>
        IabdDetails
          .apply(
            Some(nino),
            employmentSequenceNumber,
            source.flatten,
            Some(iabdType._2),
            receiptDate,
            captureDate,
            grossAmount
          )
    )

  val reads: Reads[Seq[IabdDetails]] =
    (JsPath \ "iabdDetails").readNullable(Reads.seq(iabdReads)).map(_.getOrElse(Seq.empty))

}
