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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.calculation.IncomeCategory
import uk.gov.hmrc.tai.model.domain.formatters.IncomeCategoryHodFormatters
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.TaxAccountSummaryRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TotalTaxServiceSpec extends BaseSpec {
  val mockTaxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]
  val mockTaxAccountSummaryRepository: TaxAccountSummaryRepository = mock[TaxAccountSummaryRepository]
  class Dummy extends IncomeCategoryHodFormatters
  val incomeCategoryHodFormatters = new Dummy

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaxAccountConnector, mockTaxAccountSummaryRepository)
    when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
      .thenReturn(Future.successful(incomeCategories))
  }

  val incomeCategories: JsObject = Json.obj(
    "taxYear" -> TaxYear().year,
    "totalLiability" -> Json.obj(
      "ukDividends" -> Json.obj(
        "totalTax"           -> 0,
        "totalTaxableIncome" -> 0,
        "totalIncome"        -> 0,
        "taxBands" -> Json.arr(
          Json.obj(
            "bandType"  -> "",
            "taxCode"   -> "",
            "income"    -> 0,
            "tax"       -> 0,
            "lowerBand" -> null,
            "upperBand" -> null,
            "rate"      -> 0
          ),
          Json.obj(
            "bandType"  -> "B",
            "taxCode"   -> "BR",
            "income"    -> 10000,
            "tax"       -> 500,
            "lowerBand" -> 5000,
            "upperBand" -> 20000,
            "rate"      -> 10
          )
        )
      ),
      "foreignDividends" -> Json.obj(
        "totalTax"           -> 1000.23,
        "totalTaxableIncome" -> 1000.24,
        "totalIncome"        -> 1000.25,
        "allowReliefDeducts" -> Json.obj("amount" -> 100)
      )
    )
  )

  private def createSUT(
    taxAccountConnector: TaxAccountConnector,
    taxAccountSummaryRepository: TaxAccountSummaryRepository
  ) =
    new TotalTaxService(taxAccountSummaryRepository, taxAccountConnector)
  val sut: TotalTaxService = createSUT(mockTaxAccountConnector, mockTaxAccountSummaryRepository)

  "totalTax" must {
    "return the income categories that is coming from TotalTaxRepository" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any()))
        .thenReturn(Future.successful(Some(BigDecimal(40))))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.incomeCategories must contain theSameElementsAs incomeCategories.as[Seq[IncomeCategory]](
        incomeCategoryHodFormatters.incomeCategorySeqReads
      )
      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return amount that is coming from TaxAccountSummary" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any()))
        .thenReturn(Future.successful(Some(BigDecimal(40))))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.amount mustBe BigDecimal(1000)
      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return reliefs giving back tax adjustment component" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any())).thenReturn(
        Future.successful(
          Some(adjustment)
        )
      )
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any()))
        .thenReturn(Future.successful(Some(BigDecimal(40))))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.reliefsGivingBackTax mustBe Some(adjustment)
    }

    "return other tax due component" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(ExcessGiftAidTax, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any()))
        .thenReturn(Future.successful(Some(BigDecimal(40))))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.otherTaxDue mustBe Some(adjustment)
    }

    "return already taxed at source component" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(TaxOnBankBSInterest, 100)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.alreadyTaxedAtSource mustBe Some(adjustment)
    }

    "return tax on other income" in {
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any()))
        .thenReturn(Future.successful(Some(BigDecimal(40))))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.taxOnOtherIncome mustBe Some(40)
    }

    "return tax relief components" in {
      val taxReliefComponents = TaxAdjustment(100, Seq(TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100)))
      when(mockTaxAccountSummaryRepository.taxAccountSummary(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountSummaryRepository.reliefsGivingBackTaxComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.otherTaxDueComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.alreadyTaxedAtSourceComponents(any(), any())(any()))
        .thenReturn(Future.successful(None))
      when(mockTaxAccountSummaryRepository.taxReliefComponents(any(), any())(any()))
        .thenReturn(Future.successful(Some(taxReliefComponents)))
      when(mockTaxAccountSummaryRepository.taxOnOtherIncome(any(), any())(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.taxReliefComponent mustBe Some(taxReliefComponents)
    }
  }

  "taxFreeAllowance" must {
    "return tax free allowance amount" in {
      val result = sut.taxFreeAllowance(nino, TaxYear()).futureValue

      result mustBe 100

    }
  }

}
