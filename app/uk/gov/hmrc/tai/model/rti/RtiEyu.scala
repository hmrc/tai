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

case class RtiEyu(
  taxablePayDelta: Option[BigDecimal],
  totalTaxDelta: Option[BigDecimal],
  empeeContribnsDelta: Option[BigDecimal],
  rcvdDate: LocalDate
) extends Ordered[RtiEyu] {

  def compare(that: RtiEyu): Int = this.rcvdDate compareTo that.rcvdDate

}

object RtiEyu {
  private val stringMapFormat: Format[Map[String, BigDecimal]] =
    JsonExtra.mapFormat[String, BigDecimal]("type", "amount")

  implicit val formatRtiEyu: Format[RtiEyu] = Format(
    new Reads[RtiEyu] {
      override def reads(json: JsValue): JsResult[RtiEyu] = {

        val optionalAdjustmentAmountMap =
          (json \ "optionalAdjustmentAmount")
            .asOpt[Map[String, BigDecimal]](stringMapFormat)
            .getOrElse(Map.empty[String, BigDecimal])

        val niFigure = (json \ "niLettersAndValues")
          .asOpt[JsArray]
          .map(x => x \\ "niFigure")
          .flatMap(_.headOption)
          .map(_.asOpt[Map[String, BigDecimal]](stringMapFormat).getOrElse(Map.empty[String, BigDecimal]))

        val rcvdDate = (json \ "rcvdDate").as[LocalDate](formatLocalDate)

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
                  "amount" -> element.fold(BigDecimal(0))(ele => ele._2)
                )
              }
            )
          ),
          "rcvdDate" -> eyu.rcvdDate
        )
      }
    }
  )

}
