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

package uk.gov.hmrc.tai.model.domain

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear

class EmploymentSpec extends PlaySpec {

  "Employment" should {

    "Generate a unique employer designation, consisting of tax district and paye ref" in {
      val desig = singleEmploymentWithAllRefs.head.employerDesignation
      desig mustBe "754-AZ00070"
    }

    "Generate a full key (unique employer designation plus optional employee payroll number)" when {
      "Employment has an employee payrollNumber present" in {

        val desig = singleEmploymentWithAllRefs.head.key
        desig mustBe "754-AZ00070-64765"
      }
    }
    "Generate a full key consisting of only tax district and paye ref" when {

      "Employment has no employee payrollNumber" in {

        val desig = singleEmploymentWithMissingPayrollNumber.head.key
        desig mustBe "754-AZ00070"
      }

      "Employment has empty string as employee payroll number" in {

        val desig = singleEmploymentWithEmptyStringPayrollNumber.head.key
        desig mustBe "754-AZ00070"
      }
    }

    "return true if any annual account exists, with a status of temporarily unavailable, for the requested year" in {
      val accounts =
        Seq(createAnnualAccount(TemporarilyUnavailable), createAnnualAccount(Available, taxYear))

      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.tempUnavailableStubExistsForYear(taxYear) mustBe true
    }

    "return true if no annual account exists, with a status of temporarily unavailable, for the requested year" in {
      val accounts =
        Seq(createAnnualAccount(TemporarilyUnavailable, TaxYear(2019)), createAnnualAccount(Available))

      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.tempUnavailableStubExistsForYear(taxYear) mustBe false
    }

    "return true if an annual accounts exist for a particular year" in {
      val accounts = Seq(createAnnualAccount(Available, TaxYear(2019)), createAnnualAccount(Available))
      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.hasAnnualAccountsForYear(taxYear) mustBe true
    }

    "return false if annual accounts don't exist for a particular year" in {
      val accounts = Seq(createAnnualAccount(Available, TaxYear(2019)), createAnnualAccount(Unavailable, TaxYear(2018)))
      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.hasAnnualAccountsForYear(taxYear) mustBe false
    }

    "return all annual accounts for the request year" in {
      val expectedAccount1 = createAnnualAccount(Available)
      val expectedAccount2 = createAnnualAccount(Unavailable)
      val expectedAccount3 = createAnnualAccount(TemporarilyUnavailable)

      val accounts =
        Seq(
          createAnnualAccount(TemporarilyUnavailable, TaxYear(2019)),
          expectedAccount1,
          expectedAccount2,
          expectedAccount3,
          createAnnualAccount(Available, TaxYear(2018))
        )

      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.annualAccountsForYear(taxYear) mustBe Seq(
        expectedAccount1,
        expectedAccount2,
        expectedAccount3)
    }

  }

  val taxYear = TaxYear()

  def createAnnualAccount(rtiStatus: RealTimeStatus, taxYear: TaxYear = taxYear): AnnualAccount =
    AnnualAccount("0-0-0", taxYear, rtiStatus, Nil, Nil)

  val singleEmploymentWithAllRefs = List(
    Employment(
      "XXX PPPP",
      Live,
      Some("64765"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false))
  val singleEmploymentWithMissingPayrollNumber = List(
    Employment(
      "XXX PPPP",
      Live,
      None,
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false))
  val singleEmploymentWithEmptyStringPayrollNumber = List(
    Employment(
      "XXX PPPP",
      Live,
      Some(""),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false))
  val dualEmployment = List(
    Employment(
      "XXX PPPP",
      Live,
      Some("64765"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false),
    Employment(
      "XXX PPPP",
      Live,
      Some("64766"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false)
  )

}
