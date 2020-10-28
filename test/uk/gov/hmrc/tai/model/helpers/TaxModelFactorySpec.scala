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

package uk.gov.hmrc.tai.model.helpers

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito.{spy, times, verify}
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsIabdSummary, NpsReliefsGivingBackTax, NpsTax, NpsTotalLiability, _}
import uk.gov.hmrc.tai.model.nps2.{AllowanceType, DeductionType, IabdType, TaxAccount, TaxDetail, TaxObject}
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.model.{MarriageAllowance, _}
import uk.gov.hmrc.tai.util.TaiConstants

class TaxModelFactorySpec extends PlaySpec {

  "create" should {
    "return an empty TaxSummaryDetails object" when {
      "only default values are provided" in {
        val sut = createSUT
        val result = sut.create("", 0)
        result mustBe TaxSummaryDetails("", 0,
          extensionReliefs = Some(ExtensionReliefs(Some(ExtensionRelief()), Some(ExtensionRelief()))))
      }
    }

    "return paTapered in DecreasesTax as True in TaxSummaryDetails" when {
      "personal allowance amount is less than personal allowance source amount" in {
        val sut = createSUT
        val personsAllowanceNps = NpsComponent(amount = Some(1), sourceAmount = Some(2))

        val decreasesTaxResult = DecreasesTax(personalAllowance = Some(1),
          personalAllowanceSourceAmount = Some(2),
          paTapered = true,
          total = 1)

        val result = sut.create("", 0, personalAllowanceNps = Some(personsAllowanceNps))
        result.decreasesTax mustBe Some(decreasesTaxResult)
      }
    }

    "return paTapered in DecreasesTax as False in TaxSummaryDetails" when {
      "personal allowance amount is equal to the personal allowance source amount" in {
        val sut = createSUT
        val personsAllowanceNps = NpsComponent(amount = Some(1), sourceAmount = Some(1))

        val decreasesTaxResult = DecreasesTax(personalAllowance = Some(1),
          personalAllowanceSourceAmount = Some(1),
          paTapered = false,
          total = 1)

        val result = sut.create("", 0, personalAllowanceNps = Some(personsAllowanceNps))
        result.decreasesTax mustBe Some(decreasesTaxResult)
      }
    }

    "return paTapered in DecreasesTax as False in TaxSummaryDetails" when {
      "personal allowance amount is greater than personal allowance source amount" in {

        val sut = createSUT
        val personsAllowanceNps = NpsComponent(amount = Some(2), sourceAmount = Some(1))

        val decreasesTaxResult = DecreasesTax(personalAllowance = Some(2),
          personalAllowanceSourceAmount = Some(1),
          paTapered = false,
          total = 2)

        val result = sut.create("", 0, personalAllowanceNps = Some(personsAllowanceNps))
        result.decreasesTax mustBe Some(decreasesTaxResult)
      }
    }

    "not return a DeceasesTax element in TaxSummaryDetails as decreases tax total is 0 (zero)" when {
      "only personal allowance amount and personal allowance source amount are provided and both are 0 (zero)" in {
        val sut = createSUT
        val personsAllowanceNps = NpsComponent(amount = None, sourceAmount = None)
        val result = sut.create("", 0, personalAllowanceNps = Some(personsAllowanceNps))

        result.decreasesTax mustBe None
      }
    }

    "not return a DeceasesTax element in TaxSummaryDetails as decreases tax total is 0 (zero)" when {
      "only personal allowance amount and personal allowance source amount are provided and both are None will " +
        "default to 0 (zero)" in {
        val sut = createSUT
        val personsAllowanceNps = NpsComponent(amount = None, sourceAmount = None)
        val result = sut.create("", 0, personalAllowanceNps = Some(personsAllowanceNps))

        result.decreasesTax mustBe None
      }
    }

    "return employments with totals in IncreasesTax - taxCodeIncomes" when {
      "employments are supplied" in {
        val sut = createSUT
        val totalIncome = 120
        val totalTax = 20
        val totalTaxableIncome = 100
        val employments = TaxCodeIncomeTotal(Nil, totalIncome, totalTax, totalTaxableIncome)
        val result = sut.create("", 0, employments = Some(employments))

        result.increasesTax must not be None

        result.increasesTax foreach { it =>
          it.incomes must not be None
          it.incomes foreach { in =>
            in.taxCodeIncomes.employments mustBe Some(employments)
            in.taxCodeIncomes.totalIncome mustBe totalIncome
            in.taxCodeIncomes.totalTax mustBe totalTax
            in.taxCodeIncomes.totalTaxableIncome mustBe totalTaxableIncome
          }
        }
      }
    }

    "return state pension in IncreasesTax - noneTaxCodeIncomes" when {
      "state pension is supplied" in {
        val sut = createSUT
        val statePension = 123
        val result = sut.create("", 0, statePension = Some(statePension))

        result.increasesTax must not be None
        result.increasesTax foreach { it =>
          it.incomes must not be None
          it.incomes foreach { in =>
            in.noneTaxCodeIncomes.statePension mustBe Some(statePension)
            in.noneTaxCodeIncomes.totalIncome mustBe statePension
          }
        }
      }
    }

    "return state pension lump sum in IncreasesTax - noneTaxCodeIncomes" when {
      "state pension lump sum is supplied" in {
        val sut = createSUT
        val statePensionLumpSum = 123
        val result = sut.create("", 0, statePensionLumpSum = Some(statePensionLumpSum))

        result.increasesTax must not be None
        result.increasesTax foreach { it =>
          it.incomes must not be None
          it.incomes foreach { in =>
            in.noneTaxCodeIncomes.statePensionLumpSum mustBe Some(statePensionLumpSum)
            in.noneTaxCodeIncomes.totalIncome mustBe statePensionLumpSum
          }
        }
      }
    }

    "return occupationalPension in increasesTax - taxCodeIncomes" when {
      "occupationalPension is supplied" in {
        val sut = createSUT
        val result = sut.create("", 0, occupationalPensions = Some(occupationalPensionsIncomeTotal))

        result.increasesTax must not be None
        result.increasesTax foreach { it =>
          it.incomes must not be None
          it.incomes foreach { in =>
            in.taxCodeIncomes.occupationalPensions mustBe Some(occupationalPensionsIncomeTotal)
          }
        }
      }
    }

    "return taxable state benefit income in IncreasesTax - taxCodeIncomes" when {
      "taxable state benefit income is supplied" in {

        val sut = createSUT

        val totalIncome = 120
        val totalTax = 20
        val totalTaxableIncome = 100

        val taxableStateBenefitIncomes = TaxCodeIncomeTotal(Nil, totalIncome, totalTax, totalTaxableIncome)

        val result = sut.create("", 0, taxableStateBenefitIncomes = Some(taxableStateBenefitIncomes))

        result.increasesTax must not be None

        result.increasesTax foreach { it =>

          it.incomes must not be None

          it.incomes foreach { in =>
            in.taxCodeIncomes.taxableStateBenefitIncomes mustBe Some(taxableStateBenefitIncomes)
            in.taxCodeIncomes.totalIncome mustBe totalIncome
            in.taxCodeIncomes.totalTax mustBe totalTax
            in.taxCodeIncomes.totalTaxableIncome mustBe totalTaxableIncome
          }
        }
      }
    }

    "return taxable state benefit in IncreasesTax - noneTaxCodeIncomes" when {
      "taxable state benefit is supplied" in {

        val sut = createSUT

        val totalIncome = 120
        val taxableStateBenefit = TaxComponent(totalIncome, 0, "", Nil)

        val result = sut.create("", 0, taxableStateBenefit = Some(taxableStateBenefit))

        result.increasesTax must not be None

        result.increasesTax foreach { it =>

          it.incomes must not be None

          it.incomes foreach { in =>
            in.noneTaxCodeIncomes.taxableStateBenefit mustBe Some(taxableStateBenefit)
            in.noneTaxCodeIncomes.totalIncome mustBe totalIncome
          }
        }
      }
    }

    "return ceasedEmployments in IncreasesTax" when {
      "ceasedEmployments are supplied" in {

        val sut = createSUT

        val totalIncome = 120
        val totalTax = 20
        val totalTaxableIncome = 100

        val ceasedEmployments = TaxCodeIncomeTotal(Nil, totalIncome, totalTax, totalTaxableIncome)

        val result = sut.create("", 0, ceasedEmployments = Some(ceasedEmployments))

        result.increasesTax must not be None

        result.increasesTax foreach { it =>

          it.incomes must not be None

          it.incomes foreach { in =>
            in.taxCodeIncomes.ceasedEmployments mustBe Some(ceasedEmployments)
            in.taxCodeIncomes.totalIncome mustBe totalIncome
            in.taxCodeIncomes.totalTax mustBe totalTax
            in.taxCodeIncomes.totalTaxableIncome mustBe totalTaxableIncome
          }
        }
      }
    }

    "return a TotalLiability object" when {
      "an NpsTotalLiability object is provided" in {

        val sut = createSUT

        val totalLiability = NpsTotalLiability()

        val result = sut.create("", 0, totalLiability = Some(totalLiability))

        result.totalLiability must not be None
      }
    }

    "verify that incomeSources are passed to the verified methods and used in further processing" when {
      "incomeSources are supplied" in {

        val spySUT = spy(createSUT)

        val incomeSources = List(NpsIncomeSource(name=Some("testIncomeSource")))

        spySUT.create("", 0, incomeSources = Some(incomeSources))

        verify(spySUT, times(1)).groupIncomes(
          any(), any(), any(),
          any(), any(), any(),
          any(), any(), meq(Some(incomeSources)))

        verify(spySUT, times(1)).groupItemsThatIncreaseTax(
          any(), meq(Some(incomeSources)),
          any(), any())

        verify(spySUT, times(1)).groupItemsThatDecreaseTax(
          any(), meq(Some(incomeSources)),
          any(), any(), any())
      }
    }

    "return adjustedNetIncome" when {
      "adjustedNetIncome is provided" in {

        val sut = createSUT

        val adjustedNetIncomeValue = 123.23
        val adjustedNetIncome = NpsComponent(amount = Some(adjustedNetIncomeValue))

        val result = sut.create("", 0, adjustedNetIncome = Some(adjustedNetIncome))

        result.adjustedNetIncome mustBe adjustedNetIncomeValue
      }
    }

    "return underpaymentPreviousYear" when {
      "underpaymentPreviousYear is provided" in {

        val sut = createSUT

        val underpaymentPreviousYearValue = 123.23
        val totalLiability = NpsTotalLiability()
        val underpaymentPreviousYear = NpsComponent(sourceAmount = Some(underpaymentPreviousYearValue))

        val result = sut.create("", 0, underpaymentPreviousYear = Some(underpaymentPreviousYear), totalLiability = Some(totalLiability))

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.underpaymentPreviousYear mustBe underpaymentPreviousYearValue
        }
      }
    }

