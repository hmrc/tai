/*
 * Copyright 2020 HM Revenue & Customs
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

import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat
import play.api.data.validation.ValidationError
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
            JsSuccess(new LocalDate(y.toInt, m.toInt, d.toInt))
          case invalid => JsError(ValidationError(s"Invalid date format [dd/MM/yyyy]: $invalid"))
        }
      },
      new Writes[LocalDate] {
        val dateFormat = DateTimeFormat.forPattern("dd/MM/yyyy")

        override def writes(date: LocalDate): JsValue =
          JsString(dateFormat.print(date))
      }
    )

    implicit val format: Format[IabdDetails] = Json.format[IabdDetails]
  }

}
