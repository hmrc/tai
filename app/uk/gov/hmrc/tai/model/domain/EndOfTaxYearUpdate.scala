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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json.{Format, JsSuccess, JsValue, Json, Reads}
import uk.gov.hmrc.tai.model.domain.Payment.niFigure
import uk.gov.hmrc.tai.model.tai.JsonExtra

import java.time.LocalDate

case class EndOfTaxYearUpdate(date: LocalDate, adjustments: Seq[Adjustment]) extends Ordered[EndOfTaxYearUpdate] {

  def compare(that: EndOfTaxYearUpdate): Int = this.date compareTo that.date
}

object EndOfTaxYearUpdate {
  implicit val format: Format[EndOfTaxYearUpdate] = Json.format[EndOfTaxYearUpdate]

  private[domain] val endOfTaxYearUpdateHodReads: Reads[EndOfTaxYearUpdate] = (json: JsValue) => {

    val optionalAdjustmentAmountMap =
      (json \ "optionalAdjustmentAmount").asOpt[Map[String, BigDecimal]].getOrElse(Map())

    val rcvdDate = (json \ "rcvdDate").as[LocalDate]

    val adjusts = Seq(
      optionalAdjustmentAmountMap.get("TotalTaxDelta").map(Adjustment(TaxAdjustment, _)),
      optionalAdjustmentAmountMap.get("TaxablePayDelta").map(Adjustment(IncomeAdjustment, _)),
      niFigure(json).flatMap(_.get("EmpeeContribnsDelta")).map(Adjustment(NationalInsuranceAdjustment, _))
    ).flatten.filter(_.amount != 0)

    JsSuccess(EndOfTaxYearUpdate(rcvdDate, adjusts))
  }

  private implicit val stringMapFormat: Format[Map[String, BigDecimal]] =
    JsonExtra.mapFormat[String, BigDecimal]("type", "amount")
}
