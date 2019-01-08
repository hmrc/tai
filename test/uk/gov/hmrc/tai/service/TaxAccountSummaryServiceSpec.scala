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

package uk.gov.hmrc.tai.service

import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation._
import uk.gov.hmrc.tai.model.domain.income.{Live, OtherBasisOperation, TaxCodeIncome}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.TaxAccountSummaryRepository

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class TaxAccountSummaryServiceSpec extends PlaySpec with MockitoSugar {

  "taxFreeAmountCalculation" must {
    "return zero" when {
      "there is no codingComponent" in {
        val sut = createSUT(mock[TaxAccountSummaryRepository], mock[CodingComponentService], mock[IncomeService])

        sut.taxFreeAmountCalculation(Seq.empty[CodingComponent]) mustBe 0
      }
    }

    "add allowances to taxFreeAmount value" when {
      "there are allowances in the coding component list" in {
        val codingComponents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 11500, "PersonalAllowancePA"),
          CodingComponent(MarriageAllowanceReceived, Some(234), 200, "MarriageAllowanceReceived"))

        val sut = createSUT(mock[TaxAccountSummaryRepository], mock[CodingComponentService], mock[IncomeService])

        sut.taxFreeAmountCalculation(codingComponents) mustBe 11700
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
          CodingComponent(UnderPaymentFromPreviousYear, Some(31), 10, "MarriageAllowanceTransferred"))

        val sut = createSUT(mock[TaxAccountSummaryRepository], mock[CodingComponentService], mock[IncomeService])

        sut.taxFreeAmountCalculation(codingComponents) mustBe -620
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

        val sut = createSUT(mock[TaxAccountSummaryRepository], mock[CodingComponentService], mock[IncomeService])

        sut.taxFreeAmountCalculation(codingComponents) mustBe 90
      }
    }
  }

  "taxAccountSummary" must {
    "return zero value in year adjustment figures" when {
      "no in year adjustment values are present on individual TaxCodeIncomeSource's" in {
        val taxCodeIncomes = Seq(
          TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1111),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(2222),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)))

        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(codingComponents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)
        val res = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5 seconds)
        res.totalInYearAdjustmentIntoCY mustBe BigDecimal(0)
        res.totalInYearAdjustment mustBe BigDecimal(0)
        res.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(0)
      }
      "in year adjustment amounts are present, but sum to zero" in {
        val taxCodeIncomes = Seq(
          TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1111),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(-13.37), BigDecimal(10.70), BigDecimal(11.10)),
          TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(2222),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(10.32), BigDecimal(-5.50), BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(2222),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(3.05), BigDecimal(-5.20), BigDecimal(-11.10)))

        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(codingComponents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)
        val res = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5 seconds)
        res.totalInYearAdjustmentIntoCY mustBe BigDecimal(0)
        res.totalInYearAdjustment mustBe BigDecimal(0)
        res.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(0)
      }
    }
    "return correctly generated in year adjustment figure" when {
      "in year adjustment amounts are present and sum to a non zero value" in {

        val taxCodeIncomes = Seq(
          TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1111),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(12.34), BigDecimal(33.30), BigDecimal(5.50)),
          TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(2222),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(55.10), BigDecimal(-10.10), BigDecimal(6.10))
        )

        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(codingComponents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)

        val res = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5 seconds)
        res.totalInYearAdjustmentIntoCY mustBe BigDecimal(67.44)
        res.totalInYearAdjustment mustBe BigDecimal(23.20)
        res.totalInYearAdjustmentIntoCYPlusOne mustBe BigDecimal(11.60)

      }
    }

    "return correctly generated TaxAccountSummary object" when {
      "receiving a range of components" in {
        val taxFreeAmountCompnents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 10000, "PersonalAllowancePA"),
          CodingComponent(EmployerProvidedServices, Some(12), 10000, "EmployerProvidedServices"),
          CodingComponent(BenefitInKind, Some(12), 100, "EmployerProvidedServices"),
          CodingComponent(ForeignDividendIncome, Some(12), 200, "ForeignDividendIncome"),
          CodingComponent(Commission, Some(12), 300, "ForeignDividendIncome"),
          CodingComponent(MarriageAllowanceTransferred, Some(31), 10, "MarriageAllowanceTransferred"),
          CodingComponent(UnderPaymentFromPreviousYear, Some(31), 10, "MarriageAllowanceTransferred"))

        val taxCodeIncomes = Seq(
          TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1111),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(12.34), BigDecimal(0), BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(2222),
            "PensionIncome", "1150L", "PensionProvider1", OtherBasisOperation, Live, BigDecimal(55.12), BigDecimal(0), BigDecimal(0))
        )

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(taxFreeAmountCompnents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTaxDetails))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(10000)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)

        Await.result(sut.taxAccountSummary(nino, TaxYear()), 5 seconds) mustBe
          TaxAccountSummary(1111, -620, 67.46, 0, 0, 0, 10000)
      }
    }

    "return TaxAccount summary with tax free allowance and taxableIncome" when {
      "liability sections are present" in {
        val taxFreeAmountCompnents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 5000, "PersonalAllowancePA")
        )
        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(taxFreeAmountCompnents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockTotalTaxService = mock[TotalTaxService]
        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 1000, 0, Seq.empty[TaxBand]),
          IncomeCategory(UntaxedInterestIncomeCategory, 0, 2000, 0, Seq.empty[TaxBand]),
          IncomeCategory(ForeignDividendsIncomeCategory, 0, 3000, 0, Seq.empty[TaxBand]),
          IncomeCategory(ForeignInterestIncomeCategory, 0, 4000, 0, Seq.empty[TaxBand]),
          IncomeCategory(BankInterestIncomeCategory, 0, 5000, 0, Seq.empty[TaxBand]),
          IncomeCategory(UkDividendsIncomeCategory, 0, 6000, 0, Seq.empty[TaxBand])
        )
        val totalTax  = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(1000)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)

        val result = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5.seconds)

        result.totalEstimatedIncome mustBe 22000
        result.taxFreeAllowance mustBe 1000
      }

      "tax free allowance is zero" in {
        val taxFreeAmountCompnents = Seq(
          CodingComponent(PersonalPensionPayments, Some(234), 5000, "PersonalPensionPayments"),
          CodingComponent(GiftAidPayments, Some(234), 5000, "GiftAid")
        )
        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(taxFreeAmountCompnents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(1111)))

        val mockTotalTaxService = mock[TotalTaxService]
        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 1000, 0, Seq.empty[TaxBand])
        )
        val totalTax  = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(0)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)

        val result = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5.seconds)

        result.totalEstimatedIncome mustBe 1000
        result.taxFreeAllowance mustBe 0
      }
    }
    "return TaxAccount summary with total estimated income and total estimated tax" when {
      "total estimated tax is zero" in {
        val taxFreeAmountCompnents = Seq(
          CodingComponent(PersonalAllowancePA, Some(234), 11500, "PersonalAllowancePA")
        )
        val mockcodingComponentService = mock[CodingComponentService]
        when(mockcodingComponentService.codingComponents(Matchers.eq(nino), any())(any())).thenReturn(Future.successful(taxFreeAmountCompnents))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val mockTaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
        when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), any())(any()))
          .thenReturn(Future.successful(BigDecimal(0)))

        val mockTotalTaxService = mock[TotalTaxService]
        val incomeCategories = Seq(
          IncomeCategory(NonSavingsIncomeCategory, 0, 0, 8000, Seq.empty[TaxBand])
        )
        val totalTax = TotalTax(0, incomeCategories, None, None, None)
        when(mockTotalTaxService.totalTax(any(), any())(any())).thenReturn(Future.successful(totalTax))
        when(mockTotalTaxService.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(11500)))

        val sut = createSUT(mockTaxAccountSummaryRepository, mockcodingComponentService, mockIncomeService, mockTotalTaxService)

        val result = Await.result(sut.taxAccountSummary(nino, TaxYear()), 5.seconds)

        result.totalEstimatedIncome mustBe 8000
        result.taxFreeAllowance mustBe 11500
      }
    }
  }

  private val nino: Nino = new Generator(new Random).nextNino

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  private val totalTaxDetails  = TotalTax(0, Seq.empty[IncomeCategory], None, None, None)

  val codingComponents: Seq[CodingComponent] =
    Seq(
      CodingComponent(GiftAidPayments, None, 1000, "GiftAidPayments description"),
      CodingComponent(PersonalPensionPayments, None, 1000, "PersonalPensionPayments description"))

  private def createSUT(taxAccountSummaryRepository: TaxAccountSummaryRepository,
                        codingComponentService: CodingComponentService,
                        incomeService: IncomeService,
                        totalTaxService: TotalTaxService = mock[TotalTaxService]) =
    new TaxAccountSummaryService(taxAccountSummaryRepository, codingComponentService, incomeService, totalTaxService)
}