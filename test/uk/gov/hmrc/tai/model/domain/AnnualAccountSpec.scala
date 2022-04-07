/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.tai.TaxYear

class AnnualAccountSpec extends PlaySpec {

  "AnnualAccount" must {
    "create an instance given correct input" in {
      val sequenceNumber = 0
      val realTimeStatus = Available
      val taxYear = TaxYear()

      AnnualAccount(sequenceNumber, taxYear, realTimeStatus) mustBe AnnualAccount(sequenceNumber, taxYear, realTimeStatus, Nil, Nil)
    }
  }

  "totalIncome" must {
    "return the latest year to date value from the payments" when {
      "there is only one payment" in {
        SutWithOnePayment.totalIncomeYearToDate mustBe 2000
      }
      "there are multiple payments" in {
        SutWithMultiplePayments.totalIncomeYearToDate mustBe 2000
      }
    }
    "return zero for the latest year to date value" when {
      "there are no payments" in {
        SutWithNoPayments.totalIncomeYearToDate mustBe 0
      }
    }

    "Check for sequenceNumber" when {
      "Employment has an employee payrollNumber present" in {

        val desig = SutWithNoPayments.sequenceNumber
        desig mustBe 0
      }
      "Employment has no employee payrollNumber" in {

        val desig = SutWithNoPayroll.sequenceNumber
        desig mustBe 1
      }
    }
  }

  val SutWithNoPayments = AnnualAccount(
    0,
    taxYear = TaxYear("2017"),
    realTimeStatus = Available,
    payments = Nil,
    endOfTaxYearUpdates = Nil)

  val SutWithNoPayroll = AnnualAccount(
    1,
    taxYear = TaxYear("2017"),
    realTimeStatus = Available,
    payments = Nil,
    endOfTaxYearUpdates = Nil)

  val SutWithOnePayment = AnnualAccount(
    2,
    taxYear = TaxYear("2017"),
    realTimeStatus = Available,
    payments = List(
      Payment(
        date = new LocalDate(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = Monthly,
        duplicate = None
      )),
    endOfTaxYearUpdates = Nil
  )

  val SutWithMultiplePayments = SutWithOnePayment.copy(
    payments = SutWithOnePayment.payments :+
      Payment(
        date = new LocalDate(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = Weekly,
        duplicate = None
      ) :+
      Payment(
        date = new LocalDate(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = FortNightly,
        duplicate = None
      ))
}
