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

class UnifiedEmploymentsSpec extends PlaySpec {

  val currentTaxYear: TaxYear = TaxYear()
  val previousTaxYear = currentTaxYear.prev

  def createStubbedAnnualAccount(
    rtiStatus: RealTimeStatus = Available,
    key: String = "0-0-0",
    taxYear: TaxYear = currentTaxYear): AnnualAccount =
    AnnualAccount(key, taxYear, rtiStatus, Nil, Nil)

  "UnifiedEmployments" should {
    "return a sequence of employments with only accounts for a given year" in {
      val annualAccountCTY = createStubbedAnnualAccount()
      val annualAccountPTY = createStubbedAnnualAccount(taxYear = previousTaxYear)

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

      val unifiedEmployment = UnifiedEmployments(Seq(employment))
      val accountsForYear = unifiedEmployment.withAccountsForYear(currentTaxYear)

      accountsForYear mustBe expectedEmployments
    }

    "return an empty sequence of employments if no accounts exist for a tax year" in {
      val annualAccountPTY = createStubbedAnnualAccount(taxYear = previousTaxYear)

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

      val unifiedEmployment = UnifiedEmployments(Seq(employment))
      val accountsForYear = unifiedEmployment.withAccountsForYear(currentTaxYear)

      accountsForYear mustBe List.empty[Employment]
    }

    "return true if an employment contains a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccount =
        createStubbedAnnualAccount(rtiStatus = TemporarilyUnavailable, taxYear = previousTaxYear)
      val annualAccountCTY = createStubbedAnnualAccount()

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

      val unifiedEmployment = UnifiedEmployments(Seq(employment))
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe true
    }

    "return false if an employment does not contain a TemporarilyUnavailable stubbed annual account for a given year" in {
      val stubbedAnnualAccountCTY = createStubbedAnnualAccount(rtiStatus = TemporarilyUnavailable)
      val annualAccountPTY = createStubbedAnnualAccount(taxYear = previousTaxYear)

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

      val unifiedEmployment = UnifiedEmployments(Seq(employment))
      val containsTempAccount = unifiedEmployment.containsTempAccount(previousTaxYear)

      containsTempAccount mustBe false
    }

    "merge employments" when {
      "the employments have the same key" in {

//        val now = LocalDate.now()
//
//        val annualAccount1 = AnnualAccount("0-0-0", TaxYear(2018), Available, Nil, Nil)
//        val currentEmployment = Employment(
//          "EMPLOYER1",
//          Some("0"),
//          new LocalDate(2016, 4, 6),
//          None,
//          Seq(annualAccount1),
//          "0",
//          "0",
//          2,
//          None,
//          false,
//          false)
//
//        val annualAccount2 = AnnualAccount("00", TaxYear(2018), Available, Nil, Nil)
//        val employment2 =
//          Employment("Employer2", Some("00"), now, None, Seq(annualAccount2), "", "", 2, Some(100), false, false)
//
//        val unifiedEmployment = UnifiedEmployments(Seq(currentEmployment))
//        val mergedEmployments = unifiedEmployment.mergeEmployments(Seq(employment2))
//
//        mergedEmployments mustBe ""
//        val expectedAnnualAccount = AnnualAccount(
//          "0-0-0",
//          TaxYear(2017),
//          Available,
//          List(Payment(new LocalDate(2016, 4, 30), 5000.0, 1500.0, 600.0, 5000.0, 1500.0, 600.0, BiAnnually, None)),
//          List(
//            EndOfTaxYearUpdate(
//              new LocalDate(2016, 6, 17),
//              List(Adjustment(TaxAdjustment, -27.99), Adjustment(NationalInsuranceAdjustment, 12.3))))
//        )
//
//        val expectedEmployment = npsSingleEmployment.copy(annualAccounts = Seq(expectedAnnualAccount))
//        val cacheUpdatedEmployment1 =
//          npsSingleEmployment.copy(annualAccounts = Seq(annualAccount1, expectedAnnualAccount))
//
//        val updatedCacheContents = Seq(employment2, cacheUpdatedEmployment1)
//        val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])
//
//
//
//        val result = Await.result(sut.employmentsForYear(nino, TaxYear(2017)), 5.seconds)
//        result mustBe Seq(expectedEmployment)
//
//
//
//        val actualCached = employmentsCaptor.getValue
//        actualCached.size mustBe 2
//        actualCached must contain(employment2)
//
//        val cachedEmp1 = actualCached.filter(_.name == "EMPLOYER1")
//
//        cachedEmp1.flatMap(_.annualAccounts) mustBe Seq(expectedAnnualAccount, annualAccount1)

      }
    }

  }
}
