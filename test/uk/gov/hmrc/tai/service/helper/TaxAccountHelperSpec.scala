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

package uk.gov.hmrc.tai.service.helper

import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchersSugar.eqTo
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.admin.HipToggleTaxAccount
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxAccountHelperSpec extends BaseSpec {
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private val mockTaxAccountConnector = mock[TaxAccountConnector]
  private def createSUT() = new TaxAccountHelper(mockTaxAccountConnector, mockFeatureFlagService)

  private val taxAccountSummaryNpsJson = Json
    .parse("""{
             |  "nationalInsuranceNumber": "AA000003",
             |  "taxYear": 2023,
             |  "totalLiabilityDetails": {
             |    "nonSavings": {
             |      "totalIncomeDetails": {
             |        "summaryIABDDetailsList": [],
             |        "summaryIABDEstimatedPayDetailsList": [
             |          {
             |            "amount": 100,
             |            "type": "Non-Coded Income (019)"
             |          },
             |          {
             |            "amount": 100,
             |            "type": "Job Seekers Allowance (084)"
             |          }
             |        ]
             |      },
             |      "taxBandDetails": [
             |        {
             |          "bandType": "B",
             |          "taxCode": "BR",
             |          "income": 1000,
             |          "rate": 40
             |        },
             |        {
             |          "bandType": "D0",
             |          "taxCode": "BR",
             |          "income": 1000,
             |          "rate": 20
             |        }
             |      ]
             |    },
             |    "basicRateExtensionsDetails": {
             |      "summaryIABDDetailsList": [],
             |      "personalPensionPayment": 600,
             |      "personalPensionPaymentRelief": 100,
             |      "giftAidPaymentsRelief": 200
             |    },
             |    "reliefsGivingBackTaxDetails": {
             |      "summaryIABDDetailsList": [],
             |      "enterpriseInvestmentSchemeRelief": 100,
             |      "concessionalRelief": 100.5,
             |      "maintenancePayments": 200,
             |      "marriedCouplesAllowance": 300,
             |      "doubleTaxationRelief": 400
             |    },
             |    "otherTaxDueDetails": {
             |      "summaryIABDDetailsList": [],
             |      "excessGiftAidTax": 100,
             |      "excessWidowsAndOrphans": 100,
             |      "pensionPaymentsAdjustment": 200,
             |      "childBenefit": 300
             |    },
             |    "totalLiability": 1111
             |  }
             |}""".stripMargin)
    .as[JsObject]

  private val taxAccountDetails = Future.successful(taxAccountSummaryNpsJson)
  private val emptyTaxAccountDetails = Future.successful(Json.obj())
  private def createJsonWithDeductions(deductions: JsArray) = {
    val incomeSources = Json.arr(Json.obj("deductionsDetails" -> deductions))
    taxAccountSummaryNpsJson ++ Json.obj("employmentDetailsList" -> incomeSources)
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleTaxAccount))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = true))
    )
  }

  "TotalEstimatedTax" must {
    "return totalEstimatedTax from the TaxAccountSummary connector" when {
      "underpayment from previous year present" in {

        val underpaymentDeduction = Json.arr(
          Json.obj(
            "adjustedAmount" -> 100,
            "type"           -> "Underpayment from previous year (35)",
            "sourceAmount"   -> 100
          )
        )
        val jsonWithUnderPayments = createJsonWithDeductions(underpaymentDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithUnderPayments))
        val result = createSUT().totalEstimatedTax(nino, TaxYear()).futureValue
        result mustBe BigDecimal(1171)
      }

      "outstanding debt present" in {
        val outstandingDebtDeduction = Json.arr(
          Json.obj(
            "adjustedAmount" -> 100,
            "type"           -> "Outstanding Debt (41)",
            "sourceAmount"   -> 100
          )
        )
        val jsonWithOutstandingDebt = createJsonWithDeductions(outstandingDebtDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithOutstandingDebt))

        val result = createSUT().totalEstimatedTax(nino, TaxYear()).futureValue
        result mustBe BigDecimal(1171)
      }

      "EstimatedTaxYouOweThisYear present" in {
        val estimatedTaxOwedDeduction = Json.arr(
          Json.obj(
            "adjustedAmount" -> 100,
            "type"           -> "Estimated Tax You Owe This Year (45)",
            "sourceAmount"   -> 100
          )
        )
        val jsonWithEstimatedTaxOwed = createJsonWithDeductions(estimatedTaxOwedDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithEstimatedTaxOwed))

        val result = createSUT().totalEstimatedTax(nino, TaxYear()).futureValue
        result mustBe BigDecimal(1171)
      }

      "all components" which {
        "can affect the totalTax are present" in {
          val allAffectingDeductions = Json.arr(
            Json.obj(
              "adjustedAmount" -> 100,
              "type"           -> "Underpayment from previous year (35)",
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "adjustedAmount" -> 100,
              "type"           -> "Outstanding Debt (41)",
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "adjustedAmount" -> 100,
              "type"           -> "Estimated Tax You Owe This Year (45)",
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "adjustedAmount" -> 100,
              "type"           -> "Something we aren't interested in (911)",
              "sourceAmount"   -> 100
            )
          )
          val jsonWithAllAffectingComponents = createJsonWithDeductions(allAffectingDeductions)
          when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(jsonWithAllAffectingComponents))

          val result = createSUT().totalEstimatedTax(nino, TaxYear()).futureValue
          result mustBe BigDecimal(1371)
        }
      }

      "no components" which {
        "can affect the totalTax are present" in {
          val noAffectingDeductions = Json.arr(
            Json.obj(
              "adjustedAmount" -> 100,
              "type"           -> "Community Investment Tax Credit (16)",
              "sourceAmount"   -> 100
            )
          )
          val jsonWithNoEffectingComponent = createJsonWithDeductions(noAffectingDeductions)
          when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(jsonWithNoEffectingComponent))

          val result = createSUT().totalEstimatedTax(nino, TaxYear()).futureValue
          result mustBe BigDecimal(1071)
        }
      }
    }
  }

  "Reliefs Giving Back Tax Components" must {
    "return only reliefs giving back tax components" in {
      val sut = createSUT()
      val result = sut.reliefsGivingBackTaxComponents(taxAccountDetails).futureValue

      result mustBe Some(
        TaxAdjustment(
          1100.5,
          Seq(
            TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
            TaxAdjustmentComponent(ConcessionalRelief, 100.5),
            TaxAdjustmentComponent(MaintenancePayments, 200),
            TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
            TaxAdjustmentComponent(DoubleTaxationRelief, 400)
          )
        )
      )
    }

    "return empty list " when {
      "reliefs giving back tax components are not present" in {
        val sut = createSUT()
        val result = sut.reliefsGivingBackTaxComponents(emptyTaxAccountDetails).futureValue

        result mustBe None
      }
    }
  }

  "Other Tax Due Components" must {
    "return only other tax due components" in {
      val sut = createSUT()
      val result = sut.otherTaxDueComponents(taxAccountDetails).futureValue

      result mustBe Some(
        TaxAdjustment(
          700,
          Seq(
            TaxAdjustmentComponent(ExcessGiftAidTax, 100),
            TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
            TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
            TaxAdjustmentComponent(ChildBenefit, 300)
          )
        )
      )
    }

    "return empty list " when {
      "other tax components are not present" in {
        val sut = createSUT()
        val result = sut.otherTaxDueComponents(emptyTaxAccountDetails).futureValue

        result mustBe None
      }
    }
  }

  "Already Taxed At Sources Components" must {
    "return only already taxed at source components" in {
      val sut = createSUT()
      val result = sut.alreadyTaxedAtSourceComponents(taxAccountDetails).futureValue

      result mustBe Some(
        TaxAdjustment(
          700,
          Seq(
            TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
            TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
            TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
            TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
          )
        )
      )
    }

    "return empty list " when {
      "already tax at source components are not present" in {
        val sut = createSUT()
        val result = sut.alreadyTaxedAtSourceComponents(emptyTaxAccountDetails).futureValue

        result mustBe None
      }
    }
  }
