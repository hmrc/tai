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

package uk.gov.hmrc.tai.model

import org.joda.time.LocalDate
import play.api.libs.json.{Format, Json}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

case class EditIncomeForm(
  name: String,
  description: String,
  employmentId: Int = 0,
  newAmount: Option[String] = None,
  oldAmount: Int = 0,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  isLive: Boolean = true,
  isOccupationalPension: Boolean = false,
  hasMultipleIncomes: Boolean = false,
  payToDate: Option[String] = None
)

object EditIncomeForm {
  implicit val formats: Format[EditIncomeForm] = Json.format[EditIncomeForm]
}

case class HowToUpdateForm(howToUpdate: Option[String])

object HowToUpdateForm {
  implicit val formats = Json.format[HowToUpdateForm]
}

case class HoursWorkedForm(workingHours: Option[String])

object HoursWorkedForm {
  implicit val formats = Json.format[HoursWorkedForm]
}

case class PayPeriodForm(payPeriod: Option[String], otherInDays: Option[Int] = None)

object PayPeriodForm {
  implicit val formats = Json.format[PayPeriodForm]
}

case class PayslipForm(totalSalary: Option[String] = None)

object PayslipForm {
  implicit val formats = Json.format[PayslipForm]
}

case class TaxablePayslipForm(taxablePay: Option[String] = None)

object TaxablePayslipForm {
  implicit val formats = Json.format[TaxablePayslipForm]
}

case class PayslipDeductionsForm(payslipDeductions: Option[String])

object PayslipDeductionsForm {
  implicit val formats = Json.format[PayslipDeductionsForm]
}

case class BonusPaymentsForm(bonusPayments: Option[String], bonusPaymentsMoreThisYear: Option[String])

object BonusPaymentsForm {
  implicit val formats = Json.format[BonusPaymentsForm]
}

case class BonusOvertimeAmountForm(amount: Option[String] = None)

object BonusOvertimeAmountForm {
  implicit val formats = Json.format[BonusOvertimeAmountForm]
}

case class IncomeCalculation(
  incomeId: Option[Int] = None,
  howToUpdateForm: Option[HowToUpdateForm] = None,
  hoursWorkedForm: Option[HoursWorkedForm] = None,
  payPeriodForm: Option[PayPeriodForm] = None,
  payslipForm: Option[PayslipForm] = None,
  taxablePayslipForm: Option[TaxablePayslipForm] = None,
  payslipDeductionsForm: Option[PayslipDeductionsForm] = None,
  bonusPaymentsForm: Option[BonusPaymentsForm] = None,
  bonusOvertimeAmountForm: Option[BonusOvertimeAmountForm] = None,
  netAmount: Option[BigDecimal] = None,
  grossAmount: Option[BigDecimal] = None
)

object IncomeCalculation {
  implicit val formats: Format[IncomeCalculation] = Json.format[IncomeCalculation]
}
