/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.LocalDate
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.slf4j._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.tai.{JsonExtra, TaxYear}

import scala.util.matching.Regex

package object rti {

  implicit val log: Logger = LoggerFactory.getLogger(this.getClass)

  implicit val freqFormat = JsonExtra.enumerationFormat(PayFrequency)

  implicit val stringMapFormat = JsonExtra.mapFormat[String, BigDecimal]("type", "amount")

  implicit val formatLocalDate: Format[LocalDate] = Format(
    new Reads[LocalDate] {
      val dateRegex: Regex = """^(\d\d\d\d)-(\d\d)-(\d\d)$""".r
      override def reads(json: JsValue): JsResult[LocalDate] = json match {
        case JsString(dateRegex(y, m, d)) =>
          JsSuccess(new LocalDate(y.toInt, m.toInt, d.toInt))
        case invalid => JsError(JsonValidationError(s"Invalid date format [yyyy-MM-dd]: $invalid"))
      }
    },
    new Writes[LocalDate] {
      val dateFormat: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd")
      override def writes(date: LocalDate): JsValue =
        JsString(dateFormat.print(date))
    }
  )

  implicit val formatRtiPayment2: Format[RtiPayment] = Format(
    new Reads[RtiPayment] {
      override def reads(json: JsValue): JsResult[RtiPayment] = {

        val mma = (json \ "mandatoryMonetaryAmount")
          .as[Map[String, BigDecimal]]

        val oma = (json \ "optionalMonetaryAmount")
          .asOpt[Map[String, BigDecimal]]
          .getOrElse(Map())

        val niFigure = (json \ "niLettersAndValues")
          .asOpt[JsArray]
          .map(x => x \\ "niFigure")
          .flatMap(_.headOption)
          .map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map.empty[String, BigDecimal]))

        val rti = RtiPayment(
          payFrequency = (json \ "payFreq").as[PayFrequency.Value],
          paidOn = (json \ "pmtDate").as[LocalDate],
          submittedOn = (json \ "rcvdDate").as[LocalDate],
          taxablePay = mma("TaxablePay"),
          taxablePayYTD = mma("TaxablePayYTD"),
          taxed = mma("TaxDeductedOrRefunded"),
          taxedYTD = mma("TotalTaxYTD"),
          payId = (json \ "payId").asOpt[String],
          isOccupationalPension = (json \ "occPenInd").asOpt[Boolean].getOrElse(false),
          occupationalPensionAmount = oma.get("OccPensionAmount"),
          weekOfTaxYear = (json \ "weekNo").asOpt[String].map(_.toInt),
          monthOfTaxYear = (json \ "monthNo").asOpt[String].map(_.toInt),
          nicPaid = niFigure.flatMap(_.get("EmpeeContribnsInPd")),
          nicPaidYTD = niFigure.flatMap(_.get("EmpeeContribnsYTD"))
        )

        JsSuccess(rti)
      }
    },
    new Writes[RtiPayment] {
      override def writes(pay: RtiPayment): JsValue =
        Json.obj(
          "payFreq"  -> pay.payFrequency,
          "pmtDate"  -> pay.paidOn,
          "rcvdDate" -> pay.submittedOn,
          "mandatoryMonetaryAmount" -> Seq(
            "TaxablePayYTD"         -> pay.taxablePayYTD,
            "TotalTaxYTD"           -> pay.taxedYTD,
            "TaxablePay"            -> pay.taxablePay,
            "TaxDeductedOrRefunded" -> pay.taxed
          ).map { e =>
            Json.obj("type" -> e._1, "amount" -> e._2)
          },
          "optionalMonetaryAmount" -> Seq(
            pay.occupationalPensionAmount.map { e =>
              "OccPensionAmount" -> e
            }
          ).flatten.map { e =>
            Json.obj("type" -> e._1, "amount" -> e._2)
          },
          "payId"     -> pay.payId,
          "occPenInd" -> pay.isOccupationalPension,
          "irrEmp"    -> pay.isIrregular,
          "weekNo"    -> pay.weekOfTaxYear.map(_.toString),
          "monthNo"   -> pay.monthOfTaxYear.map(_.toString),
          "niLettersAndValues" -> Json.arr(
            Json.obj(
              "niFigure" -> Seq(
                pay.nicPaid.map { e =>
                  "EmpeeContribnsInPd" -> e
                },
                pay.nicPaidYTD.map { e =>
                  "EmpeeContribnsYTD" -> e
                }
              ).flatten.map { e =>
                Json.obj("type" -> e._1, "amount" -> e._2)
              }
            ))
        )
    }
  )

  implicit val formatRtiEyu: Format[RtiEyu] = Format(
    new Reads[RtiEyu] {
      override def reads(json: JsValue): JsResult[RtiEyu] = {

        val optionalAdjustmentAmountMap =
          (json \ "optionalAdjustmentAmount").asOpt[Map[String, BigDecimal]].getOrElse(Map.empty[String, BigDecimal])

        val niFigure = (json \ "niLettersAndValues")
          .asOpt[JsArray]
          .map(x => x \\ "niFigure")
          .flatMap(_.headOption)
          .map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map.empty[String, BigDecimal]))

        val rcvdDate = (json \ "rcvdDate").as[LocalDate]

        val eyu = RtiEyu(
          taxablePayDelta = optionalAdjustmentAmountMap.get("TaxablePayDelta"),
          totalTaxDelta = optionalAdjustmentAmountMap.get("TotalTaxDelta"),
          empeeContribnsDelta = niFigure.flatMap(_.get("EmpeeContribnsDelta")),
          rcvdDate = rcvdDate
        )

        JsSuccess(eyu)
      }
    },
    new Writes[RtiEyu] {
      override def writes(eyu: RtiEyu): JsValue = {

        val formSeqElement = (typeName: String, amount: Option[BigDecimal]) =>
          if (amount.isDefined) {
            Seq(typeName -> amount)
          } else {
            Seq[(String, Option[BigDecimal])]()
        }

        val optionalAdjustmentAmount: Seq[(String, Option[BigDecimal])] =
          formSeqElement("TaxablePayDelta", eyu.taxablePayDelta) ++
            formSeqElement("TotalTaxDelta", eyu.totalTaxDelta)

        val niFigureAmount: Seq[Option[(String, BigDecimal)]] =
          if (eyu.empeeContribnsDelta.isDefined) {
            Seq(
              eyu.empeeContribnsDelta.map { element =>
                "EmpeeContribnsDelta" -> element
              }
            )
          } else {
            Seq[Option[(String, BigDecimal)]]()
          }

        Json.obj(
          "optionalAdjustmentAmount" -> optionalAdjustmentAmount.map { element =>
            Json.obj("type" -> element._1, "amount" -> element._2)
          },
          "niLettersAndValues" -> Json.arr(
            Json.obj(
              "niFigure" -> niFigureAmount.map { element =>
                Json.obj(
                  "type"   -> element.fold("")(ele => ele._1),
                  "amount" -> element.fold(BigDecimal(0))(ele => ele._2))
              }
            )),
          "rcvdDate" -> eyu.rcvdDate
        )
      }
    }
  )

  implicit val formatRtiPaymentList: Format[List[RtiPayment]] =
    JsonExtra.bodgeList[RtiPayment]

  implicit val formatRtiEyuList: Format[List[RtiEyu]] =
    JsonExtra.bodgeList[RtiEyu]

  implicit val formatRtiEmployment: Format[RtiEmployment] = (
    (__ \ "empRefs" \ "officeNo").format[String] and
      (__ \ "empRefs" \ "payeRef").format[String] and
      (__ \ "empRefs" \ "aoRef").format[String] and
      (__ \ "payments" \ "inYear")
        .formatNullable[List[RtiPayment]]
        .inmap[List[RtiPayment]](
          o => o.map(_.sorted).getOrElse(List.empty[RtiPayment]),
          s => if (s.isEmpty) Some(Nil) else Some(s)
        ) and
      (__ \ "payments" \ "eyu")
        .formatNullable[List[RtiEyu]]
        .inmap[List[RtiEyu]](
          o => o.map(_.sorted).getOrElse(List.empty[RtiEyu]),
          s => if (s.isEmpty) Some(Nil) else Some(s)
        ) and
      (__ \ "currentPayId").formatNullable[String] and
      (__ \ "sequenceNumber").format[Int]
  )(RtiEmployment.apply, unlift(RtiEmployment.unapply))

  implicit val formatRtiData: Format[RtiData] =
    ((__ \ "request" \ "nino").format[String] and
      (__ \ "request" \ "relatedTaxYear")
        .format[String]
        .inmap[TaxYear](
          o => TaxYear(o),
          s => s.twoDigitRange
        ) and
      (__ \ "request" \ "requestId").format[String] and
      (__ \ "individual" \ "employments" \ "employment")
        .format[List[RtiEmployment]])(RtiData.apply, unlift(RtiData.unapply))
}
