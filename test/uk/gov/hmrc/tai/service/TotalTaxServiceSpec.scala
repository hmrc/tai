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
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.model.domain.calculation._
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{TaxAccountSummaryRepository, TotalTaxRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random


class TotalTaxServiceSpec extends PlaySpec  with MockitoSugar{

  "totalTax" must{
    "return the income categories that is coming from TotalTaxRepository" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(Some(BigDecimal(40))))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.incomeCategories must contain theSameElementsAs incomeCategories
      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return amount that is coming from TaxAccountSummary" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(Some(BigDecimal(40))))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.amount mustBe BigDecimal(1000)
      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return reliefs giving back tax adjustment component" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(
        Some(adjustment)
      ))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(Some(BigDecimal(40))))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.reliefsGivingBackTax mustBe Some(adjustment)
    }


    "return other tax due component" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(ExcessGiftAidTax, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(Some(BigDecimal(40))))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.otherTaxDue mustBe Some(adjustment)
    }

    "return already taxed at source component" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(TaxOnBankBSInterest, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(None))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.alreadyTaxedAtSource mustBe Some(adjustment)
    }

    "return tax on other income" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(Some(BigDecimal(40))))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.taxOnOtherIncome mustBe Some(40)
    }

    "return tax relief components" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      val mockTaxAccountSummaryRepository =  mock[TaxAccountSummaryRepository]
      val taxReliefComponents = TaxAdjustment(100, Seq(TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100)))
      when(mockTotalTaxRepository.incomeCategories(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(incomeCategories))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(Some(taxReliefComponents)))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(None))

      val sut = createSUT(mockTotalTaxRepository, mockTaxAccountSummaryRepository)
      val result = Await.result(sut.totalTax(nino, TaxYear()), 5.seconds)

      result.taxReliefComponent mustBe Some(taxReliefComponents)
    }
  }

  "taxFreeAllowance" must {
    "return tax free allowance amount" in {
      val mockTotalTaxRepository = mock[TotalTaxRepository]
      when(mockTotalTaxRepository.taxFreeAllowance(any(), any())(any())).thenReturn(Future.successful(BigDecimal(100)))

      val sut = createSUT(mockTotalTaxRepository, mock[TaxAccountSummaryRepository])
      val result = Await.result(sut.taxFreeAllowance(nino, TaxYear()), 5.seconds)

      result mustBe 100

    }
  }

    private val nino: Nino = new Generator(new Random).nextNino

    private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

    val incomeCategories = Seq(
      IncomeCategory(UkDividendsIncomeCategory, 0, 0, 0,
        Seq(TaxBand(bandType = "", code = "", income = 0, tax = 0, lowerBand = None, upperBand = None, rate = 0),
          TaxBand(bandType = "B", code = "BR", income = 10000, tax = 500, lowerBand = Some(5000), upperBand = Some(20000), rate = 10))),
      IncomeCategory(ForeignDividendsIncomeCategory, 1000.23, 1000.24, 1000.25, Nil))

    private def createSUT(totalTaxRepository: TotalTaxRepository, taxAccountSummaryRepository: TaxAccountSummaryRepository) =
      new TotalTaxService(totalTaxRepository, taxAccountSummaryRepository)

}