    "return inYearAdjustment" when {
      "inYearAdjustment is provided" in {

        val sut = createSUT

        val inYearAdjustmentValue = 123.23
        val totalLiability = NpsTotalLiability()
        val inYearAdjustment = NpsComponent(sourceAmount = Some(inYearAdjustmentValue))

        val result = sut.create("", 0, inYearAdjustment = Some(inYearAdjustment), totalLiability = Some(totalLiability))

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.inYearAdjustment mustBe Some(inYearAdjustmentValue)
        }
      }
    }

    "return outstandingDebt" when {
      "outstandingDebt is provided" in {

        val sut = createSUT

        val outstandingDebtValue = 123.23
        val totalLiability = NpsTotalLiability()
        val outstandingDebt = NpsComponent(sourceAmount = Some(outstandingDebtValue))

        val result = sut.create("", 0, outstandingDebt = Some(outstandingDebt), totalLiability = Some(totalLiability))

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.outstandingDebt mustBe outstandingDebtValue
        }
      }
    }

    "return childBenefitAmount and zero childBenefitTaxDue" when {
      "only childBenefitAmount is provided" in {

        val sut = createSUT

        val totalLiability = NpsTotalLiability()
        val childBenefitAmount = 123.23

        val result = sut.create("", 0, childBenefitAmount = Some(childBenefitAmount), totalLiability = Some(totalLiability))

        result.totalLiability must not be None

        result.totalLiability foreach { tl =>
          tl.childBenefitAmount mustBe childBenefitAmount
          tl.childBenefitTaxDue mustBe 0
        }
      }
    }

    "return childBenefitTaxDue and zero childBenefitAmount" when {
      "only childBenefitTaxDue is provided" in {

        val sut = createSUT

        val childBenefitTaxDue = 123.23
        val otherTaxDue = NpsOtherTaxDue(childBenefit = Some(childBenefitTaxDue))
        val totalLiability = NpsTotalLiability(otherTaxDue = Some(otherTaxDue))

        val result = sut.create("", 0, totalLiability = Some(totalLiability))

        result.totalLiability must not be None

        result.totalLiability foreach { tl =>
          tl.childBenefitAmount mustBe 0
          tl.childBenefitTaxDue mustBe childBenefitTaxDue
        }
      }
    }

    "return childBenefitAmount and childBenefitTaxDue" when {
      "childBenefitAmount and childBenefitTaxDue are provided" in {

        val sut = createSUT

        val childBenefitAmount = 123.23
        val childBenefitHigherThreshold = TaiConstants.CHILDBENEFIT_HIGHER_THRESHOLD
        val adjustedNetIncome = NpsComponent(amount = Some(childBenefitHigherThreshold))
        val totalLiability = NpsTotalLiability()

        val result = sut.create("", 0, totalLiability = Some(totalLiability), childBenefitAmount = Some(childBenefitAmount),
          adjustedNetIncome = Some(adjustedNetIncome))

        result.totalLiability must not be None

        result.totalLiability foreach { tl =>
          tl.childBenefitAmount mustBe childBenefitAmount
          tl.childBenefitTaxDue must not be 0
        }
      }
    }

    "verify that npsEmployments are passed to groupItemsThatIncreaseTax and used in further processing" when {
      "npsEmployments are supplied" in {

        val spySUT = spy(createSUT)

        val npsEmployments = List(NpsEmployment(0, NpsDate(LocalDate.parse("2010-01-01")), None, "", "", None, 0))

        spySUT.create("", 0, npsEmployments = Some(npsEmployments))

        verify(spySUT, times(1)).groupItemsThatIncreaseTax(any(), any(), any(), meq(Some(npsEmployments)))
      }
    }

    "return a marraige allowance value in TotalLiability - liabilityReductions" when {
      "a marraige allowance amount is supplied by taxCodeDetails" in {

        val sut = createSUT

        val marriageAllowanceAmount = 20
        val marriageAllowance = List(TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance.id), amount = Some(marriageAllowanceAmount)))
        val taxCodeDetails = TaxCodeDetails(None, None, None, None, Some(marriageAllowance), None, 0)
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(20))))

        val result = sut.create("", 0, totalLiability = Some(totalLiability), taxCodeDetails = Some(taxCodeDetails))

        result.totalLiability must not be None

        result.totalLiability foreach { tl =>

          tl.liabilityReductions must not be None

          tl.liabilityReductions foreach {
            lr => lr.marriageAllowance mustBe Some(MarriageAllowance(marriageAllowanceAmount, marriageAllowanceAmount))
          }
        }
      }
    }

    "return a zero total tax in the TotalLiability object" when {
      "an empty map is supplied for taxObjects in AnnualAccounts" in {

        val sut = createSUT

        val totalTax = 0
        val accounts = List(AnnualAccount(year = TaxYear(), nps = Some(TaxAccount(None, None, 0, taxObjects = Map.empty))))
        val totalLiability = NpsTotalLiability()

        val result = sut.create("", 0, totalLiability = Some(totalLiability), accounts = accounts)

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.totalTax mustBe totalTax
        }
      }
    }

    "return a single total tax value in the TotalLiability object" when {
      "a single total tax value is supplied by the tax objects map in AnnualAccounts" in {

        val sut = createSUT

        val totalTax = 20
        val taxObjects = Map(TaxObject.Type.NonSavings -> TaxDetail(totalTax = Some(totalTax)))
        val accounts = List(AnnualAccount(year = TaxYear(), nps = Some(TaxAccount(None, None, 0, taxObjects = taxObjects))))
        val totalLiability = NpsTotalLiability()

        val result = sut.create("", 0, totalLiability = Some(totalLiability), accounts = accounts)

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.totalTax mustBe totalTax
        }
      }
    }

    "return a summed total tax value in the TotalLiability object" when {
      "multiple total tax values are supplied by the tax objects map in AnnualAccounts" in {

        val sut = createSUT

        val totalTaxPerTaxObject = 20
        val totalTax = totalTaxPerTaxObject * 2
        val totalLiability = NpsTotalLiability()

        val taxObjects = Map(
          TaxObject.Type.NonSavings -> TaxDetail(totalTax = Some(totalTaxPerTaxObject)),
          TaxObject.Type.BankInterest -> TaxDetail(totalTax = Some(totalTaxPerTaxObject))
        )

        val accounts = List(AnnualAccount(year = TaxYear(), nps = Some(TaxAccount(None, None, 0, taxObjects = taxObjects))))

        val result = sut.create("", 0, totalLiability = Some(totalLiability), accounts = accounts)

        result.totalLiability must not be None

        result.totalLiability foreach {
          tl => tl.totalTax mustBe totalTax
        }
      }
    }

    "return ceasedEmploymentDetail in TaxSummaryDetails" when {
      "ceasedEmploymentDetail is supplied" in {

        val sut = createSUT

        val ceasedEmploymentDetail = CeasedEmploymentDetails(None, None, None, None)

        val result = sut.create("", 0, ceasedEmploymentDetail = Some(ceasedEmploymentDetail))

        result.ceasedEmploymentDetail mustBe Some(ceasedEmploymentDetail)
      }
    }
  }

  "createTaxCodeIncomeTotal" should {
    "return tax code income total" when {
      val employmentName = "test"
      val employmentId = 1234
      val employmentType = 1
      val taxBands = List(TaxBand(income = Some(1000), rate = Some(0)), TaxBand(income = Some(1000), rate = Some(20)))
      val resTax = Tax(Some(1000), Some(6000), Some(200), None, None, None, None,
        Some(taxBands), Some(5000), Some(1000), Some(200))

      "passed Nil as tax code incomes" in {
        val taxCodeIncomeTotal = createSUT.createTaxCodeIncomeTotal(Nil)
        taxCodeIncomeTotal mustBe TaxCodeIncomeTotal(Nil, 0, 0, 0)
      }

      "passed one tax-code summary" in {
        val taxCodeDetails = TaxCodeIncomeSummary(name = employmentName, taxCode = "BR",
          employmentId = Some(employmentId), employmentType = Some(employmentType), tax = resTax, income = Some(100))
        val taxCodeSummary = List(taxCodeDetails)
        val taxCodeIncomeTotal = createSUT.createTaxCodeIncomeTotal(taxCodeSummary)

        taxCodeIncomeTotal.taxCodeIncomes mustBe taxCodeSummary
        taxCodeIncomeTotal.totalIncome mustBe 100
        taxCodeIncomeTotal.totalTax mustBe 200
        taxCodeIncomeTotal.totalTaxableIncome mustBe 6000

      }

      "passed multiple tax-code details" in {
        val taxCodeDetails = TaxCodeIncomeSummary(name = employmentName, taxCode = "BR",
          employmentId = Some(employmentId), employmentType = Some(employmentType), tax = resTax, income = Some(100))

        val secondaryTaxCodeDetails = taxCodeDetails.copy(taxCode = "1150L", income = Some(200))

        val taxCodeSummary = List(taxCodeDetails, secondaryTaxCodeDetails)
        val taxCodeIncomeTotal = createSUT.createTaxCodeIncomeTotal(taxCodeSummary)

        taxCodeIncomeTotal.taxCodeIncomes mustBe taxCodeSummary
        taxCodeIncomeTotal.totalIncome mustBe 300
        taxCodeIncomeTotal.totalTax mustBe 400
        taxCodeIncomeTotal.totalTaxableIncome mustBe 12000
      }
    }
  }

  "getIncreasesTaxTotal" should {
    "return Increased Tax Total" when {
      "passed None incomes and benefits from employment" in {
        createSUT.getIncreasesTaxTotal() mustBe BigDecimal(0)
      }

      "passed incomes and benefits from employment" in {

        val taxCodeIncomes = TaxCodeIncomes(hasDuplicateEmploymentNames = false,
          totalIncome  = BigDecimal(50000),
          totalTaxableIncome = BigDecimal(40000),
          totalTax = BigDecimal(1000.0))

        val noneTaxCodeIncomes = NoneTaxCodeIncomes(totalIncome = BigDecimal(20000))
        val incomes = Some(Incomes(taxCodeIncomes, noneTaxCodeIncomes, BigDecimal(30000)))
        val benefits = Some(TaxComponent(BigDecimal(10000), 1, "desc", Nil))

        createSUT.getIncreasesTaxTotal(incomes, benefits) mustBe BigDecimal(40000)
      }
    }
  }

  "createTaxSummaryWithTotals" should {
    "return tax summary with totals" when {
      "passed default values" in {
        val taxSummaryDetails = createSUT.createTaxSummaryWithTotals(nino = "12345678", version = 1,
          adjustedNetIncome = None, ceasedEmploymentDetail = None)

        taxSummaryDetails.nino mustBe "12345678"
        taxSummaryDetails.decreasesTax mustBe None
        taxSummaryDetails.increasesTax mustBe None
        taxSummaryDetails.version mustBe 1
        taxSummaryDetails.totalLiability mustBe None
        taxSummaryDetails.adjustedNetIncome mustBe 0
        taxSummaryDetails.ceasedEmploymentDetail mustBe None
      }

      "passed custom values" in {
        val decreasesTaxDetail = Some(DecreasesTax(total = 100))
        val increasesTaxDetail = Some(IncreasesTax(total = 100))
        val totalLiabilityDetail = Some(TotalLiability(totalTax = 100))
        val ceasedEmploymentDetails = Some(CeasedEmploymentDetails(None, Some(true), Some("1"), Some(1)))
        val taxSummaryDetails = createSUT.createTaxSummaryWithTotals(nino = "12345678", version = 1,
          adjustedNetIncome = Some(100), ceasedEmploymentDetail = ceasedEmploymentDetails,
          decreasesTax = decreasesTaxDetail,
          increasesTax = increasesTaxDetail, totalLiability = totalLiabilityDetail)

        taxSummaryDetails.nino mustBe "12345678"
        taxSummaryDetails.decreasesTax mustBe decreasesTaxDetail
        taxSummaryDetails.increasesTax mustBe increasesTaxDetail
        taxSummaryDetails.version mustBe 1
        taxSummaryDetails.totalLiability mustBe totalLiabilityDetail
        taxSummaryDetails.adjustedNetIncome mustBe 100
        taxSummaryDetails.ceasedEmploymentDetail mustBe  ceasedEmploymentDetails
      }
    }
  }

  "createIncreasesTaxWithTotal" should {
    "return Increased Tax Total" when {
      "passed None incomes and benefits from employment" in {
        createSUT.createIncreasesTaxWithTotal() mustBe IncreasesTax(None,None,0)
      }

      "passed incomes and benefits from employment" in {

        val taxCodeIncomes = TaxCodeIncomes(hasDuplicateEmploymentNames = false,
          totalIncome  = BigDecimal(50000),
          totalTaxableIncome = BigDecimal(40000),
          totalTax = BigDecimal(1000.0))

        val noneTaxCodeIncomes = NoneTaxCodeIncomes(totalIncome = BigDecimal(20000))
        val incomes = Some(Incomes(taxCodeIncomes, noneTaxCodeIncomes, BigDecimal(30000)))
        val benefits = Some(TaxComponent(BigDecimal(10000), 1, "desc", Nil))

        val resIncomes = TaxCodeIncomes(None,None,None,None,hasDuplicateEmploymentNames = false,50000,40000,1000.0)
        val resNoneTaxCodeIncomes = NoneTaxCodeIncomes(None,None,None,None,None,None,None,None,None,None,20000)
        val resTotal = BigDecimal(30000)

        val res = IncreasesTax(Some(Incomes(resIncomes, resNoneTaxCodeIncomes, resTotal)), benefits, BigDecimal(40000))
        createSUT.createIncreasesTaxWithTotal(incomes, benefits) mustBe res
      }
    }
  }

  "updateDecreasesTaxTotal" should {
    "return updated decrease tax total" when {
      "passed decreases tax model with default values" in {
        val decTaxTotal = createSUT.updateDecreasesTaxTotal(DecreasesTax(total = 0))

        decTaxTotal.total mustBe 0
      }

      "passed decreases tax model with concrete values" in {
        val taxComponent = Some(TaxComponent(amount = 100, componentType = 1, description = "", iabdSummaries = Nil))
        val decreasesTaxDetail = DecreasesTax(total = 100, personalAllowance = Some(11500),
          personalAllowanceSourceAmount = Some(100), blindPerson = taxComponent, expenses = taxComponent, giftRelated = taxComponent,
          jobExpenses = taxComponent, miscellaneous = taxComponent, pensionContributions = taxComponent, paTransferredAmount = Some(100),
          paReceivedAmount = Some(100),personalSavingsAllowance = taxComponent)

        val decTaxTotal = createSUT.updateDecreasesTaxTotal(decreasesTaxDetail)

        decTaxTotal.total mustBe 12400
      }
    }
  }


  "convertToNonCodedIncome" should {
    "return tax amounts" when {
      "there is a non-coded income amount and a total tax amount" in {
        val totalTax = 300
        val nonCodedAmount = 101

        val npsTax = NpsTax(totalIncome = Some(NpsComponent(
          iabdSummaries = Some(List(
            NpsIabdSummary(`type` = Some(IabdType.NonCodedIncome.code), amount = Some(nonCodedAmount)),
            NpsIabdSummary(`type` = Some(IabdType.Commission.code), amount = Some(201))
          )))), totalTax = Some(totalTax))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe
          Some(Tax(totalIncome = Some(nonCodedAmount), totalTaxableIncome = Some(nonCodedAmount), totalTax = Some(totalTax)))
      }

      "iabd summaries is populated with a non-coded income code and the amount is negative" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent(iabdSummaries = Some(List(
          NpsIabdSummary(`type`= Some(IabdType.NonCodedIncome.code), amount= Some(-200))
        )))))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe Some(Tax(totalIncome = Some(-200),totalTaxableIncome = Some(-200)))
      }
    }

    "return an empty tax object" when {
      "iabd summaries is populated with a non-coded income code and the amount is none" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent(iabdSummaries = Some(List(
          NpsIabdSummary(`type`= Some(IabdType.NonCodedIncome.code), amount= None)
        )))))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe Some(Tax())
      }
    }

    "not return a tax object" when {
      "there is no NPS tax" in {
        createSUT.convertToNonCodedIncome(None) mustBe None
      }

      "NPS tax is empty" in {
        val npsTax = NpsTax()
        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }

      "NPS tax has no total income or total tax" in {
        val npsTax = NpsTax(totalIncome= None, totalTax= None)

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }

      "NPS component is empty" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent()))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }

      "there is no iabd summaries" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent(iabdSummaries = None)))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }

      "iabd summaries is an empty list" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent(iabdSummaries = Some(Nil))))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }

      "iabd summaries is populated and there is an amount but the type does not match the non-coded income code" in {
        val npsTax = NpsTax(totalIncome= Some(NpsComponent(iabdSummaries = Some(List(
          NpsIabdSummary(`type`= Some(IabdType.Commission.code), amount= Some(201))
        )))))

        createSUT.convertToNonCodedIncome(Some(npsTax)) mustBe None
      }
    }
  }

  "getMarriageAllowance" should {
    "return marriage allowance with amounts for marriage allowance and reliefs" when {
      "there is a marriage couples allowance amount in reliefs giving back tax and multiple married couples allowance amounts" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = List(
          TaxCodeComponent(amount = Some(7000), componentType = Some(AllowanceType.JobExpenses.id)),
          TaxCodeComponent(amount = Some(4000), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)),
          TaxCodeComponent(amount = Some(6000), componentType = Some(AllowanceType.MarriedCouplesAllowance5.id)),
          TaxCodeComponent(amount = Some(2000), componentType = Some(AllowanceType.MarriedCouplesAllowance.id)),
          TaxCodeComponent(amount = Some(5000), componentType = Some(AllowanceType.MarriedCouplesAllowance4.id)),
          TaxCodeComponent(amount = Some(3000), componentType = Some(AllowanceType.MarriedCouplesAllowance2.id))
        )

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))
        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe Some(MarriageAllowance(4000, 1000))
      }

      "there is a marriage couples allowance amount that is less than 0 in reliefs giving back tax" +
        "and multiple married couples allowance amounts less than zero" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(-1000))))

        val allowances = List(
          TaxCodeComponent(amount = Some(-7000), componentType = Some(AllowanceType.JobExpenses.id)),
          TaxCodeComponent(amount = Some(-4000), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)),
          TaxCodeComponent(amount = Some(-6000), componentType = Some(AllowanceType.MarriedCouplesAllowance5.id)),
          TaxCodeComponent(amount = Some(-2000), componentType = Some(AllowanceType.MarriedCouplesAllowance.id)),
          TaxCodeComponent(amount = Some(-5000), componentType = Some(AllowanceType.MarriedCouplesAllowance4.id)),
          TaxCodeComponent(amount = Some(-3000), componentType = Some(AllowanceType.MarriedCouplesAllowance2.id))
        )

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe Some(MarriageAllowance(-4000, -1000))
      }
    }

    "not return marriage allowance" when {
      "the married couples allowance amount in reliefs giving back tax is 0" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(0))))

        val allowances = List(TaxCodeComponent(amount = Some(4000), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "the married couples allowance amount in reliefs giving back tax is none" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = None)))

        val allowances = List(TaxCodeComponent(amount = Some(4000), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "there is a tax code component with a married couples allowance type but the amount is 0" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = List(TaxCodeComponent(amount = Some(10), componentType = Some(AllowanceType.JobExpenses.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "there is a tax code component with a married couples allowance type but the amount is none" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = List(TaxCodeComponent(amount = None, componentType = Some(AllowanceType.JobExpenses.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "there is a tax code component and the amount is other than 0 but the component type is something other than marriage allowance" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = List(TaxCodeComponent(amount = Some(0), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "reliefs giving back tax is none" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = None)

        val allowances = List(TaxCodeComponent(amount = Some(10), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "NPS total liability is empty" in {
        val totalLiability = NpsTotalLiability()

        val allowances = List(TaxCodeComponent(amount = Some(10), componentType = Some(AllowanceType.MarriedCouplesAllowance3.id)))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "the component type is none" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = List(TaxCodeComponent(amount = Some(10), componentType = None))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }

      "allowances is an empty list" in {
        val totalLiability = NpsTotalLiability(reliefsGivingBackTax = Some(NpsReliefsGivingBackTax(marriedCouplesAllowance = Some(1000))))

        val allowances = Nil

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(allowances))

        createSUT.getMarriageAllowance(Some(totalLiability), Some(taxCodeDetails)) mustBe None
      }
    }
  }

  "adjustMarriageAllowance" should {
    "return the allowances" when {
      "there are allowances and no Higher Personal Allowance Restriction" in {
        val allowance = TaxCodeComponent(componentType = Some(1))

        val result = createSUT.adjustMarriageAllowance(allowances = Some(List(allowance)), deductions = None)

        result mustBe Some(List(allowance))
      }

      "there are allowances and Higher Personal Allowance Restriction but no marriage allowances" in {
        val allowance = TaxCodeComponent(componentType = Some(1))
        val deduction = TaxCodeComponent(componentType = Some(DeductionType.HigherPersonalAllowanceRestriction.id))

        val result = createSUT.adjustMarriageAllowance(allowances = Some(List(allowance)), deductions = Some(List(deduction)))

        result mustBe Some(List(allowance))
      }

      "there is a marriage allowance and Higher Personal Allowance Restriction with no amount" in {
        val allowances = List(TaxCodeComponent(description = Some("ma1"), amount = Some(5), componentType = Some(AllowanceType.MarriedCouplesAllowance.id)))

        val deduction = TaxCodeComponent(amount = None, componentType = Some(DeductionType.HigherPersonalAllowanceRestriction.id))

        val result = createSUT.adjustMarriageAllowance(allowances = Some(allowances), deductions = Some(List(deduction)))

        result mustBe Some(allowances)
      }

      "there are allowances and deductions but no component type indications" in {
        val allowances = List(TaxCodeComponent(amount = Some(4), componentType = None))

        val deduction = TaxCodeComponent(amount = Some(5), componentType = None)

        val result = createSUT.adjustMarriageAllowance(allowances = Some(allowances), deductions = Some(List(deduction)))

        result mustBe Some(allowances)
      }
    }

    "return the marriage allowance amount minus higher personal allowance amount" when {
      "there is a marriage allowance and Higher Personal Allowance Restriction" in {
        val allowances = List(TaxCodeComponent(description = Some("ma1"), amount = Some(4), componentType = Some(AllowanceType.MarriedCouplesAllowance.id)))

        val deduction = TaxCodeComponent(amount = Some(5), componentType = Some(DeductionType.HigherPersonalAllowanceRestriction.id))

        val result = createSUT.adjustMarriageAllowance(allowances = Some(allowances), deductions = Some(List(deduction)))

        result mustBe Some(List(TaxCodeComponent(description = Some("ma1"), amount = Some(-1), componentType = Some(15))))
      }

      "there is a marriage allowance with no amount and Higher Personal Allowance Restriction" in {
        val allowances = List(TaxCodeComponent(description = Some("ma1"), amount = None, componentType = Some(AllowanceType.MarriedCouplesAllowance.id)))

        val deduction = TaxCodeComponent(amount = Some(5), componentType = Some(DeductionType.HigherPersonalAllowanceRestriction.id))

        val result = createSUT.adjustMarriageAllowance(allowances = Some(allowances), deductions = Some(List(deduction)))

        result mustBe Some(List(TaxCodeComponent(description = Some("ma1"), amount = Some(-5), componentType = Some(15))))
      }
    }

    "return an empty list" when {
      "allowances and deductions are empty lists" in {
        val result = createSUT.adjustMarriageAllowance(allowances = Some(Nil), deductions = Some(Nil))

        result mustBe Some(Nil)
      }
    }

    "return nothing" when {
      "there are no allowances or deductions" in {
        val result = createSUT.adjustMarriageAllowance(allowances = None, deductions = None)

        result mustBe None
      }
    }
  }

  "createTaxCodeInfo" should {
    "add the filtered list of allowed allowances" when {
      "given a list of all the allowance types" in {
        val taxCodeDetails = TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = None,
          allowances = Some(allowedAllowances ::: disallowedAllowances),
          total = 0
        )

        val decreasesTax = DecreasesTax(
          personalAllowance = None,
          personalAllowanceSourceAmount = None,
          blindPerson = None,
          expenses = None,
          giftRelated = None,
          jobExpenses = None,
          miscellaneous = None,
          pensionContributions = None,
          paTransferredAmount = None,
          paReceivedAmount = None,
          paTapered = false,
          personalSavingsAllowance = None,
          total = 0
        )

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = Some(decreasesTax))

        result mustBe Some(TaxCodeDetails(employment = None, taxCode = None, deductions = None, allowances = Some(filteredAllowances), total = 605))
      }
    }

    "add the filtered list allowed deductions" when {
      "given a list of all the deduction types" in {

        val disallowedDeductions = List(
          TaxCodeComponent(componentType = Some(DeductionType.OtherEarningsOrPension.id), description = Some("OEOP"), amount = Some(88)),
          TaxCodeComponent(componentType = Some(DeductionType.PersonalAllowanceTransferred.id), description = Some("PAT"), amount = Some(102))
        )

        val taxCodeDetails = TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(allowedDeductions ::: disallowedDeductions),
          allowances = None,
          total = 0
        )
        val decreasesTax = DecreasesTax(
          personalAllowance = None,
          personalAllowanceSourceAmount = None,
          blindPerson = None,
          expenses = None,
          giftRelated = None,
          jobExpenses = None,
          miscellaneous = None,
          pensionContributions = None,
          paTransferredAmount = None,
          paReceivedAmount = None,
          paTapered = false,
          personalSavingsAllowance = None,
          total = 0
        )

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = Some(decreasesTax))
        result mustBe Some(TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(filteredDeductions),
          allowances = None,
          splitAllowances = Some(true),
          total = -3300
        ))
      }
    }

    "set the split allowances to true" when {
      "the deductions contain the 'other earnings or pension' component type" in {
        val deduction = TaxCodeComponent(componentType = Some(DeductionType.OtherEarningsOrPension.id), description = Some("OEOP"), amount = Some(60))

        val taxCodeDetails = TaxCodeDetails( employment = None, taxCode = None, deductions = Some(List(deduction)), allowances = None)

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = None)

        result mustBe Some(TaxCodeDetails(employment = None, taxCode = None, deductions = Some(List()), allowances = None, splitAllowances = Some(true)))
      }
    }

    "set the split allowances to false" when {
      "the deductions do not contain the 'other earnings or pension' component type" in {
        val deduction = TaxCodeComponent(componentType = Some(DeductionType.OtherEarnings.id), description = Some("OE"), amount = Some(60))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = Some(List(deduction)), allowances = None)

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = None)

        result mustBe Some(TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(List( TaxCodeComponent(description = Some("OE"), amount = Some(60), componentType = Some(17)))),
          allowances = None,
          splitAllowances = Some(false),
          total = -60))
      }
    }

    "show the non-filtered deductions" when {
      "there is no amount for the individual deductions" in {
        val deduction1 = TaxCodeComponent(componentType = Some(DeductionType.EmployerBenefits.id), description = Some("EB"), amount = None)
        val deduction2 = TaxCodeComponent(componentType = Some(DeductionType.JobseekersAllowance.id), description = Some("JA"), amount = None)

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = Some(List(deduction1, deduction2)), allowances = None)

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = None)

        result mustBe Some(TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(List(TaxCodeComponent(description = Some("JA"), amount = None, componentType = Some(18)))),
          allowances = None,
          splitAllowances = Some(false),
          total = 0))
      }
    }

    "show the non-filtered deductions" when {
      "there is no component type" in {
        val deduction = TaxCodeComponent(componentType = None, description = Some("Unknown deduction"), amount = Some(13))

        val taxCodeDetails = TaxCodeDetails(employment = None, taxCode = None, deductions = Some(List(deduction)), allowances = None)

        val result = createSUT.createTaxCodeInfo(taxCodeDetails = Some(taxCodeDetails), decreasesTax = None)

        result mustBe Some(TaxCodeDetails(
          employment = None,
          taxCode = None,
          deductions = Some(List(TaxCodeComponent(description = Some("Unknown deduction"), amount = Some(13), componentType = None))),
          allowances = None,
          splitAllowances = Some(false),
          total = -13))
      }
    }
  }

  "createDecreasesTaxWithTotal" should {
    "return DecreaseTax object with updated total" when {
      "passed default values in arguments" in {
        val decreasesTax = createSUT.createDecreasesTaxWithTotal(paTapered = false)

        decreasesTax mustBe DecreasesTax(None,None,None,None,None,None,None,None,None,None,paTapered = false, None, total = 0)
      }

      "passed custom values" in {
        val taxComponent = Some(TaxComponent(amount = 100, componentType = 1, description = "", iabdSummaries = Nil))
        val npsComponent = Some(NpsComponent(amount = Some(100),sourceAmount = Some(50)))

        val decreaseTax = createSUT.createDecreasesTaxWithTotal(personalAllowance = npsComponent, blindPerson = taxComponent, expenses = taxComponent,
          giftRelated = taxComponent, jobExpenses = taxComponent, miscellaneous = taxComponent, pensionContributions = taxComponent,
          paTapered = true, paReceivedAmount = Some(100), paTransferredAmount = Some(100), personalSavingsAllowance = taxComponent)

        decreaseTax mustBe DecreasesTax(
          personalAllowance = Some(100),
          personalAllowanceSourceAmount = Some(50),
          blindPerson = taxComponent,
          expenses = taxComponent,
          giftRelated = taxComponent,
          jobExpenses = taxComponent,
          miscellaneous = taxComponent,
          pensionContributions = taxComponent,
          paTransferredAmount = Some(100),
          paReceivedAmount = Some(100),
          paTapered = true,
          personalSavingsAllowance = taxComponent,
          total = 1000)
      }
    }
  }

  "updateNoneTaxableIncomesTotal" should {
    "return the new total for NonTaxCodeIncomes" when {
      "each income has an amount" in {

        val incomes = NoneTaxCodeIncomes(
          statePension = Some(100),
          statePensionLumpSum = Some(200),
          otherPensions = Some(otherPensionsTaxComponent),
          otherIncome = Some(otherIncomeTaxComponent),
          taxableStateBenefit = Some(taxableStateBenefitTaxComponent),
          untaxedInterest = Some(untaxedInterestTaxComponent),
          bankBsInterest = Some(bankBsInterestTaxComponent),
          dividends = Some(dividendsTaxComponent),
          foreignInterest = Some(foreignInterestTaxComponent),
          foreignDividends = Some(foreignDividendsTaxComponent),
          totalIncome = 100
        )

        createSUT.updateNoneTaxableIncomesTotal(incomes).totalIncome mustBe 3600
      }
      "there are no income amounts" in {

        val incomes = NoneTaxCodeIncomes(
          statePension = None,
          statePensionLumpSum = None,
          otherPensions = None,
          otherIncome = None,
          taxableStateBenefit = None,
          untaxedInterest = None,
          bankBsInterest = None,
          dividends = None,
          foreignInterest = None,
          foreignDividends = None,
          totalIncome = 100
        )

        createSUT.updateNoneTaxableIncomesTotal(incomes).totalIncome mustBe 0
      }
    }
  }

  "updateTaxableIncomesTotal" should {
    "return the new total for TaxCodeIncomes" when {
      "each income has an amount" in {

        val incomes = TaxCodeIncomes(
          employments = Some(employmentsIncomeTotal),
          occupationalPensions = Some(occupationalPensionsIncomeTotal),
          taxableStateBenefitIncomes = Some(taxableStateBenefitIncomeTotal),
          ceasedEmployments = Some(ceasedEmploymentsIncomeTotal),
          hasDuplicateEmploymentNames = false,
          totalIncome = 1000,
          totalTaxableIncome = 800,
          totalTax = 300
        )

        val updatedIncomes = createSUT.updateTaxableIncomesTotal(incomes)

        updatedIncomes.totalIncome mustBe 16400
        updatedIncomes.totalTax mustBe 1010
        updatedIncomes.totalTaxableIncome mustBe 554
      }
      "there are no income amounts" in {

        val incomes = TaxCodeIncomes(
          employments = None,
          occupationalPensions = None,
          taxableStateBenefitIncomes = None,
          ceasedEmployments = None,
          hasDuplicateEmploymentNames = false,
          totalIncome = 1000,
          totalTaxableIncome = 800,
          totalTax = 300
        )

        val updatedIncomes = createSUT.updateTaxableIncomesTotal(incomes)

        updatedIncomes.totalIncome mustBe 0
        updatedIncomes.totalTax mustBe 0
        updatedIncomes.totalTaxableIncome mustBe 0
      }
    }
  }

  "updateIncomesTotal" should {
    "return updated Incomes Total" when {
      "passed incomes with TaxCodeIncomes and NonTaxCodeIncomes" in {

        val taxCodeIncomeSummary = List(TaxCodeIncomeSummary(name = "name",
          taxCode = "taxCode", tax = Tax(Some(5000))))

        val taxCodeIncomeTotal = TaxCodeIncomeTotal(taxCodeIncomeSummary, BigDecimal(50000), BigDecimal(5000),
          BigDecimal(40000))

        val taxCodeIncomes = TaxCodeIncomes(employments = Some(taxCodeIncomeTotal),
          hasDuplicateEmploymentNames = false,
          totalIncome = BigDecimal(50000),
          totalTaxableIncome = BigDecimal(40000),
          totalTax = BigDecimal(1000.0))

        val noneTaxCodeIncomes = NoneTaxCodeIncomes(
          statePension = Some(100),
          statePensionLumpSum = Some(200),
          otherPensions = Some(TaxComponent(300, 3, "other pensions", Nil)),
          otherIncome = Some(TaxComponent(400, 4, "other income", Nil)),
          taxableStateBenefit = Some(TaxComponent(500, 5, "taxable state benefit", Nil)),
          untaxedInterest = Some(TaxComponent(600, 6, "untaxed interest", Nil)),
          bankBsInterest = Some(TaxComponent(700, 7, "bank interest", Nil)),
          dividends = Some(TaxComponent(800, 8, "dividends", Nil)),
          foreignInterest = Some(TaxComponent(900, 9, "foreign interest", Nil)),
          foreignDividends = Some(TaxComponent(1000, 10, "foreign dividends", Nil)),
          totalIncome = 3600
        )

        val incomes = Incomes(taxCodeIncomes, noneTaxCodeIncomes, BigDecimal(30000))

        val resTaxCodeIncomesTotal = TaxCodeIncomeTotal(List(TaxCodeIncomeSummary("name", "taxCode", None,
          None, None, None, None, Tax(Some(5000), None, None, None, None, None, None, None), None, None, None, None, None, None, isEditable = false,
          isLive = false, isOccupationalPension = false, isPrimary = true, None, None)), 50000, 5000, 40000)

        val resTaxCodeInomes = TaxCodeIncomes(Some(resTaxCodeIncomesTotal), None, None, None, hasDuplicateEmploymentNames = false, 50000, 40000, 5000)
        val res = Incomes(resTaxCodeInomes, noneTaxCodeIncomes, BigDecimal(53600))

        createSUT.updateIncomesTotal(incomes) mustBe res
      }
    }
  }

  "createIncomesWithTotal" should {
    val taxCodeSummary = TaxCodeIncomeSummary("ABC", "taxCode", tax = Tax())
    val employmentDetails = Some(TaxCodeIncomeTotal(List(taxCodeSummary), 0, 0, 0))
    "return income object" when {
      "passed default values in arguments" in {
        val createdIncomesWithTotal = createSUT.createIncomesWithTotal()

        val resTaxCodeIncomes = TaxCodeIncomes(None, None, None, None, hasDuplicateEmploymentNames = false, 0, 0, 0)
        val resNonTaxCodeIncomes = NoneTaxCodeIncomes(None, None, None, None, None, None, None, None, None, None, 0)

        createdIncomesWithTotal mustBe Incomes(resTaxCodeIncomes, resNonTaxCodeIncomes, 0)
      }

      "passed taxable incomes" in {
        val createIncomesWithTotal = createSUT.createIncomesWithTotal(
          employments = employmentDetails,
          ceasedEmployments = employmentDetails,
          occupationalPensions = employmentDetails,
          taxableStateBenefitIncomes = employmentDetails)

        createIncomesWithTotal.taxCodeIncomes mustBe TaxCodeIncomes(
          employments = employmentDetails,
          ceasedEmployments = employmentDetails,
          occupationalPensions = employmentDetails,
          taxableStateBenefitIncomes = employmentDetails,
          hasDuplicateEmploymentNames = true,
          totalIncome = 0, totalTax = 0, totalTaxableIncome = 0
        )

      }

      "passed non taxable incomes" in {
        val taxComponent = Some(TaxComponent(BigDecimal(10000), 1, "desc", Nil))
        val createIncomesWithTotal = createSUT.createIncomesWithTotal(
          statePension = Some(100),
          statePensionLumpSum = Some(100),
          otherPensions = taxComponent,
          otherIncome = taxComponent,
          taxableStateBenefit = taxComponent,
          dividends = taxComponent,
          untaxedInterest = taxComponent,
          bankBsInterest = taxComponent,
          foreignInterest = taxComponent,
          foreignDividends = taxComponent)

        createIncomesWithTotal.noneTaxCodeIncomes mustBe NoneTaxCodeIncomes(
          statePension = Some(100),
          statePensionLumpSum = Some(100),
          otherPensions = taxComponent,
          otherIncome = taxComponent,
          taxableStateBenefit = taxComponent,
          dividends = taxComponent,
          untaxedInterest = taxComponent,
          bankBsInterest = taxComponent,
          foreignInterest = taxComponent,
          foreignDividends = taxComponent,
          totalIncome = 60200
        )
      }
    }

    "return hasDuplicateEmploymentNames boolean" when {
      "passed duplicate employer names" in {
        val ceasedEmployment = Some(TaxCodeIncomeTotal(List(taxCodeSummary.copy(name = "ABCD")), 0, 0, 0))
        val pensionName = Some(TaxCodeIncomeTotal(List(taxCodeSummary), 0, 0, 0))
        val taxableStateBenefits = Some(TaxCodeIncomeTotal(List(taxCodeSummary.copy(name = "ABCDE")), 0, 0, 0))

        val createIncomesWithTotal = createSUT.createIncomesWithTotal(
          employments = employmentDetails,
          ceasedEmployments = ceasedEmployment,
          occupationalPensions = pensionName,
          taxableStateBenefitIncomes = taxableStateBenefits)

        createIncomesWithTotal.taxCodeIncomes.hasDuplicateEmploymentNames mustBe true
      }

      "passed unique employer names" in {
        val ceasedEmployment = Some(TaxCodeIncomeTotal(List(taxCodeSummary.copy(name = "ABCD")), 0, 0, 0))
        val pensionName = Some(TaxCodeIncomeTotal(List(taxCodeSummary.copy(name = "ABCDE")), 0, 0, 0))
        val taxableStateBenefits = Some(TaxCodeIncomeTotal(List(taxCodeSummary.copy(name = "ABCDEF")), 0, 0, 0))

        val createIncomesWithTotal = createSUT.createIncomesWithTotal(
          employments = employmentDetails,
          ceasedEmployments = ceasedEmployment,
          occupationalPensions = pensionName,
          taxableStateBenefitIncomes = taxableStateBenefits)

        createIncomesWithTotal.taxCodeIncomes.hasDuplicateEmploymentNames mustBe false
      }
    }
  }

  val allowedAllowances = List(
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalPensionRelief.id), description = Some("PPR"), amount = Some(50)),
    TaxCodeComponent(componentType = Some(AllowanceType.GiftAidPayment.id), description = Some("GAP"), amount = Some(51)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance.id), description = Some("MCA1"), amount = Some(52)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance2.id), description = Some("MCA2"), amount = Some(53)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance3.id), description = Some("MCA3"), amount = Some(54)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance4.id), description = Some("MCA4"), amount = Some(55)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowance5.id), description = Some("MCA"), amount = Some(56)),
    TaxCodeComponent(componentType = Some(AllowanceType.EnterpriseInvestmentSchemeRelief.id), description = Some("EISR"), amount = Some(57)),
    TaxCodeComponent(componentType = Some(AllowanceType.ConcessionalRelief.id), description = Some("CR"), amount = Some(58)),
    TaxCodeComponent(componentType = Some(AllowanceType.MaintenancePayment.id), description = Some("MP"), amount = Some(59)),
    TaxCodeComponent(componentType = Some(AllowanceType.DoubleTaxationReliefAllowance.id), description = Some("DTRA"), amount = Some(60))
  )

  val disallowedAllowances = List(
    TaxCodeComponent(componentType = Some(AllowanceType.JobExpenses.id), description = Some("JE"), amount = Some(60)),
    TaxCodeComponent(componentType = Some(AllowanceType.FlatRateJobExpenses.id), description = Some("FRJE"), amount = Some(61)),
    TaxCodeComponent(componentType = Some(AllowanceType.ProfessionalSubscriptions.id), description = Some("PS"), amount = Some(62)),
    TaxCodeComponent(componentType = Some(AllowanceType.PaymentsTowardsARetirementAnnuity.id), description = Some("PTARA"), amount = Some(63)),
    TaxCodeComponent(componentType = Some(AllowanceType.LoanInterest.id), description = Some("LI"), amount = Some(64)),
    TaxCodeComponent(componentType = Some(AllowanceType.LossRelief.id), description = Some("LR"), amount = Some(65)),
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalAllowanceStandard.id), description = Some("PAS"), amount = Some(66)),
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalAllowanceAged.id), description = Some("PAA"), amount = Some(67)),
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalAllowanceElderly.id), description = Some("PAE"), amount = Some(68)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowanceFromHusband.id), description = Some("MCAFH1"), amount = Some(69)),
    TaxCodeComponent(componentType = Some(AllowanceType.MarriedCouplesAllowanceFromHusband2.id), description = Some("MCAFH2"), amount = Some(70)),
    TaxCodeComponent(componentType = Some(AllowanceType.BlindPersonsAllowance.id), description = Some("BPA"), amount = Some(71)),
    TaxCodeComponent(componentType = Some(AllowanceType.BalanceOfTaxAllowances.id), description = Some("BOTA"), amount = Some(72)),
    TaxCodeComponent(componentType = Some(AllowanceType.DeathSicknessOrFuneralBenefits.id), description = Some("DSOFB1"), amount = Some(73)),
    TaxCodeComponent(componentType = Some(AllowanceType.DeathSicknessOrFuneralBenefits2.id), description = Some("DSOFB2"), amount = Some(74)),
    TaxCodeComponent(componentType = Some(AllowanceType.DeathSicknessOrFuneralBenefits3.id), description = Some("DSOFB3"), amount = Some(75)),
    TaxCodeComponent(componentType = Some(AllowanceType.StartingRateAdjustment.id), description = Some("SRA"), amount = Some(76)),
    TaxCodeComponent(componentType = Some(AllowanceType.ForeignPensionAllowance.id), description = Some("FPA"), amount = Some(77)),
    TaxCodeComponent(componentType = Some(AllowanceType.EarlierYearsAdjustment.id), description = Some("EYA"), amount = Some(78)),
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalAllowanceReceived.id), description = Some("PAR"), amount = Some(79)),
    TaxCodeComponent(componentType = Some(AllowanceType.PersonalSavingsAllowance.id), description = Some("PSA"), amount = Some(80))
  )

  val allowedDeductions = List(
    TaxCodeComponent(componentType = Some(DeductionType.StatePensionOrBenefits.id), description = Some("SPOB"), amount = Some(60)),
    TaxCodeComponent(componentType = Some(DeductionType.PublicServicesPension.id), description = Some("PSP"), amount = Some(61)),
    TaxCodeComponent(componentType = Some(DeductionType.ForcesPension.id), description = Some("FP"), amount = Some(62)),
    TaxCodeComponent(componentType = Some(DeductionType.OtherPension.id), description = Some("OP"), amount = Some(63)),
    TaxCodeComponent(componentType = Some(DeductionType.TaxableIncapacityBenefit.id), description = Some("TIB"), amount = Some(64)),
    TaxCodeComponent(componentType = Some(DeductionType.MarriedCouplesAllowanceToYourWife.id), description = Some("TIB"), amount = Some(65)),
    TaxCodeComponent(componentType = Some(DeductionType.EmployerBenefits.id), description = Some("EB"), amount = Some(66)),
    TaxCodeComponent(componentType = Some(DeductionType.CarBenefit.id), description = Some("CB"), amount = Some(67)),
    TaxCodeComponent(componentType = Some(DeductionType.VanBenefit.id), description = Some("VB"), amount = Some(68)),
    TaxCodeComponent(componentType = Some(DeductionType.CarFuel.id), description = Some("CF"), amount = Some(69)),
    TaxCodeComponent(componentType = Some(DeductionType.ServiceBenefit.id), description = Some("SB"), amount = Some(70)),
    TaxCodeComponent(componentType = Some(DeductionType.LoanFromYourEmployer.id), description = Some("LFYE"), amount = Some(71)),
    TaxCodeComponent(componentType = Some(DeductionType.MedicalInsurance.id), description = Some("MI"), amount = Some(72)),
    TaxCodeComponent(componentType = Some(DeductionType.Telephone.id), description = Some("Telephone"), amount = Some(73)),
    TaxCodeComponent(componentType = Some(DeductionType.BalancingCharge.id), description = Some("BC"), amount = Some(74)),
    TaxCodeComponent(componentType = Some(DeductionType.TaxableExpensesPayments.id), description = Some("TEP"), amount = Some(75)),
    TaxCodeComponent(componentType = Some(DeductionType.OtherEarnings.id), description = Some("OE"), amount = Some(76)),
    TaxCodeComponent(componentType = Some(DeductionType.JobseekersAllowance.id), description = Some("JA"), amount = Some(77)),
    TaxCodeComponent(componentType = Some(DeductionType.PartTimeEarnings.id), description = Some("PTE"), amount = Some(78)),
    TaxCodeComponent(componentType = Some(DeductionType.Tips.id), description = Some("Tips"), amount = Some(79)),
    TaxCodeComponent(componentType = Some(DeductionType.Commission.id), description = Some("Commission"), amount = Some(80)),
    TaxCodeComponent(componentType = Some(DeductionType.OtherEarnings2.id), description = Some("OE2"), amount = Some(81)),
    TaxCodeComponent(componentType = Some(DeductionType.InterestWithoutTaxTakenOffGrossInterest.id), description = Some("IWTTOGI"), amount = Some(82)),
    TaxCodeComponent(componentType = Some(DeductionType.OtherIncomeNotEarnings.id), description = Some("OINE"), amount = Some(83)),
    TaxCodeComponent(componentType = Some(DeductionType.PropertyIncome.id), description = Some("PI"), amount = Some(84)),
    TaxCodeComponent(componentType = Some(DeductionType.Annuity.id), description = Some("Anuity"), amount = Some(85)),
    TaxCodeComponent(componentType = Some(DeductionType.PropertyIncome2.id), description = Some("PI2"), amount = Some(86)),
    TaxCodeComponent(componentType = Some(DeductionType.UnderpaymentRestriction.id), description = Some("UR"), amount = Some(87)),
    TaxCodeComponent(componentType = Some(DeductionType.GiftAidAdjustment.id), description = Some("GAA"), amount = Some(89)),
    TaxCodeComponent(componentType = Some(DeductionType.WidowsAndOrphansAdjustment.id), description = Some("WAOA"), amount = Some(90)),
    TaxCodeComponent(componentType = Some(DeductionType.SavingsIncomeTaxableAtHigherRate.id), description = Some("SITAHR"), amount = Some(91)),
    TaxCodeComponent(componentType = Some(DeductionType.AdjustmentToBasicRateBand.id), description = Some("ATBRB"), amount = Some(92)),
    TaxCodeComponent(componentType = Some(DeductionType.AdjustmentToLowerRateBand.id), description = Some("ATLRB"), amount = Some(93)),
    TaxCodeComponent(componentType = Some(DeductionType.UnderpaymentAmount.id), description = Some("UA"), amount = Some(94)),
    TaxCodeComponent(componentType = Some(DeductionType.VanFuelBenefit.id), description = Some("VFB"), amount = Some(95)),
    TaxCodeComponent(componentType = Some(DeductionType.HigherPersonalAllowanceRestriction.id), description = Some("HPAR"), amount = Some(96)),
    TaxCodeComponent(componentType = Some(DeductionType.EmploymentSupportAllowance.id), description = Some("ESA"), amount = Some(97)),
    TaxCodeComponent(componentType = Some(DeductionType.NonCashBenefits.id), description = Some("NCB"), amount = Some(98)),
    TaxCodeComponent(componentType = Some(DeductionType.AdjustmentToRateBand.id), description = Some("ATRB"), amount = Some(99)),
    TaxCodeComponent(componentType = Some(DeductionType.OutstandingDebtRestriction.id), description = Some("ODR"), amount = Some(100)),
    TaxCodeComponent(componentType = Some(DeductionType.ChildBenefit.id), description = Some("CB"), amount = Some(101)),
    TaxCodeComponent(componentType = Some(DeductionType.InYearAdjustment.id), description = Some("IYA"), amount = Some(103))
  )

  val filteredDeductions = List(
    TaxCodeComponent(Some("SPOB"), Some(60), Some(1)),
    TaxCodeComponent(Some("PSP"), Some(61), Some(2)),
    TaxCodeComponent(Some("FP"), Some(62), Some(3)),
    TaxCodeComponent(Some("OP"), Some(63), Some(4)),
    TaxCodeComponent(Some("TIB"), Some(64), Some(5)),
    TaxCodeComponent(Some("TIB"), Some(65), Some(6)),
    TaxCodeComponent(Some("BC"), Some(74), Some(15)),
    TaxCodeComponent(Some("OE"), Some(76), Some(17)),
    TaxCodeComponent(Some("JA"), Some(77), Some(18)),
    TaxCodeComponent(Some("PTE"), Some(78), Some(19)),
    TaxCodeComponent(Some("Tips"), Some(79), Some(20)),
    TaxCodeComponent(Some("Commission"), Some(80), Some(21)),
    TaxCodeComponent(Some("OE2"), Some(81), Some(22)),
    TaxCodeComponent(Some("IWTTOGI"), Some(82), Some(23)),
    TaxCodeComponent(Some("OINE"), Some(83), Some(24)),
    TaxCodeComponent(Some("PI"), Some(84), Some(25)),
    TaxCodeComponent(Some("Anuity"), Some(85), Some(26)),
    TaxCodeComponent(Some("PI2"), Some(86), Some(27)),
    TaxCodeComponent(Some("UR"), Some(87), Some(28)),
    TaxCodeComponent(Some("GAA"), Some(89), Some(30)),
    TaxCodeComponent(Some("WAOA"), Some(90), Some(31)),
    TaxCodeComponent(Some("SITAHR"), Some(91), Some(32)),
    TaxCodeComponent(Some("ATBRB"), Some(92), Some(33)),
    TaxCodeComponent(Some("ATLRB"), Some(93), Some(34)),
    TaxCodeComponent(Some("UA"), Some(94), Some(35)),
    TaxCodeComponent(Some("ESA"), Some(97), Some(38)),
    TaxCodeComponent(Some("ATRB"), Some(99), Some(40)),
    TaxCodeComponent(Some("ODR"), Some(100), Some(41)),
    TaxCodeComponent(Some("CB"), Some(101), Some(42)),
    TaxCodeComponent(Some("IYA"), Some(103), Some(45)),
    TaxCodeComponent(None, Some(824), Some(7))
  )
  val filteredAllowances = List(
    TaxCodeComponent(Some("Tax Free Amount"), Some(0), Some(0)),
    TaxCodeComponent(Some("PPR"), Some(50), Some(5)),
    TaxCodeComponent(Some("GAP"), Some(51), Some(6)),
    TaxCodeComponent(Some("MCA1"), Some(52), Some(15)),
    TaxCodeComponent(Some("MCA2"), Some(53), Some(16)),
    TaxCodeComponent(Some("MCA3"), Some(54), Some(17)),
    TaxCodeComponent(Some("MCA4"), Some(55), Some(18)),
    TaxCodeComponent(Some("MCA"), Some(56), Some(21)),
    TaxCodeComponent(Some("EISR"), Some(57), Some(7)),
    TaxCodeComponent(Some("CR"), Some(58), Some(28)),
    TaxCodeComponent(Some("MP"), Some(59), Some(10)),
    TaxCodeComponent(Some("DTRA"), Some(60), Some(29))
  )
  def createSUT = new SUT

  class SUT extends TaxModelFactory

  private val employmentsIncomeTotal = TaxCodeIncomeTotal(Nil, 1000, 200, 500)
  private val occupationalPensionsIncomeTotal = TaxCodeIncomeTotal(Nil, 7000, 700, 0)
  private val taxableStateBenefitIncomeTotal = TaxCodeIncomeTotal(Nil, 8000, 100, 50)
  private val ceasedEmploymentsIncomeTotal = TaxCodeIncomeTotal(Nil, 400, 10, 4)

  private val otherPensionsTaxComponent = TaxComponent(300, 3, "other pensions", Nil)
  private val otherIncomeTaxComponent = TaxComponent(400, 4, "other income", Nil)
  private val taxableStateBenefitTaxComponent = TaxComponent(500, 5, "taxable state benefit", Nil)
  private val untaxedInterestTaxComponent = TaxComponent(600, 6, "untaxed interest", Nil)
  private val bankBsInterestTaxComponent = TaxComponent(700, 7, "other pensions", Nil)
  private val dividendsTaxComponent = TaxComponent(800, 8, "other pensions", Nil)
  private val foreignInterestTaxComponent = TaxComponent(900, 9, "other pensions", Nil)
  private val foreignDividendsTaxComponent = TaxComponent(1000, 10, "other pensions", Nil)
}

