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

import play.api.libs.json.{Format, JsArray, JsSuccess, JsValue, Json, Reads}
import uk.gov.hmrc.tai.model.domain.PaymentFrequency.paymentFrequencyFormatFromHod
import uk.gov.hmrc.tai.model.tai.JsonExtra

import java.time.LocalDate

case class Payment(
  date: LocalDate,
  amountYearToDate: BigDecimal,
  taxAmountYearToDate: BigDecimal,
  nationalInsuranceAmountYearToDate: BigDecimal,
  amount: BigDecimal,
  taxAmount: BigDecimal,
  nationalInsuranceAmount: BigDecimal,
  payFrequency: PaymentFrequency,
  duplicate: Option[Boolean]
) extends Ordered[Payment] {

  def compare(that: Payment): Int = this.date compareTo that.date
}

object Payment {
  implicit val paymentFrequencyFormat: Format[PaymentFrequency] = PaymentFrequency.paymentFrequencyFormat
  implicit val format: Format[Payment] = Json.format[Payment]

  private implicit val LocalDateOrder: Ordering[LocalDate] = (x: LocalDate, y: LocalDate) => x.compareTo(y)
  implicit val dateOrdering: Ordering[Payment] = Ordering.by(_.date)

  def niFigure(json: JsValue): Option[Map[String, BigDecimal]] = (json \ "niLettersAndValues")
    .asOpt[JsArray]
    .map(x => x \\ "niFigure")
    .flatMap(_.headOption)
    .map(_.asOpt[Map[String, BigDecimal]].getOrElse(Map()))

  val paymentHodReads: Reads[Payment] = (json: JsValue) => {

    val mandatoryMoneyAmount = (json \ "mandatoryMonetaryAmount").as[Map[String, BigDecimal]]

    val payment = Payment(
      date = (json \ "pmtDate").as[LocalDate],
      amountYearToDate = mandatoryMoneyAmount("TaxablePayYTD"),
      taxAmountYearToDate = mandatoryMoneyAmount("TotalTaxYTD"),
      nationalInsuranceAmountYearToDate = niFigure(json).flatMap(_.get("EmpeeContribnsYTD")).getOrElse(0),
      amount = mandatoryMoneyAmount("TaxablePay"),
      taxAmount = mandatoryMoneyAmount("TaxDeductedOrRefunded"),
      nationalInsuranceAmount = niFigure(json).flatMap(_.get("EmpeeContribnsInPd")).getOrElse(0),
      payFrequency = (json \ "payFreq").as[PaymentFrequency](paymentFrequencyFormatFromHod),
      duplicate = (json \ "duplicate").asOpt[Boolean]
    )

    JsSuccess(payment)
  }

  private implicit val stringMapFormat: Format[Map[String, BigDecimal]] =
    JsonExtra.mapFormat[String, BigDecimal]("type", "amount")
}
