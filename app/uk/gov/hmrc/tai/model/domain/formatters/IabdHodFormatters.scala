/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.formatters

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import play.api.libs.json.{JsError, _}
import uk.gov.hmrc.tai.util.IabdTypeConstants

trait IabdHodFormatters extends IabdTypeConstants {

  val iabdEstimatedPayReads = new Reads[JsValue] {
    override def reads(json: JsValue): JsResult[JsValue] = {
      val iabdDetails = json.as[Seq[IabdDetails]]
      JsSuccess(Json.toJson(iabdDetails.filter(_.`type`.contains(NewEstimatedPay))))
    }
  }

  case class IabdDetails(
    nino: Option[String],
    employmentSequenceNumber: Option[Int],
    source: Option[Int],
    `type`: Option[Int],
    receiptDate: Option[LocalDate],
    captureDate: Option[LocalDate])

  object IabdDetails {
    implicit val formatLocalDate: Format[LocalDate] = Format(
      new Reads[LocalDate] {
        val dateRegex = """^(\d\d)/(\d\d)/(\d\d\d\d)$""".r

        override def reads(json: JsValue): JsResult[LocalDate] = json match {
          case JsString(dateRegex(d, m, y)) =>
            JsSuccess(LocalDate.of(y.toInt, m.toInt, d.toInt))
          case invalid => JsError(JsonValidationError(s"Invalid date format [dd/MM/yyyy]: $invalid"))
        }
      },
      new Writes[LocalDate] {
        val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        override def writes(date: LocalDate): JsValue =
          JsString(date.format(dateFormat))
      }
    )

    implicit val format: Format[IabdDetails] = Json.format[IabdDetails]
  }

}
