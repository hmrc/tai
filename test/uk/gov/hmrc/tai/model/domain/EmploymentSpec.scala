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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate

class EmploymentSpec extends PlaySpec {
  private val taxYear = TaxYear()

  private def createAnnualAccount(rtiStatus: RealTimeStatus, taxYear: TaxYear = taxYear): AnnualAccount =
    AnnualAccount(0, taxYear, rtiStatus, Nil, Nil)

  private val singleEmploymentWithAllRefs = List(
    Employment(
      "XXX PPPP",
      Live,
      Some("64765"),
      LocalDate.of(2016, 4, 6),
      None,
      Nil,
      "754",
      "AZ00070",
      2,
      Some(100),
      false,
      false,
      PensionIncome
    )
  )

  "Format" must {
    "un-marshall and marshal valid Employment json and object" when {
      "given a valid Employment object and json" in {
        val employmentDetails = List(
          Employment(
            "TEST",
            Live,
            Some("12345"),
            LocalDate.parse("2017-05-26"),
            None,
            List(
              AnnualAccount(
                0,
                TaxYear(2017),
                Available,
                List(Payment(LocalDate.parse("2017-05-26"), 10, 10, 10, 10, 10, 10, Monthly, Some(true))),
                List(
                  EndOfTaxYearUpdate(LocalDate.parse("2017-05-26"), List(Adjustment(NationalInsuranceAdjustment, 10)))
                )
              )
            ),
            "",
            "",
            2,
            Some(100),
            false,
            false,
            PensionIncome
          )
        )

        val json = Json.arr(
          Json.obj(
            "name"             -> "TEST",
            "employmentStatus" -> Live.toString,
            "payrollNumber"    -> "12345",
            "startDate"        -> "2017-05-26",
            "annualAccounts" -> Json.arr(
              Json.obj(
                "sequenceNumber" -> 0,
                "taxYear"        -> 2017,
                "realTimeStatus" -> "Available",
                "payments" -> Json.arr(
                  Json.obj(
                    "date"                              -> "2017-05-26",
                    "amountYearToDate"                  -> 10,
                    "taxAmountYearToDate"               -> 10,
                    "nationalInsuranceAmountYearToDate" -> 10,
                    "amount"                            -> 10,
                    "taxAmount"                         -> 10,
                    "nationalInsuranceAmount"           -> 10,
                    "payFrequency"                      -> "Monthly",
                    "duplicate"                         -> true
                  )
                ),
                "endOfTaxYearUpdates" -> Json.arr(
                  Json.obj(
                    "date" -> "2017-05-26",
                    "adjustments" -> Json.arr(
                      Json.obj(
                        "type"   -> "NationalInsuranceAdjustment",
                        "amount" -> 10
                      )
                    )
                  )
                )
              )
            ),
            "taxDistrictNumber"            -> "",
            "payeNumber"                   -> "",
            "sequenceNumber"               -> 2,
            "cessationPay"                 -> 100,
            "hasPayrolledBenefit"          -> false,
            "receivingOccupationalPension" -> false,
            "employmentType"               -> "PensionIncome"
          )
        )

        Json.toJson(employmentDetails) mustBe json

      }
    }
  }

  "tempUnavailableStubExistsForYear" must {
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
  }

  "hasAnnualAccountsForYear" must {
    "return true if an annual accounts exist for a particular year" in {
      val accounts = Seq(createAnnualAccount(Available, TaxYear(2019)), createAnnualAccount(Available))
      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.hasAnnualAccountsForYear(taxYear) mustBe true
    }

    "return false if annual accounts don't exist for a particular year" in {
      val accounts =
        Seq(createAnnualAccount(Available, TaxYear(2019)), createAnnualAccount(TemporarilyUnavailable, TaxYear(2018)))
      val employmentWithAccount = singleEmploymentWithAllRefs.head.copy(annualAccounts = accounts)

      employmentWithAccount.hasAnnualAccountsForYear(taxYear) mustBe false
    }
  }

  "annualAccountsForYear" must {
    "return all annual accounts for the request year" in {
      val expectedAccount1 = createAnnualAccount(Available)
      val expectedAccount2 = createAnnualAccount(TemporarilyUnavailable)
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

      employmentWithAccount
        .annualAccountsForYear(taxYear) mustBe Seq(expectedAccount1, expectedAccount2, expectedAccount3)
    }

  }

}
