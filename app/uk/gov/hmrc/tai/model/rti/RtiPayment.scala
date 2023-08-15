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
