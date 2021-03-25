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

package uk.gov.hmrc.tai.calculators

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsTax}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{Tax, TaxBand}
import uk.gov.hmrc.tai.util.TaiConstants

class TaxCalculatorSpec extends PlaySpec {

  "updateTax in Tax calculator" should {
    "update totalIncome to expected value" when {
      "updateTax is called with totalIncome as None" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(), 0, 0)
        expectedTax.totalIncome must be(None)
      }

      "updateTax is called with some totalIncome value" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(totalIncome = Some(4000)), 0, 0)
        expectedTax.totalIncome must be(Some(BigDecimal(4000)))
      }

      "updateTax is called with negative updateDifference" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(totalIncome = Some(2000)), -5000, 0)
        expectedTax.totalIncome must be(Some(BigDecimal(0)))
      }
    }

    "update allowedReliefDeducts with expected value" when {
      "updateTax is called with allowedReliefDeducts as None" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(), 0, 0)
        expectedTax.allowReliefDeducts must be(None)
      }

      "updateTax is called with some allowedReliefDeducts value" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(allowReliefDeducts = Some(1000)), 0, 2000)
        expectedTax.allowReliefDeducts must be(Some(BigDecimal(3000)))
      }
    }

    "update totalTaxableIncome with expected value" when {
      "updateTax is called with some totalIncome and allowedReliefDeducts values" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(totalIncome = Some(4000), allowReliefDeducts = Some(1000)), 0, -2000)
        expectedTax.totalTaxableIncome must be(Some(BigDecimal(5000)))
      }

      "updateTax is called with totalIncome less than allowedReliefDeducts" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(totalIncome = Some(4000), allowReliefDeducts = Some(5000)), 0, 0)
        expectedTax.totalTaxableIncome must be(Some(BigDecimal(0)))
      }
    }

    "update taxBands with expected value" when {
      val taxBandList = Some(List(taxBand(), taxBand(income = Some(20), tax = Some(2000))))

      "updateTax is called with totalIncome as None" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(taxBands = taxBandList), 0, 0)
        expectedTax.taxBands must be(Some(List(taxBand(Some(0), Some(0)), taxBand(Some(0), Some(0)))))
      }

      "updateTax is called with some totalIncome value" in {
        val sut = SUT
        val expectedTax: Tax = sut.updateTax(Tax(totalIncome = Some(4000), taxBands = taxBandList), 0, 0)
        expectedTax.taxBands must be(Some(List(taxBand(Some(4000), Some(0)), taxBand(Some(0), Some(0)))))
      }
    }

    "update total tax with expected values" when {
      "updateTax is called with No tax bands" in {
        val sut = SUT
        val expectedTax = sut.updateTax(Tax(), 0, 0)
        expectedTax.totalTax must be(None)
      }

      "updateTax is called with taxBands having rates" in {
        val taxBandList = List(taxBand(rate = Some(20)), taxBand(rate = Some(40)))
        val sut = SUT
        val expectedTax = sut.updateTax(Tax(totalIncome = Some(1000), taxBands = Some(taxBandList)), 0, 0)
        expectedTax.totalTax must be(Some(BigDecimal(200)))
      }
    }
  }

  "AdjustmentsTaxFreeAmount" should {
    "adjust the allowance and deductions" when {
      "both allowance and deduction are none" in {
        val sut = SUT
        sut.adjustmentsTaxFreeAmount() must be(0)
      }

      "deduction is none" in {
        val sut = SUT
        sut.adjustmentsTaxFreeAmount(allowances = allowances) must be(90)
      }

      "allowance is none" in {
        val sut = SUT
        sut.adjustmentsTaxFreeAmount(deductions = deductions) must be(-50)
      }

      "both allowance and deduction have values" in {
        val sut = SUT
        sut.adjustmentsTaxFreeAmount(allowances, deductions) must be(40)
      }
    }
  }

  "isAdjustmentNeeded" should {
    "check the conditions for tax adjustment" when {

      val oldTax = NpsTax(totalTax = Some(20), allowReliefDeducts = Some(NpsComponent(amount = Some(40))))

      "all the parameters are none" in {
        val sut = SUT
        sut.isAdjustmentNeeded(oldTax = NpsTax()) must be(false)
      }

      "taxCode value starts with B" in {
        val sut = SUT
        sut.isAdjustmentNeeded(taxCode = Some("BT"), oldTax = NpsTax()) must be(false)
      }

      "taxCode value starts with D" in {
        val sut = SUT
        sut.isAdjustmentNeeded(taxCode = Some("D0"), oldTax = NpsTax()) must be(false)
      }

      "taxCode value neither starts with B or D" in {
        val sut = SUT
        sut.isAdjustmentNeeded(
          taxCode = Some("K001"),
          allowances = allowances,
          deductions = deductions,
          oldTax = oldTax) must be(false)
      }

      "NpsTax has total tax amount" in {
        val sut = SUT
        val oldTax = NpsTax(totalTax = Some(100))
        sut.isAdjustmentNeeded(oldTax = oldTax) must be(false)
      }

      "NpsTax has total tax amount is 0" in {
        val sut = SUT
        val oldTax = NpsTax(totalTax = Some(0))
        sut.isAdjustmentNeeded(oldTax = oldTax) must be(false)
      }

      "NpsTax has total tax amount is None" in {
        val sut = SUT
        val oldTax = NpsTax(totalTax = None)
        sut.isAdjustmentNeeded(oldTax = oldTax) must be(false)
      }

      "NPS has allowReliefDeductTotal equivalent to adjustment tax" in {
        val sut = SUT
        sut.isAdjustmentNeeded(allowances = allowances, deductions = deductions, oldTax = oldTax) must be(false)
      }

      "NPS has allowReliefDeductTotal not equal to adjustment tax" in {
        val sut = SUT
        val oldTax = NpsTax(totalTax = Some(20), allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        sut.isAdjustmentNeeded(allowances = allowances, deductions = deductions, oldTax = oldTax) must be(true)
      }
    }
  }

  "adjustTaxData" should {
    "return adjusted tax values" when {
      "all the parameters are none" in {
        val sut = SUT
        val result = sut.adjustTaxData(oldTax = NpsTax())
        result must be((None, None, None, None))
      }

      "adjustment is not needed" in {
        val sut = SUT
        val taxBands = Some(List(TaxBand()))
        val totalTax = Some(BigDecimal(100))
        val totalTaxableIncome = Some(BigDecimal(100))
        val allowReliefDeducts = Some(NpsComponent(amount = Some(100)))
        val oldTax = NpsTax(
          allowReliefDeducts = allowReliefDeducts,
          totalTaxableIncome = totalTaxableIncome,
          totalTax = totalTax,
          taxBands = taxBands)

        val result = sut.adjustTaxData(taxCode = Some("D0"), oldTax = oldTax)
        result must be((taxBands, totalTax, totalTaxableIncome, Some(100)))
      }

      "adjustment is needed but oldTax does not have any value" in {
        val sut = SUT
        val oldTax = NpsTax(totalTax = Some(20), allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        result must be((None, None, None, None))
      }

      "adjustment is needed but total component has empty NPS component" in {
        val sut = SUT
        val oldTax = NpsTax(
          totalTax = Some(20),
          totalIncome = Some(NpsComponent()),
          allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        result must be((None, None, None, None))
      }

      "totalIncome is provided to adjust Taxable Income and Tax free amount" in {
        val sut = SUT
        val oldTax = NpsTax(
          totalTax = Some(20),
          totalIncome = Some(NpsComponent(amount = Some(1000))),
          allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        result must be((None, None, Some(960), Some(40)))
      }

      "taxable income is 0" in {
        val sut = SUT
        val oldTax = NpsTax(
          totalTax = Some(20),
          totalIncome = Some(NpsComponent(amount = Some(40))),
          allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        result must be((None, None, Some(0), Some(40)))
      }

      "tax bands are specified" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(20)), taxBand(rate = Some(40)))
        val oldTax = NpsTax(
          totalTax = Some(20),
          totalIncome = Some(NpsComponent(amount = Some(40))),
          taxBands = Some(taxBandList),
          allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        val expectedTaxBands = List(
          TaxBand(Some(0), Some(0), Some(0), Some(5000), Some(20)),
          TaxBand(Some(0), Some(0), Some(0), Some(5000), Some(40)))
        result must be((Some(expectedTaxBands), Some(0), Some(0), Some(40)))
      }

      "tax bands have specified tax" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(20), tax = Some(1000)), taxBand(rate = Some(40), tax = Some(2000)))
        val oldTax = NpsTax(
          totalTax = Some(20),
          totalIncome = Some(NpsComponent(amount = Some(1000))),
          taxBands = Some(taxBandList),
          allowReliefDeducts = Some(NpsComponent(amount = Some(50))))
        val result = sut.adjustTaxData(allowances = allowances, deductions = deductions, oldTax = oldTax)

        val expectedTaxBands = List(
          TaxBand(Some(960), Some(192), Some(0), Some(5000), Some(20)),
          TaxBand(Some(0), Some(0), Some(0), Some(5000), Some(40)))
        result must be((Some(expectedTaxBands), Some(192), Some(960), Some(40)))
      }

    }
  }

  "getStartDateInCurrentFinancialYear" should {
    "return financial year" when {
      val startDateCY = TaxYear().start

      "last year date is provided" in {
        val sut = SUT
        sut.getStartDateInCurrentFinancialYear(new LocalDate(2016, 6, 9)) must be(startDateCY)
      }

      "future date is provided" in {
        val sut = SUT
        val futureDate = startDateCY.plusMonths(1)
        sut.getStartDateInCurrentFinancialYear(futureDate) must be(futureDate)
      }
    }
  }

  "totalAtBasicRate" should {
    "return basic rate" when {
      "total income and tax bands are None" in {
        val sut = SUT
        sut.totalAtBasicRate(None, None) must be(0)
      }

      "total income is None" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(20), tax = Some(1000)), taxBand(rate = Some(40), tax = Some(2000)))
        sut.totalAtBasicRate(None, Some(taxBandList)) must be(0)
      }

      "total income has value" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(20), tax = Some(1000)), taxBand(rate = Some(40), tax = Some(2000)))
        sut.totalAtBasicRate(Some(100), Some(taxBandList)) must be(20.00)
      }

      "rate band is None" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = None, tax = Some(1000)))
        sut.totalAtBasicRate(Some(100), Some(taxBandList)) must be(0)
      }
    }
  }

  "actualTaxDueAssumingBasicRateAlreadyPaid" should {
    "return actual tax to be paid" when {
      "all parameters are none" in {
        val sut = SUT
        sut.actualTaxDueAssumingBasicRateAlreadyPaid(None, None, None) must be(None)
      }

      "totalTax has a value" in {
        val sut = SUT
        sut.actualTaxDueAssumingBasicRateAlreadyPaid(None, Some(200), None) must be(Some(200))
      }

      "tax is less than basic rate paid" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(50), tax = Some(1000)), taxBand(rate = Some(40), tax = Some(2000)))
        sut.actualTaxDueAssumingBasicRateAlreadyPaid(Some(100), Some(20), Some(taxBandList)) must be(None)
      }

      "tax is more than basic rate paid" in {
        val sut = SUT
        val taxBandList = List(taxBand(rate = Some(20), tax = Some(1000)), taxBand(rate = Some(40), tax = Some(2000)))
        sut.actualTaxDueAssumingBasicRateAlreadyPaid(Some(100), Some(200), Some(taxBandList)) must be(Some(180))
      }
    }
  }

  "calculateChildBenefit" should {
    "return child benefit" when {
      "both inputs are zero" in {
        val sut = SUT
        sut.calculateChildBenefit(0, 0) must be(0)
      }

      "net income is zero" in {
        val sut = SUT
        sut.calculateChildBenefit(1000, 0) must be(0)
      }

      "net income is on child benefit upper threshold" in {
        val sut = SUT
        sut.calculateChildBenefit(1000, TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD) must be(990)
      }

      "net income is greater than child benefit upper threshold" in {
        val sut = SUT
        sut.calculateChildBenefit(1000, TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD + 100) must be(1000)
      }

      "net income is lower than child benefit upper threshold" in {
        val sut = SUT
        sut.calculateChildBenefit(1000, TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD - 2000) must be(800)
      }

      "both values are negative" in {
        val sut = SUT
        sut.calculateChildBenefit(-1000, -100) must be(0)
      }
    }
  }

  private def SUT = TaxCalculator

  private def taxBand(
    income: Option[BigDecimal] = Some(10),
    tax: Option[BigDecimal] = Some(1000),
    lowerBand: Option[BigDecimal] = Some(0),
    upperBand: Option[BigDecimal] = Some(5000),
    rate: Option[BigDecimal] = Some(0)) = TaxBand(income, tax, lowerBand, upperBand, rate)
  private val allowances = Some(List(NpsComponent(Some(60)), NpsComponent(Some(30))))
  private val deductions = Some(List(NpsComponent(Some(20)), NpsComponent(Some(30))))

}
