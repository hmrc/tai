/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.test.FakeRequest
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation._
import uk.gov.hmrc.tai.model.domain.income.{Live, OtherBasisOperation, TaxCodeIncome}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxAccountHelper
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxAccountSummaryServiceSpec extends BaseSpec {
  private val codingComponents: Seq[CodingComponent] =
    Seq(
      CodingComponent(GiftAidPayments, None, 1000, "GiftAidPayments description"),
      CodingComponent(PersonalPensionPayments, None, 1000, "PersonalPensionPayments description")
    )

  private val totalTaxDetails = TotalTax(0, Seq.empty[IncomeCategory], None, None, None)

  private val mockCodingComponentService = mock[CodingComponentService]
  private val mockIncomeService = mock[IncomeService]
  private val mockTotalTaxService = mock[TotalTaxService]
  private val mockTaxAccountHelper = mock[TaxAccountHelper]

  private def createSUT() = new TaxAccountSummaryService(
    mockCodingComponentService,
    mockIncomeService,
    mockTotalTaxService,
    mockTaxAccountHelper
  )

  "taxFreeAmountCalculation" must {
    "return zero" when {
      "there is no codingComponent" in {
        createSUT().taxFreeAmountCalculation(Seq.empty[CodingComponent]) mustBe 0
      }
    }

    "add allowances to taxFreeAmount value" when {
      "there are allowances in the coding component list" in {
        val codingComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 11500, "PersonalAllowancePA"),
          CodingComponent(MarriageAllowanceReceived, Some(234), 200, "MarriageAllowanceReceived")
        )

        createSUT().taxFreeAmountCalculation(codingComponents) mustBe 11700
      }
    }

    "subtract all the other coding componentTypes from taxFreeAmount value" when {
      "there are other Coding Components in the coding component list" in {
        val codingComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 10000, "PersonalAllowancePA"),
          CodingComponent(EmployerProvidedServices, Some(12), 10000, "EmployerProvidedServices"),
          CodingComponent(BenefitInKind, Some(12), 100, "EmployerProvidedServices"),
          CodingComponent(ForeignDividendIncome, Some(12), 200, "ForeignDividendIncome"),
          CodingComponent(Commission, Some(12), 300, "ForeignDividendIncome"),
          CodingComponent(MarriageAllowanceTransferred, Some(31), 10, "MarriageAllowanceTransferred"),
          CodingComponent(UnderPaymentFromPreviousYear, Some(31), 10, "MarriageAllowanceTransferred")
        )

        createSUT().taxFreeAmountCalculation(codingComponents) mustBe -620
      }
    }

    "add allowance and subtract other coding components" when {
      "even provided with negative allowance or deductions" in {
        val codingComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), -10300, "PersonalAllowancePA"),
          CodingComponent(EmployerProvidedServices, Some(12), -10000, "EmployerProvidedServices"),
          CodingComponent(ForeignDividendIncome, Some(12), 200, "ForeignDividendIncome"),
          CodingComponent(UnderPaymentFromPreviousYear, Some(31), -10, "MarriageAllowanceTransferred")
        )

        createSUT().taxFreeAmountCalculation(codingComponents) mustBe 90
      }
    }
  }

  "TaxAccountSummary" must {
    "return zero value in year adjustment figures" when {
      "no in year adjustment values are present on individual TaxCodeIncomeSource's" in {
        val taxCodeIncomes = Seq(
          TaxCodeIncome(
            PensionIncome,
            Some(1),
            BigDecimal(1111),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(2222),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(0),
            BigDecimal(0),
            BigDecimal(0)
          )
        )

        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(codingComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalInYearAdjustmentIntoCY mustBe BigDecimal(0)
        result.totalInYearAdjustment mustBe BigDecimal(0)
        result.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(0)
      }
      "in year adjustment amounts are present, but sum to zero" in {
        val taxCodeIncomes = Seq(
          TaxCodeIncome(
            PensionIncome,
            Some(1),
            BigDecimal(1111),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(-13.37),
            BigDecimal(10.70),
            BigDecimal(11.10)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(2222),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(10.32),
            BigDecimal(-5.50),
            BigDecimal(0)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(2222),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(3.05),
            BigDecimal(-5.20),
            BigDecimal(-11.10)
          )
        )

        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(codingComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(taxCodeIncomes))
        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalInYearAdjustmentIntoCY mustBe BigDecimal(0)
        result.totalInYearAdjustment mustBe BigDecimal(0)
        result.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(0)
      }
    }
    "return correctly generated in year adjustment figure" when {
      "in year adjustment amounts are present and sum to a non zero value" in {

        val taxCodeIncomes = Seq(
          TaxCodeIncome(
            PensionIncome,
            Some(1),
            BigDecimal(1111),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(12.34),
            BigDecimal(33.30),
            BigDecimal(5.50)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(2222),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(55.10),
            BigDecimal(-10.10),
            BigDecimal(6.10)
          )
        )

        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(codingComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalInYearAdjustmentIntoCY mustBe BigDecimal(67.44)
        result.totalInYearAdjustment mustBe BigDecimal(23.20)
        result.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(11.60)
      }
    }

    "return correctly generated TaxAccountSummary object" when {
      "receiving a range of components" in {
        val taxFreeAmountComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 10000, "PersonalAllowancePA"),
          CodingComponent(EmployerProvidedServices, Some(12), 10000, "EmployerProvidedServices"),
          CodingComponent(BenefitInKind, Some(12), 100, "EmployerProvidedServices"),
          CodingComponent(ForeignDividendIncome, Some(12), 200, "ForeignDividendIncome"),
          CodingComponent(Commission, Some(12), 300, "ForeignDividendIncome"),
          CodingComponent(MarriageAllowanceTransferred, Some(31), 10, "MarriageAllowanceTransferred"),
          CodingComponent(UnderPaymentFromPreviousYear, Some(31), 10, "MarriageAllowanceTransferred")
        )

        val taxCodeIncomes = Seq(
          TaxCodeIncome(
            PensionIncome,
            Some(1),
            BigDecimal(1111),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(12.34),
            BigDecimal(0),
            BigDecimal(0)
          ),
          TaxCodeIncome(
            EmploymentIncome,
            Some(1),
            BigDecimal(2222),
            "PensionIncome",
            "1150L",
            "PensionProvider1",
            OtherBasisOperation,
            Live,
            BigDecimal(55.12),
            BigDecimal(0),
            BigDecimal(0)
          )
        )

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))

        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(10000)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result mustBe TaxAccountSummary(1111, -620, 67.46, 0, 0, 0, 10000)
      }
    }

    "return TaxAccount summary with tax free allowance and taxableIncome" when {
      "liability sections are present" in {
        val taxFreeAmountComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 5000, "PersonalAllowancePA")
        )
        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))

        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 1000, 0, Seq.empty[TaxBand]),
          IncomeCategory(UntaxedInterestIncomeCategory, 0, 2000, 0, Seq.empty[TaxBand]),
          IncomeCategory(ForeignDividendsIncomeCategory, 0, 3000, 0, Seq.empty[TaxBand]),
          IncomeCategory(ForeignInterestIncomeCategory, 0, 4000, 0, Seq.empty[TaxBand]),
          IncomeCategory(BankInterestIncomeCategory, 0, 5000, 0, Seq.empty[TaxBand]),
          IncomeCategory(UkDividendsIncomeCategory, 0, 6000, 0, Seq.empty[TaxBand])
        )
        val totalTax = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(1000)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalEstimatedIncome mustBe 22000
        result.taxFreeAllowance mustBe 1000
      }

      "tax free allowance is zero" in {
        val taxFreeAmountComponents = Seq(
          CodingComponent(PersonalPensionPayments, Some(234), 5000, "PersonalPensionPayments"),
          CodingComponent(GiftAidPayments, Some(234), 5000, "GiftAid")
        )

        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(1111))

        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 1000, 0, Seq.empty[TaxBand])
        )
        val totalTax = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(0)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalEstimatedIncome mustBe 1000
        result.taxFreeAllowance mustBe 0
      }
    }
    "return TaxAccount summary with total estimated income and total estimated tax" when {
      "total estimated tax is zero" in {
        val taxFreeAmountComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 11500, "PersonalAllowancePA")
        )
        when(mockCodingComponentService.codingComponents(meq(nino), any())(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        when(mockIncomeService.taxCodeIncomes(meq(nino), any())(any(), any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), any())(any())).thenReturn(Future.successful(0))

        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 0, 8000, Seq.empty[TaxBand])
        )
        val totalTax = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(11500)))

        val result = createSUT().taxAccountSummary(nino, TaxYear())(implicitly, FakeRequest()).futureValue

        result.totalEstimatedIncome mustBe 8000
        result.taxFreeAllowance mustBe 11500
      }
    }
  }
}
