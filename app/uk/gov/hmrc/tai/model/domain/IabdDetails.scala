/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.income.IabdUpdateSource
import uk.gov.hmrc.tai.util.JsonHelper
import uk.gov.hmrc.tai.util.JsonHelper.readsTypeTuple

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class IabdDetails(
  nino: Option[String] = None,
  employmentSequenceNumber: Option[Int] = None,

  source: Option[Int] = None,
  `type`: Option[Int] = None,

  receiptDate: Option[LocalDate] = None,
  captureDate: Option[LocalDate] = None,

  grossAmount: Option[BigDecimal] = None,
  netAmount: Option[BigDecimal] = None
)

object IabdDetails extends Logging {

  private val isoFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val npsFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

  private val lenientLocalDateReads: Reads[LocalDate] = Reads {
    case JsString(s) =>
      def parse(fmt: DateTimeFormatter): Option[LocalDate] =
        scala.util.Try(LocalDate.parse(s, fmt)).toOption

      parse(isoFmt)
        .orElse(parse(npsFmt))
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid date format: $s (expected yyyy-MM-dd or dd/MM/yyyy)"))

    case other => JsError(s"Invalid JSON for LocalDate: $other")
  }

  private val localDateWrites: Writes[LocalDate] = Writes(date => JsString(date.format(isoFmt)))

  def sourceReads: Reads[Option[Int]] = new Reads[Option[Int]] {
    override def reads(json: JsValue): JsResult[Option[Int]] = json match {
      case JsString(n) =>
        mapIabdSource.get(n) match {
          case Some(code) => JsSuccess(Some(code))
          case None =>
            val msg = s"Unknown iabd source: $n"
            logger.warn(msg, new RuntimeException(msg))
            JsSuccess(None)
        }
      case JsNumber(n) if n.isValidInt => JsSuccess(Some(n.toInt))
      case JsNull                      => JsSuccess(None)
      case other                       => JsError(s"Invalid iabd source: $other")
    }
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
    "T&TSP"                 -> 52,
    "HICBC PAYE"            -> 53
  )

  implicit val writes: OWrites[IabdDetails] = new OWrites[IabdDetails] {
    private def removeNulls(js: JsObject): JsObject =
      JsObject(js.fields.collect {
        case (k, v: JsObject)      => k -> removeNulls(v)
        case (k, v) if v != JsNull => k -> v
      })

    override def writes(i: IabdDetails): JsObject =
      removeNulls(
        Json.obj(
          "nationalInsuranceNumber"  -> i.nino,
          "employmentSequenceNumber" -> i.employmentSequenceNumber,
          "source"                   -> i.source.map(IabdUpdateSource.fromCode),
          "type"                     -> i.`type`,
          "receiptDate"              -> i.receiptDate.map(Json.toJson(_)(localDateWrites)),
          "captureDate"              -> i.captureDate.map(Json.toJson(_)(localDateWrites)),
          "grossAmount"              -> i.grossAmount,
          "netAmount"                -> i.netAmount
        )
      )
  }

  private val singleReads: Reads[IabdDetails] = {
    val ninoReads: Reads[Option[String]] =
      (JsPath \ "nationalInsuranceNumber")
        .readNullable[String]
        .map(_.map(_.take(8)))
        .orElse((JsPath \ "nino").readNullable[String].map(_.map(_.take(8))))

    val typeReadsFlexible: Reads[Option[Int]] =
      (JsPath \ "type")
        .readNullable[(String, Int)](readsTypeTuple)
        .map(_.map(_._2))
        .orElse((JsPath \ "type").readNullable[Int])

    (
      ninoReads and
        (JsPath \ "employmentSequenceNumber").readNullable[Int] and
        (JsPath \ "source").readNullable[Option[Int]](sourceReads).map(_.flatten) and
        typeReadsFlexible and
        (JsPath \ "receiptDate").readNullable[LocalDate](lenientLocalDateReads) and
        (JsPath \ "captureDate").readNullable[LocalDate](lenientLocalDateReads) and
        (JsPath \ "grossAmount").readNullable[BigDecimal] and
        (JsPath \ "netAmount").readNullable[BigDecimal]
    )(IabdDetails.apply _)

  }

  implicit val readsSingle: Reads[IabdDetails] = singleReads

  implicit val listReads: Reads[List[IabdDetails]] =
    Reads.list(readsSingle)

  implicit val reads: Reads[Seq[IabdDetails]] =
    (JsPath \ "iabdDetails").readNullable(Reads.seq(singleReads)).map(_.getOrElse(Seq.empty))

  val singleObjectReads: Reads[IabdDetails] = singleReads
}