//
//  "TaxOnOtherIncome" must {
//    "return tax on other income rate" in {
//      val sut = createSUT()
//      val result = sut.taxOnOtherIncome(taxAccountDetails).futureValue
//
//      result mustBe Some(40)
//    }
//
//    "return none " when {
//      "tax on other income is not present" in {
//        val sut = createSUT()
//        val result = sut.taxOnOtherIncome(emptyTaxAccountDetails).futureValue
//
//        result mustBe None
//      }
//    }
//  }
//
//  "Tax Reliefs Component" must {
//    "return tax relief components including gift aid payment" in {
//      val jsonWithGiftAidPayment = taxAccountSummaryNpsJson ++ Json.obj(
//        "incomeSources" -> Json.arr(
//          Json.obj(
//            "allowances" -> Json.arr(
//              Json.obj(
//                "amount"       -> 100,
//                "type"         -> "Gift aid payment (6)",
//                "sourceAmount" -> 100
//              )
//            )
//          )
//        )
//      )
//
//      val taxAccountWithGiftAidPayment = Future.successful(jsonWithGiftAidPayment)
//      val sut = createSUT()
//      val result = sut.taxReliefComponents(taxAccountWithGiftAidPayment).futureValue
//
//      result mustBe Some(
//        TaxAdjustment(
//          1000,
//          Seq(
//            TaxAdjustmentComponent(PersonalPensionPayment, 600),
//            TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
//            TaxAdjustmentComponent(GiftAidPaymentsRelief, 200),
//            TaxAdjustmentComponent(GiftAidPayments, 100)
//          )
//        )
//      )
//    }
//
//    "return tax relief components excluding gift aid payment" in {
//      val sut = createSUT()
//      val result = sut.taxReliefComponents(taxAccountDetails).futureValue
//
//      result mustBe Some(
//        TaxAdjustment(
//          900,
//          Seq(
//            TaxAdjustmentComponent(PersonalPensionPayment, 600),
//            TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
//            TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
//          )
//        )
//      )
//    }
//
//    "return empty list" when {
//      "there is no data" in {
//        val sut = createSUT()
//        val result = sut.taxReliefComponents(emptyTaxAccountDetails).futureValue
//
//        result mustBe None
//      }
//    }
//  }
//
//  "Tax Adjustment Components" must {
//    "return tax adjustment components" when {
//      "components are present" in {
//        val sut = createSUT()
//        val result = sut.taxAdjustmentComponents(taxAccountDetails).futureValue
//
//        result mustBe Some(
//          TaxAdjustment(
//            3400.5,
//            Seq(
//              TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
//              TaxAdjustmentComponent(ConcessionalRelief, 100.5),
//              TaxAdjustmentComponent(MaintenancePayments, 200),
//              TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
//              TaxAdjustmentComponent(DoubleTaxationRelief, 400),
//              TaxAdjustmentComponent(ExcessGiftAidTax, 100),
//              TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
//              TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
//              TaxAdjustmentComponent(ChildBenefit, 300),
//              TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
//              TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
//              TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
//              TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300),
//              TaxAdjustmentComponent(PersonalPensionPayment, 600),
//              TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
//              TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
//            )
//          )
//        )
//      }
//    }
//
//    "return empty tax adjustment components" when {
//      "no tax adjustment component is present" in {
//        val sut = createSUT()
//        val result = sut.taxAdjustmentComponents(emptyTaxAccountDetails).futureValue
//
//        result mustBe None
//      }
//    }
//  }
}
