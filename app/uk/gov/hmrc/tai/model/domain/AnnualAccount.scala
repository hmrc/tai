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

import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.tai.model.domain.EndOfTaxYearUpdate.endOfTaxYearUpdateHodReads
import uk.gov.hmrc.tai.model.domain.Payment.paymentHodReads
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.tai.TaxYear.taxYearHodReads
import uk.gov.hmrc.tai.util.SensitiveHelper.sensitiveFormatJsArray

case class AnnualAccount(
  sequenceNumber: Int,
  taxYear: TaxYear,
  realTimeStatus: RealTimeStatus,
  payments: Seq[Payment],
  endOfTaxYearUpdates: Seq[EndOfTaxYearUpdate]
) {

  lazy val totalIncomeYearToDate: BigDecimal =
    if (payments.isEmpty) 0 else payments.max.amountYearToDate
}

object AnnualAccount {
  implicit val format: Format[AnnualAccount] = Json.format[AnnualAccount]

  def formatWithEncryption(implicit crypto: Encrypter with Decrypter): Format[Seq[AnnualAccount]] =
    sensitiveFormatJsArray[Seq[AnnualAccount]](Reads.seq(format), Writes.seq(format))

  implicit val annualAccountOrdering: Ordering[AnnualAccount] = Ordering.by(_.taxYear.year)

  def apply(sequenceNumber: Int, taxYear: TaxYear, rtiStatus: RealTimeStatus): AnnualAccount =
    AnnualAccount(sequenceNumber, taxYear, rtiStatus, Nil, Nil)

  val annualAccountHodReads: Reads[Seq[AnnualAccount]] = (json: JsValue) => {

    val employments: Seq[JsValue] = (json \ "individual" \ "employments" \ "employment").validate[JsArray] match {
      case JsSuccess(arr, _) => arr.value.toSeq
      case _                 => Nil
    }

    JsSuccess(employments.map { emp =>
      val sequenceNumber = (emp \ "sequenceNumber").as[Int]
      val payments =
        (emp \ "payments" \ "inYear").validate[JsArray] match {
          case JsSuccess(arr, _) =>
            arr.value
              .map { payment =>
                payment.as[Payment](paymentHodReads)
              }
              .toList
              .sorted
          case _ => Nil
        }

      val eyus =
        (emp \ "payments" \ "eyu").validate[JsArray] match {
          case JsSuccess(arr, _) =>
            arr.value
              .map { payment =>
                payment.as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
              }
              .toList
              .sorted
          case _ => Nil
        }

      val taxYear = (json \ "individual" \ "relatedTaxYear").as[TaxYear](taxYearHodReads)

      AnnualAccount(sequenceNumber, taxYear, Available, payments, eyus)
    })
  }
}
