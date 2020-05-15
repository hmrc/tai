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
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{Await, Future}

class EmploymentsSpec extends PlaySpec {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev

  def createAnnualAccount(
    rtiStatus: RealTimeStatus = Available,
    key: String = "0-0-0",
    taxYear: TaxYear = currentTaxYear): AnnualAccount =
    AnnualAccount(key, taxYear, rtiStatus, Nil, Nil)

  "Employments" should {
    "return a sequence of employments with only accounts for a given year" in {
      val annualAccountCTY = createAnnualAccount()
      val annualAccountPTY = createAnnualAccount(taxYear = previousTaxYear)

      val employment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        Seq(annualAccountCTY, annualAccountPTY),
        "1234",
        "",
        4,
        Some(100),
        false,
        false
      )

      val expectedEmployments = Seq(employment.copy(annualAccounts = Seq(annualAccountCTY)))

      val unifiedEmployment = Employments(Seq(employment))
      val accountsForYear = unifiedEmployment.accountsForYear(currentTaxYear)

      accountsForYear mustBe Employments(expectedEmployments)
    }

    "return an empty sequence of employments if no accounts exist for a tax year" in {
      val annualAccountPTY = createAnnualAccount(taxYear = previousTaxYear)

      val employment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        Seq(annualAccountPTY),
        "1234",
        "",
        4,
        Some(100),
        false,
        false
      )

      val unifiedEmployment = Employments(Seq(employment))
      val accountsForYear = unifiedEmployment.accountsForYear(currentTaxYear)

      accountsForYear mustBe Employments(Nil)
    }

    "return true if an employment contains a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccount =
        createAnnualAccount(rtiStatus = TemporarilyUnavailable, taxYear = previousTaxYear)
      val annualAccountCTY = createAnnualAccount()

      val employment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        Seq(stubbedAnnualAccount, annualAccountCTY),
        "1234",
        "",
        4,
        Some(100),
        false,
        false
      )

      val unifiedEmployment = Employments(Seq(employment))
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe true
    }

    "return false if an employment does not contain a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccountCTY = createAnnualAccount(rtiStatus = TemporarilyUnavailable)
      val annualAccountPTY = createAnnualAccount(taxYear = previousTaxYear)

      val employment = Employment(
        "TEST",
        Some("12345"),
        LocalDate.now(),
        None,
        Seq(stubbedAnnualAccountCTY, annualAccountPTY),
        "1234",
        "",
        4,
        Some(100),
        false,
        false
      )

      val unifiedEmployment = Employments(Seq(employment))
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe false
    }

    "merge employments" when {
      "the employments have the same key and contain different tax year annual account records" in {
        val now = LocalDate.now()

        val annualAccountCTY = createAnnualAccount(taxYear = currentTaxYear)
        val annualAccountPTY = createAnnualAccount(taxYear = previousTaxYear)

        val employment1 =
          Employment("EMPLOYER1", Some("12345"), now, None, Seq(annualAccountCTY), "0", "0", 2, None, false, false)

        val employment1WithPTYAccount =
          Employment("EMPLOYER1", Some("12345"), now, None, Seq(annualAccountPTY), "0", "0", 2, None, false, false)

        val annualAccount2CTY = createAnnualAccount(key = "01-01-01", taxYear = currentTaxYear)
        val employment2 =
          Employment("EMPLOYER2", Some("12345"), now, None, Seq(annualAccount2CTY), "01", "01", 2, None, false, false)

        val expectedMergedEmployment = employment1.copy(annualAccounts = Seq(annualAccountCTY, annualAccountPTY))

        val unifiedEmployment = Employments(Seq(employment1, employment2))
        val mergedEmployments = unifiedEmployment.mergeEmployments(Seq(employment1WithPTYAccount))

        mergedEmployments mustBe Seq(expectedMergedEmployment, employment2)
      }

      "the employments have different keys" in {
        val now = LocalDate.now()

        val annualAccountCTY = createAnnualAccount(taxYear = currentTaxYear)
        val employment1 =
          Employment("EMPLOYER1", Some("12345"), now, None, Seq(annualAccountCTY), "0", "0", 2, None, false, false)

        val annualAccount2CTY = createAnnualAccount(key = "01-01-01", taxYear = currentTaxYear)
        val employment2 =
          Employment("EMPLOYER2", Some("12345"), now, None, Seq(annualAccount2CTY), "01", "01", 2, None, false, false)

        val unifiedEmployment = Employments(Seq(employment1))
        val mergedEmployments = unifiedEmployment.mergeEmployments(Seq(employment2))

        mergedEmployments.size mustBe 2
        mergedEmployments must contain(employment1)
        mergedEmployments must contain(employment2)
      }

      //TODO name
      "gghghgh" in {

        val now = LocalDate.now()

        val annualAccountCTYTempUnavailable =
          createAnnualAccount(taxYear = currentTaxYear, rtiStatus = TemporarilyUnavailable)
        val annualAccountCTYAvailable = createAnnualAccount(taxYear = currentTaxYear, rtiStatus = Available)
        val annualAccountPTYTempUnavailable =
          createAnnualAccount(taxYear = previousTaxYear, rtiStatus = TemporarilyUnavailable)

        val employment1 =
          Employment(
            "EMPLOYER1",
            Some("12345"),
            now,
            None,
            Seq(annualAccountCTYTempUnavailable, annualAccountPTYTempUnavailable),
            "0",
            "0",
            2,
            None,
            false,
            false)

        val employment1WithUpdatedStatus =
          Employment(
            "EMPLOYER1",
            Some("12345"),
            now,
            None,
            Seq(annualAccountCTYAvailable),
            "0",
            "0",
            2,
            None,
            false,
            false)

//        val annualAccount2CTY = createAnnualAccount(key = "01-01-01", taxYear = currentTaxYear)
//        val employment2 =
//          Employment("EMPLOYER2", Some("12345"), now, None, Seq(annualAccount2CTY), "01", "01", 2, None, false, false)

        val expectedEmployments =
          employment1.copy(annualAccounts = Seq(annualAccountCTYAvailable, annualAccountPTYTempUnavailable))

        val employments = Employments(Seq(employment1))
        val mergedEmployments =
          employments.mergeEmploymentsForTaxYear(Seq(employment1WithUpdatedStatus), currentTaxYear)

        mergedEmployments mustBe Seq(expectedEmployments)
//        mergedEmployments.size mustBe 1
//        val mergedAccounts = mergedEmployments.map(_.annualAccounts)
//
//        mergedAccounts.size mustBe 2

      }

    }

  }
}
