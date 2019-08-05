/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.helpers

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.{DecreasesTax, IabdSummary, TaxCodeDetails, TaxComponent}

class TaxModelFactoryDecreasesTaxSpec extends PlaySpec with TaxModelFactoryTestData {

  "groupItemsThatDecreaseTax" should {
    "not return a decreasesTax object" when {
      "no input data is provided" in {
        createSUT.groupItemsThatDecreaseTax(None, None, None, None, None) mustBe None
      }
    }
    "return a decreasesTax object" when {
      "given fully populated input data" in {
        val sut = createSUT

        val decreasesTax = sut.groupItemsThatDecreaseTax(
          totalLiability = Some(totalLiab),
          incomeSources = Some(List(incomeSource)),
          personalAllowance = Some(genericNpsComponent),
          taxCodeDetails = Some(taxCodeDetails),
          personalAllowanceTapered = Some(true)
        )

        decreasesTax.get mustBe DecreasesTax(
          personalAllowance = Some(1000),
          personalAllowanceSourceAmount = Some(200),
          blindPerson = expectedBlindPerson,
          expenses = expectedExpenses,
          giftRelated = expectedGiftAid,
          jobExpenses = expectedJobExpenses,
          miscellaneous = expectedMiscellaneous,
          pensionContributions = expectedPersonalPension,
          paTransferredAmount = Some(-50),
          paReceivedAmount = Some(200),
          paTapered = true,
          personalSavingsAllowance = expectedPersonalSavings,
          total = 6250
        )
      }
    }
    "default the tax details allowances and deductions to zero" when {
      "the values for tax detail allowances and deductions arent given" in {
        val sut = createSUT

        val noAllowancesOrDeductions = taxCodeDetails
          .copy(allowances = Some(List(noTypeTaxCodeComponent)), deductions = Some(List(noTypeTaxCodeComponent)))
        val decreasesTax = sut.groupItemsThatDecreaseTax(
          totalLiability = Some(totalLiab),
          incomeSources = Some(List(incomeSource)),
          personalAllowance = Some(genericNpsComponent),
          taxCodeDetails = Some(noAllowancesOrDeductions),
          personalAllowanceTapered = Some(true)
        )

        decreasesTax.get mustBe DecreasesTax(
          personalAllowance = Some(1000),
          personalAllowanceSourceAmount = Some(200),
          blindPerson = expectedBlindPerson,
          expenses = expectedExpenses,
          giftRelated = expectedGiftAid,
          jobExpenses = expectedJobExpenses,
          miscellaneous = expectedMiscellaneous,
          pensionContributions = expectedPersonalPension,
          paTransferredAmount = None,
          paReceivedAmount = None,
          paTapered = true,
          personalSavingsAllowance = expectedPersonalSavings,
          total = 6100
        )
      }
    }
  }

  def createSUT = new SUT

  class SUT extends TaxModelFactory

  private val expectedBlindPerson = Some(
    TaxComponent(
      1000,
      0,
      "",
      List(
        IabdSummary(
          14,
          "blind person npsIabdSummary description",
          1000,
          Some(123),
          Some(3),
          Some("income source name")))))
  private val expectedExpenses = Some(
    TaxComponent(
      800,
      0,
      "",
      List(
        IabdSummary(56, "flat rate npsIabdSummary description", 800, Some(123), Some(2), Some("income source name")))))
  private val expectedGiftAid = Some(
    TaxComponent(
      300,
      0,
      "",
      List(
        IabdSummary(95, "gift aid npsIabdSummary description", 300, Some(123), Some(2), Some("income source name")))))
  private val expectedJobExpenses = Some(TaxComponent(
    400,
    0,
    "",
    List(
      IabdSummary(55, "job expences npsIabdSummary description", 400, Some(123), Some(2), Some("income source name")))))
  private val expectedMiscellaneous = Some(TaxComponent(
    700,
    0,
    "",
    List(
      IabdSummary(6, "misc payments npsIabdSummary description", 700, Some(123), Some(2), Some("income source name")))))
  private val expectedPersonalPension = Some(
    TaxComponent(
      900,
      0,
      "",
      List(
        IabdSummary(
          5,
          "personal pension payments npsIabdSummary description",
          900,
          Some(123),
          Some(2),
          Some("income source name")))))

  private val expectedPersonalSavings = Some(
    TaxComponent(1000, 33, "nps component description", List(IabdSummary(128, "", 1000, None, None, None))))

}
