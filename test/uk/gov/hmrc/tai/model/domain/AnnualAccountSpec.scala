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

package uk.gov.hmrc.tai.model.domain

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.tai.TaxYear

class AnnualAccountSpec extends PlaySpec {

  "AnnualAccount" must {
    "create an instance given correct input" in {
      val key = "0-0-0"
      val realTimeStatus = Available
      val taxYear = TaxYear()

      AnnualAccount(key, taxYear, realTimeStatus) mustBe AnnualAccount(key, taxYear, realTimeStatus, Nil, Nil)
    }
  }

  "totalIncome" must {
    val SutWithNoPayments: AnnualAccount = AnnualAccount(
      "taxdistrict-payeref-payroll",
      taxYear = TaxYear("2017"),
      realTimeStatus = Available,
      payments = Nil,
      endOfTaxYearUpdates = Nil)

    val SutWithNoPayroll: AnnualAccount = AnnualAccount(
      "taxdistrict-payeref",
      taxYear = TaxYear("2017"),
      realTimeStatus = Available,
      payments = Nil,
      endOfTaxYearUpdates = Nil)

    val SutWithOnePayment: AnnualAccount = AnnualAccount(
      "",
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

    val SutWithMultiplePayments: AnnualAccount = SutWithOnePayment.copy(
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

    "Generate a unique employer designation, consisting of tax district and paye ref extracted from the id string" in {
      val desig = SutWithNoPayments.employerDesignation
      desig mustBe "taxdistrict-payeref"
    }

    "Generate a full key (unique employer designation plus optional employee payroll number)" when {
      "Employment has an employee payrollNumber present" in {

        val desig = SutWithNoPayments.key
        desig mustBe "taxdistrict-payeref-payroll"
      }
    }
    "Generate a full key consisting of only tax district and paye ref" when {

      "Employment has no employee payrollNumber" in {

        val desig = SutWithNoPayroll.key
        desig mustBe "taxdistrict-payeref"
      }
    }
  }
}
