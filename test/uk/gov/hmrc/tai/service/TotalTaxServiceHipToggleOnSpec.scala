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
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.calculation.{IncomeCategory, TotalTaxHipReads}
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxAccountHelper
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TotalTaxServiceHipToggleOnSpec extends BaseSpec {
  val mockTaxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]
  val mockTaxAccountHelper: TaxAccountHelper = mock[TaxAccountHelper]
  val mockTaxAccountSummaryService: TaxAccountSummaryService = mock[TaxAccountSummaryService]
  class Dummy
  val incomeCategoryHodFormatters = new Dummy
  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockTaxAccountConnector, mockTaxAccountHelper, mockTaxAccountSummaryService)
    when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
      .thenReturn(Future.successful(incomeCategories))
  }

  private def createSUT(
    taxAccountConnector: TaxAccountConnector,
    taxAccountHelper: TaxAccountHelper
  ) =
    new TotalTaxService(taxAccountConnector, taxAccountHelper)
  val sut: TotalTaxService = createSUT(mockTaxAccountConnector, mockTaxAccountHelper)

  val incomeCategories: JsObject = Json
    .parse("""{
             |   "nationalInsuranceNumber":"AA000003",
             |   "taxYear":2023,
             |   "totalLiabilityDetails":{
             |      "ukDividends":{
             |         "totalIncomeDetails":{
             |            "type":"",
             |            "summaryIABDDetailsList":[
             |               
             |            ]
             |         },
             |         "totalTax":0,
             |         "totalTaxableIncome":0,
             |         "taxBandDetails":[
             |            {
             |               "bandType":"",
             |               "taxCode":"",
             |               "income":0,
             |               "tax":0,
             |               "rate":0
             |            },
             |            {
             |               "bandType":"B",
             |               "taxCode":"BR",
             |               "income":10000,
             |               "tax":500,
             |               "lowerBand":5000,
             |               "upperBand":20000,
             |               "rate":10
             |            }
             |         ]
             |      },
             |      "foreignDividends":{
             |         "totalIncomeDetails":{
             |            "type":"",
             |            "summaryIABDDetailsList":[
             |               
             |            ]
             |         },
             |         "allowanceReliefDeductionsDetails":{
             |            "amount":100,
             |            "type":"",
             |            "summaryIABDDetailsList":[
             |               
             |            ]
             |         },
             |         "totalTax":1000.23,
             |         "totalTaxableIncome":1000.24
             |      }
             |   }
             |}""".stripMargin)
    .as[JsObject]

  "totalTax" must {
    "return the income categories that is coming from TaxAccountConnector" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue
      result.incomeCategories must contain theSameElementsAs incomeCategories.as[Seq[IncomeCategory]](
        TotalTaxHipReads.incomeCategorySeqReads
      )

      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return amount that is coming from totalEstimatedTax" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(1000)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.amount mustBe BigDecimal(1000)
      result.reliefsGivingBackTax mustBe None
      result.otherTaxDue mustBe None
      result.alreadyTaxedAtSource mustBe None
    }

    "return reliefs giving back tax adjustment component" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(
        Future.successful(
          Some(adjustment)
        )
      )
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.reliefsGivingBackTax mustBe Some(adjustment)
    }

    "return other tax due component" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(ExcessGiftAidTax, 100)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.otherTaxDue mustBe Some(adjustment)
    }

    "return already taxed at source component" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      val adjustment = TaxAdjustment(100, Seq(TaxAdjustmentComponent(TaxOnBankBSInterest, 100)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(Some(adjustment)))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.alreadyTaxedAtSource mustBe Some(adjustment)
    }

    "return tax on other income" in {
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(Some(BigDecimal(40))))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.taxOnOtherIncome mustBe Some(40)
    }

    "return tax relief components" in {
      val taxReliefComponents = TaxAdjustment(100, Seq(TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100)))
      when(mockTaxAccountHelper.totalEstimatedTax(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(BigDecimal(0)))
      when(mockTaxAccountHelper.reliefsGivingBackTaxComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.otherTaxDueComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.alreadyTaxedAtSourceComponents(any())).thenReturn(Future.successful(None))
      when(mockTaxAccountHelper.taxReliefComponents(any())).thenReturn(Future.successful(Some(taxReliefComponents)))
      when(mockTaxAccountHelper.taxOnOtherIncome(any())).thenReturn(Future.successful(None))

      val result = sut.totalTax(nino, TaxYear()).futureValue

      result.taxReliefComponent mustBe Some(taxReliefComponents)
    }
  }

  "taxFreeAllowance" must {
    "return tax free allowance amount" in {
      val result = sut.taxFreeAllowance(nino, TaxYear()).futureValue

      result mustBe 100
    }
    "return zero when empty payload" in {
      when(mockTaxAccountConnector.taxAccount(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(Json.obj()))
      val result = sut.taxFreeAllowance(nino, TaxYear()).futureValue

      result mustBe 0
    }
  }
}
