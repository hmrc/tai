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
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.IabdType.Profit
import uk.gov.hmrc.tai.model.enums.BasisOperation


class TaxModelFactoryGroupIncomesSpec extends PlaySpec with TaxModelFactoryTestData {

  "groupIncomes" should {
    "return none incomes" when {
      "passed none parameters" in {
        createSUT.groupIncomes() mustBe None
      }
    }
    "return some grouped incomes" when {
      "passed employments as taxCodeIncomeTotal" in {
        val taxCodeIncomeSummary = List(TaxCodeIncomeSummary(name = "name",
          taxCode = "taxCode", tax = Tax(Some(5000))))

        val taxCodeIncomeTotal = TaxCodeIncomeTotal(taxCodeIncomeSummary, BigDecimal(50000), BigDecimal(5000),
          BigDecimal(40000))

        val taxCodeIncomes = TaxCodeIncomes(Some(taxCodeIncomeTotal), None, None, None, false, 50000, 40000, 5000)
        val noneTaxCodeIncomes = NoneTaxCodeIncomes(totalIncome = BigDecimal(0))
        val total = BigDecimal(50000)

        val res = Some(Incomes(taxCodeIncomes, noneTaxCodeIncomes, total))

        createSUT.groupIncomes(employments = Some(taxCodeIncomeTotal)) mustBe res
      }

      "passed state pension and statePensionLumpSum" in {

        val resTaxCodeIncomes = TaxCodeIncomes(None, None, None, None, false, 0, 0, 0)
        val resNoneTaxCodeIncomes = NoneTaxCodeIncomes(Some(100), Some(200), None, None, None, None, None, None, None, None, 300)
        val resTotal = BigDecimal(300)

        val res = Some(Incomes(resTaxCodeIncomes, resNoneTaxCodeIncomes, resTotal))
        createSUT.groupIncomes(statePension = Some(BigDecimal(100)), statePensionLumpSum = Some(BigDecimal(200))) mustBe res
      }

      "passed occupational pensions as taxCodeIncomeTotal" in {
        val taxCodeIncomeSummary = List(TaxCodeIncomeSummary(name = "name",
          taxCode = "taxCode", tax = Tax(Some(5000))))

        val taxCodeIncomeTotal = TaxCodeIncomeTotal(taxCodeIncomeSummary, BigDecimal(50000), BigDecimal(5000),
          BigDecimal(40000))

        val resTaxCodeIncomes = TaxCodeIncomes(None, Some(taxCodeIncomeTotal), None, None, false, 50000, 40000, 5000)
        val resNoneTaxCodeIncomes = NoneTaxCodeIncomes(None, None, None, None, None, None, None, None, None, None, 0)
        val total = BigDecimal(50000)
        val res = Some(Incomes(resTaxCodeIncomes, resNoneTaxCodeIncomes, total))

        createSUT.groupIncomes(occupationalPensions = Some(taxCodeIncomeTotal)) mustBe res
      }

      "passed taxableStateBenefitIncomes as taxCodeIncomeTotal" in {
        val taxCodeIncomeSummary = List(TaxCodeIncomeSummary(name = "name",
          taxCode = "taxCode", tax = Tax(Some(5000))))

        val taxCodeIncomeTotal = TaxCodeIncomeTotal(taxCodeIncomeSummary, BigDecimal(50000), BigDecimal(5000),
          BigDecimal(40000))

        val resTaxCodeIncomes = TaxCodeIncomes(None, Some(taxCodeIncomeTotal), None, None, false, 50000, 40000, 5000)
        val resNoneTaxCodeIncomes = NoneTaxCodeIncomes(None, None, None, None, None, None, None, None, None, None, 0)
        val total = BigDecimal(50000)
        val res = Some(Incomes(TaxCodeIncomes(None, None, Some(taxCodeIncomeTotal), None, false, 50000, 40000, 5000), resNoneTaxCodeIncomes, total))

        createSUT.groupIncomes(taxableStateBenefitIncomes = Some(taxCodeIncomeTotal)) mustBe res
      }

      "passed taxableStateBenefit as taxComponent" in {

        val taxComponent = Some(TaxComponent(500, 5, "taxable state benefit", Nil))

        val noneTaxCodeIncomes = NoneTaxCodeIncomes(None, None, None, None, taxComponent, None, None, None, None, None, 500)
        val taxCodeIncomes = TaxCodeIncomes(None, None, None, None, false, 0, 0, 0)
        val resTotal = BigDecimal(500)
        val res = Some(Incomes(taxCodeIncomes, noneTaxCodeIncomes, resTotal))

        createSUT.groupIncomes(taxableStateBenefit = taxComponent) mustBe res
      }

      "passed ceasedEmployments as taxCodeIncomeTotal" in {
        val taxCodeIncomeSummary = List(TaxCodeIncomeSummary(name = "name",
          taxCode = "taxCode", tax = Tax(Some(5000))))

        val taxCodeIncomeTotal = TaxCodeIncomeTotal(taxCodeIncomeSummary, BigDecimal(50000), BigDecimal(5000),
          BigDecimal(40000))

        val resTaxCodeIncomes = TaxCodeIncomes(None, Some(taxCodeIncomeTotal), None, None, false, 50000, 40000, 5000)
        val resNoneTaxCodeIncomes = NoneTaxCodeIncomes(None,None,None,None,None,None,None,None,None,None,0)
        val resTotal = BigDecimal(50000)
        val res = Some(Incomes(TaxCodeIncomes(None,None,None,Some(taxCodeIncomeTotal),false,50000,40000,5000),resNoneTaxCodeIncomes,resTotal))

        createSUT.groupIncomes(ceasedEmployments = Some(taxCodeIncomeTotal)) mustBe res
      }

      "passed totalLiability and income sources" in {

        val npsIabdSummary = NpsIabdSummary(
          amount = Some(1000),
          `type` = Some(Profit.code),
          npsDescription = Some("npsIabdSummary description"),
          employmentId = Some(123),
          estimatedPaySource = Some(3)
        )

        val npsComponent = NpsComponent(
          amount = Some(1000),
          `type` = Some(Profit.code),
          iabdSummaries = Some(List(npsIabdSummary)),
          npsDescription = Some("nps component description"),
          sourceAmount = Some(200)
        )

        val npsTax = NpsTax(
          totalIncome = Some(npsComponent),
          allowReliefDeducts = Some(npsComponent),
          totalTaxableIncome = Some(100),
          totalTax = Some(300),
          taxBands = Some(List(taxBand))
        )

        val totalLiab = NpsTotalLiability(
          nonSavings = Some(npsTax),
          untaxedInterest = Some(npsTax),
          bankInterest = Some(npsTax),
          ukDividends = Some(npsTax),
          foreignInterest = Some(npsTax),
          foreignDividends = Some(npsTax),
          basicRateExtensions = Some(npsBasicRateExtensions),
          reliefsGivingBackTax = Some(npsReliefsGivingBackTax),
          otherTaxDue = Some(npsOtherTaxDue),
          alreadyTaxedAtSource = Some(npsAlreadyTaxedAtSource),
          totalLiability = Some(5000)
        )

        val incomeSources = Some(List(NpsIncomeSource(
          name = Some("income source name"),
          taxCode = Some("K950BR"),
          employmentType = Some(1),
          allowances = Some(List(npsComponent)),
          deductions = Some(List(npsComponent)),
          payAndTax = Some(npsTax),
          employmentId = Some(123),
          employmentStatus = Some(1),
          employmentTaxDistrictNumber = Some(321),
          employmentPayeRef = Some("321/000"),
          pensionIndicator = Some(false),
          otherIncomeSourceIndicator = Some(false),
          jsaIndicator = Some(false),
          basisOperation = Some(BasisOperation.Cumulative))
        ))

        val taxCodeIncomes = TaxCodeIncomes(None,None,None,None,false,0,0,0)

        val res = createSUT.groupIncomes(totalLiability = Some(totalLiab), incomeSources = incomeSources)

        res.get.taxCodeIncomes mustBe taxCodeIncomes
        res.get.noneTaxCodeIncomes.totalIncome mustBe BigDecimal(12000)
      }
    }
  }

  def createSUT = new SUT

  class SUT extends TaxModelFactory

}

