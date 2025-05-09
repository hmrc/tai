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
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate

class EmploymentsSpec extends PlaySpec {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear: TaxYear = currentTaxYear.prev
  val now: LocalDate = LocalDate.now()

  val annualAccountCTY: AnnualAccount = createAnnualAccount()
  val annualAccountPTY: AnnualAccount = createAnnualAccount(taxYear = previousTaxYear)

  val employment1: Employment = Employment(
    "TEST",
    Live,
    Some("12345"),
    LocalDate.now(),
    None,
    Seq.empty[AnnualAccount],
    "1234",
    "0",
    1,
    Some(100),
    false,
    false,
    PensionIncome
  )

  def createAnnualAccount(
    rtiStatus: RealTimeStatus = Available,
    sequenceNumber: Int = 1,
    taxYear: TaxYear = currentTaxYear
  ): AnnualAccount =
    AnnualAccount(sequenceNumber, taxYear, rtiStatus, Nil, Nil)

  "Employments" must {
    "return a sequence of employments with only accounts for a given year" in {

      val employment = employment1.copy(annualAccounts = Seq(annualAccountCTY, annualAccountPTY))
      val expectedEmployments = Seq(employment.copy(annualAccounts = Seq(annualAccountCTY)))

      val unifiedEmployment = Employments(Seq(employment), None)
      val accountsForYear = unifiedEmployment.accountsForYear(currentTaxYear)

      accountsForYear mustBe Employments(expectedEmployments, None)
    }

    "return an empty sequence of employments if no accounts exist for a tax year" in {

      val employment = employment1.copy(annualAccounts = Seq(annualAccountPTY))

      val unifiedEmployment = Employments(Seq(employment), None)
      val accountsForYear = unifiedEmployment.accountsForYear(currentTaxYear)

      accountsForYear mustBe Employments(Nil, None)
    }

    "return true if an employment contains a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccount =
        createAnnualAccount(rtiStatus = TemporarilyUnavailable, taxYear = previousTaxYear)
      val annualAccountCTY = createAnnualAccount()

      val employment = employment1.copy(annualAccounts = Seq(stubbedAnnualAccount, annualAccountCTY))

      val unifiedEmployment = Employments(Seq(employment), None)
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe true
    }

    "return false if an employment does not contain a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccountCTY = createAnnualAccount(rtiStatus = TemporarilyUnavailable)
      val employment = employment1.copy(annualAccounts = Seq(stubbedAnnualAccountCTY, annualAccountPTY))

