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

package uk.gov.hmrc.tai.model.rti

import play.api.libs.json.{Format, JsArray, JsResult, JsSuccess, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.tai.model.LocalDateFormatter.formatLocalDate
import uk.gov.hmrc.tai.model.tai.JsonExtra

import java.time.LocalDate

/*
 *
 * @param payFrequency should really be at the employment record level
 * @param paidOn date the payment was made, can be no earlier than
 *   2014-04-06
 * @param submittedOn date the FPS submission containing this payment was
 *   received, can be no earlier than 2014-04-06
 * @param payId the employers payroll Id for this payment
 */
case class RtiPayment(
  payFrequency: PayFrequency.Value,
  paidOn: LocalDate,
  submittedOn: LocalDate,
  taxablePay: BigDecimal,
  taxablePayYTD: BigDecimal,
  taxed: BigDecimal,
  taxedYTD: BigDecimal,
  payId: Option[String] = None,
  isOccupationalPension: Boolean = false,
  occupationalPensionAmount: Option[BigDecimal] = None,
  weekOfTaxYear: Option[Int] = None,
  monthOfTaxYear: Option[Int] = None,
  nicPaid: Option[BigDecimal] = None,
  nicPaidYTD: Option[BigDecimal] = None
) extends Ordered[RtiPayment] {

  def compare(that: RtiPayment): Int = this.paidOn compareTo that.paidOn

  def isIrregular: Boolean = payFrequency == PayFrequency.Irregular
}

object RtiPayment {
  private val stringMapFormat: Format[Map[String, BigDecimal]] =
    JsonExtra.mapFormat[String, BigDecimal]("type", "amount")

  implicit val formatRtiPayment2: Format[RtiPayment] = Format(
    new Reads[RtiPayment] {
      override def reads(json: JsValue): JsResult[RtiPayment] = {

        val mma = (json \ "mandatoryMonetaryAmount")
          .as[Map[String, BigDecimal]](stringMapFormat)

        val oma = (json \ "optionalMonetaryAmount")
          .asOpt[Map[String, BigDecimal]](stringMapFormat)
          .getOrElse(Map())

        val niFigure = (json \ "niLettersAndValues")
          .asOpt[JsArray]
          .map(x => x \\ "niFigure")
          .flatMap(_.headOption)
          .map(_.asOpt[Map[String, BigDecimal]](stringMapFormat).getOrElse(Map.empty[String, BigDecimal]))

        val rti = RtiPayment(
          payFrequency = (json \ "payFreq").as[PayFrequency.Value],
          paidOn = (json \ "pmtDate").as[LocalDate](formatLocalDate),
          submittedOn = (json \ "rcvdDate").as[LocalDate](formatLocalDate),
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
          "payFreq"  -> Json.toJson(pay.payFrequency),
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
            )
          )
        )
    }
  )
}
