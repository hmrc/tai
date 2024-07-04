package uk.gov.hmrc.tai.model.domain

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, JsonValidationError, Reads, Writes}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.util.matching.Regex

case class IabdDetails(
                        nino: Option[String],
                        employmentSequenceNumber: Option[Int],
                        source: Option[Int],
                        `type`: Option[Int],
                        receiptDate: Option[LocalDate],
                        captureDate: Option[LocalDate]
                      )

object IabdDetails {
  implicit val formatLocalDate: Format[LocalDate] = Format(
    new Reads[LocalDate] {
      val dateRegex: Regex = """^(\d\d)/(\d\d)/(\d\d\d\d)$""".r

      override def reads(json: JsValue): JsResult[LocalDate] = json match {
        case JsString(dateRegex(d, m, y)) =>
          JsSuccess(LocalDate.of(y.toInt, m.toInt, d.toInt))
        case invalid => JsError(JsonValidationError(s"Invalid date format [dd/MM/yyyy]: $invalid"))
      }
    },
    new Writes[LocalDate] {
      val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

      override def writes(date: LocalDate): JsValue =
        JsString(date.format(dateFormat))
    }
  )

  val iabdEstimatedPayReads: Reads[JsValue] = new Reads[JsValue] {
    override def reads(json: JsValue): JsResult[JsValue] = {
      val iabdDetails = json.as[Seq[IabdDetails]]
      JsSuccess(Json.toJson(iabdDetails.filter(_.`type`.contains(NewEstimatedPay))))
    }
  }

  implicit val format: Format[IabdDetails] = Json.format[IabdDetails]
}