      val unifiedEmployment = Employments(Seq(employment), None)
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe false
    }

    "return a list of sequence numbers" in {
      val employment2 = employment1.copy(sequenceNumber = 2)
      val employment3 = employment1.copy(sequenceNumber = 3)

      val employments = Employments(Seq(employment1, employment2, employment3), None)
      employments.sequenceNumbers mustBe Seq(1, 2, 3)
    }

    "return an empty sequence list if no employments are present" in {
      val employments = Employments(Seq.empty[Employment], None)
      employments.sequenceNumbers mustBe Seq.empty[Int]
    }

    "return an employment by id when it exists within the collection" in {
      val employment2 = employment1.copy(sequenceNumber = 2)
      val employment3 = employment1.copy(sequenceNumber = 3)

      val employments = Employments(Seq(employment1, employment2, employment3), None)
      employments.employmentById(2) mustBe Some(employment2)
    }

    "return None if an employment does not exists in the collection" in {
      val employment2 = employment1.copy(sequenceNumber = 2)
      val employment3 = employment1.copy(sequenceNumber = 3)

      val notFoundId = 4
      val employments = Employments(Seq(employment1, employment2, employment3), None)
      employments.employmentById(notFoundId) mustBe None
    }

    "merge employments" when {
      "the employments have the different sequenceNumber and contain different tax year annual account records" in {

        val employment1 =
          Employment(
            "EMPLOYER1",
            Live,
            Some("12345"),
            now,
            None,
            Seq(annualAccountCTY),
            "0",
            "0",
            1,
            None,
            false,
            false,
            PensionIncome
          )

        val employment1WithPTYAccount =
          Employment(
            "EMPLOYER1",
            Live,
            Some("12345"),
            now,
            None,
            Seq(annualAccountPTY),
            "0",
            "0",
            1,
            None,
            false,
            false,
            PensionIncome
          )

        val annualAccount2CTY = createAnnualAccount(sequenceNumber = 2, taxYear = currentTaxYear)
        val employment2 =
          Employment(
            "EMPLOYER2",
            Live,
            Some("12345"),
            now,
            None,
            Seq(annualAccount2CTY),
            "01",
            "01",
            2,
            None,
            false,
            false,
            PensionIncome
          )

        val expectedMergedEmployment = employment1.copy(annualAccounts = Seq(annualAccountCTY, annualAccountPTY))

        val unifiedEmployment = Employments(Seq(employment1, employment2), None)
        val mergedEmployments = unifiedEmployment.mergeEmployments(Seq(employment1WithPTYAccount))

        mergedEmployments mustBe Seq(expectedMergedEmployment, employment2)
      }

      "the employments have different sequence number" in {
        val now = LocalDate.now()

        val annualAccountCTY = createAnnualAccount(taxYear = currentTaxYear)
        val employment1 =
          Employment(
            "EMPLOYER1",
            Live,
            Some("12345"),
            now,
            None,
            Seq(annualAccountCTY),
            "0",
            "0",
            1,
            None,
            false,
            false,
            PensionIncome
          )

        val annualAccount2CTY = createAnnualAccount(sequenceNumber = 2, taxYear = currentTaxYear)
        val employment2 =
          Employment(
            "EMPLOYER2",
            Live,
            Some("12345"),
            now,
            None,
            Seq(annualAccount2CTY),
            "01",
            "01",
            2,
            None,
            false,
            false,
            PensionIncome
          )

        val unifiedEmployment = Employments(Seq(employment1), None)
        val mergedEmployments = unifiedEmployment.mergeEmployments(Seq(employment2))

        mergedEmployments.size mustBe 2
        mergedEmployments must contain(employment1)
        mergedEmployments must contain(employment2)
      }
    }

    "merge the employments for a given tax year only" in {

      val annualAccountCTYTempUnavailable =
        createAnnualAccount(taxYear = currentTaxYear, rtiStatus = TemporarilyUnavailable)
      val annualAccountCTYAvailable = createAnnualAccount(taxYear = currentTaxYear, rtiStatus = Available)
      val annualAccountPTYTempUnavailable =
        createAnnualAccount(taxYear = previousTaxYear, rtiStatus = TemporarilyUnavailable)

      val employment1 =
        Employment(
          "EMPLOYER1",
          Live,
          Some("12345"),
          now,
          None,
          Seq(annualAccountCTYTempUnavailable, annualAccountPTYTempUnavailable),
          "0",
          "0",
          1,
          None,
          false,
          false,
          PensionIncome
        )

      val employment1WithUpdatedStatus =
        Employment(
          "EMPLOYER1",
          Live,
          Some("12345"),
          now,
          None,
          Seq(annualAccountCTYAvailable),
          "0",
          "0",
          1,
          None,
          false,
          false,
          PensionIncome
        )

      val annualAccount2CTY = createAnnualAccount(sequenceNumber = 2, taxYear = currentTaxYear)
      val employment2 =
        Employment(
          "EMPLOYER2",
          Live,
          Some("12345"),
          now,
          None,
          Seq(annualAccount2CTY),
          "01",
          "01",
          2,
          None,
          false,
          false,
          PensionIncome
        )

      val expectedEmployments =
        employment1.copy(annualAccounts = Seq(annualAccountCTYAvailable, annualAccountPTYTempUnavailable))

      val employments = Employments(Seq(employment1, employment2), None)
      val mergedEmployments =
        employments.mergeEmploymentsForTaxYear(Seq(employment1WithUpdatedStatus), currentTaxYear)

      mergedEmployments mustBe Seq(expectedEmployments, employment2)

    }
  }
}
