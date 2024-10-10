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

package uk.gov.hmrc.tai.repositories

import org.mockito.ArgumentMatchers.any
import play.api.libs.json.{JsArray, JsNull, Json}
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.TaxAccountSummaryRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxAccountSummaryRepositorySpec extends BaseSpec {

  private val mockTaxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]
  private def createSUT() = new TaxAccountSummaryRepository(mockTaxAccountConnector)

  private def createJsonWithDeductions(deductions: JsArray) = {
    val incomeSources = Json.arr(Json.obj("deductions" -> deductions))
    taxAccountSummaryNpsJson ++ Json.obj("incomeSources" -> incomeSources)
  }

  private val taxAccountSummaryNpsJson = Json.obj(
    "totalLiability" -> Json.obj(
      "totalLiability" -> 1111,
      "basicRateExtensions" -> Json.obj(
        "personalPensionPayment"       -> 600,
        "personalPensionPaymentRelief" -> 100,
        "giftAidPaymentsRelief"        -> 200
      ),
      "reliefsGivingBackTax" -> Json.obj(
        "enterpriseInvestmentSchemeRelief" -> 100,
        "concessionalRelief"               -> 100.50,
        "maintenancePayments"              -> 200,
        "marriedCouplesAllowance"          -> 300,
        "doubleTaxationRelief"             -> 400
      ),
      "otherTaxDue" -> Json.obj(
        "excessGiftAidTax"          -> 100,
        "excessWidowsAndOrphans"    -> 100,
        "pensionPaymentsAdjustment" -> 200,
        "childBenefit"              -> 300
      ),
      "alreadyTaxedAtSource" -> Json.obj(
        "taxOnBankBSInterest"               -> 100,
        "taxCreditOnUKDividends"            -> 100,
        "taxCreditOnForeignInterest"        -> 200,
        "taxCreditOnForeignIncomeDividends" -> 300
      ),
      "nonSavings" -> Json.obj(
        "totalIncome" -> Json.obj(
          "iabdSummaries" -> JsArray(
            Seq(
              Json.obj(
                "amount"         -> 100,
                "type"           -> 19,
                "npsDescription" -> "Non-Coded Income",
                "employmentId"   -> JsNull
              ),
              Json.obj(
                "amount"         -> 100,
                "type"           -> 84,
                "npsDescription" -> "Job-Seeker Allowance",
                "employmentId"   -> JsNull
              )
            )
          )
        ),
        "taxBands" -> JsArray(
          Seq(
            Json.obj(
              "bandType" -> "B",
              "income"   -> 1000,
              "taxCode"  -> "BR",
              "rate"     -> 40
            ),
            Json.obj(
              "bandType" -> "D0",
              "taxCode"  -> "BR",
              "income"   -> 1000,
              "rate"     -> 20
            )
          )
        )
      )
    )
  )

  "TaxAccountSummary" must {
    "return totalEstimatedTax from the TaxAccountSummary connector" when {
      "underpayment from previous year present" in {
        val underpaymentDeduction = Json.arr(
          Json.obj(
            "npsDescription" -> "Underpayment from previous year",
            "amount"         -> 100,
            "type"           -> 35,
            "sourceAmount"   -> 100
          )
        )
        val jsonWithUnderPayments = createJsonWithDeductions(underpaymentDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithUnderPayments))

        val sut = createSUT()
        val result = sut.taxAccountSummary(nino, TaxYear()).futureValue

        result mustBe BigDecimal(1171)
      }

      "outstanding debt present" in {
        val outstandingDebtDeduction = Json.arr(
          Json.obj(
            "npsDescription" -> "Outstanding Debt",
            "amount"         -> 100,
            "type"           -> 41,
            "sourceAmount"   -> 100
          )
        )
        val jsonWithOutstandingDebt = createJsonWithDeductions(outstandingDebtDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithOutstandingDebt))

        val sut = createSUT()
        val result = sut.taxAccountSummary(nino, TaxYear()).futureValue

        result mustBe BigDecimal(1171)
      }

      "EstimatedTaxYouOweThisYear present" in {
        val estimatedTaxOwedDeduction = Json.arr(
          Json.obj(
            "npsDescription" -> "Estimated Tax You Owe This Year",
            "amount"         -> 100,
            "type"           -> 45,
            "sourceAmount"   -> 100
          )
        )
        val jsonWithEstimatedTaxOwed = createJsonWithDeductions(estimatedTaxOwedDeduction)
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(jsonWithEstimatedTaxOwed))

        val sut = createSUT()
        val result = sut.taxAccountSummary(nino, TaxYear()).futureValue

        result mustBe BigDecimal(1171)
      }

      "all components" which {
        "can affect the totalTax are present" in {
          val allAffectingDeductions = Json.arr(
            Json.obj(
              "npsDescription" -> "Underpayment from previous year",
              "amount"         -> 100,
              "type"           -> 35,
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "npsDescription" -> "Outstanding Debt",
              "amount"         -> 100,
              "type"           -> 41,
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "npsDescription" -> "Estimated Tax You Owe This Year",
              "amount"         -> 100,
              "type"           -> 45,
              "sourceAmount"   -> 100
            ),
            Json.obj(
              "npsDescription" -> "Something we aren't interested in",
              "amount"         -> 100,
              "type"           -> 911,
              "sourceAmount"   -> 100
            )
          )
          val jsonWithAllAffectingComponents = createJsonWithDeductions(allAffectingDeductions)
          when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(jsonWithAllAffectingComponents))

          val sut = createSUT()
          val result = sut.taxAccountSummary(nino, TaxYear()).futureValue

          result mustBe BigDecimal(1371)
        }
      }

      "no components" which {
        "can affect the totalTax are present" in {
          val noAffectingDeductions = Json.arr(
            Json.obj(
              "npsDescription" -> "Community Investment Tax Credit",
              "amount"         -> 100,
              "type"           -> 16,
              "sourceAmount"   -> 100
            )
          )
          val jsonWithNoEffectingComponent = createJsonWithDeductions(noAffectingDeductions)
          when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(jsonWithNoEffectingComponent))

          val sut = createSUT()
          val result = sut.taxAccountSummary(nino, TaxYear()).futureValue

          result mustBe BigDecimal(1071)
        }
      }
    }

    "return tax adjustment components" when {
      "components are present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(taxAccountSummaryNpsJson))

        val sut = createSUT()
        val result = sut.taxAdjustmentComponents(nino, TaxYear()).futureValue

        result mustBe Some(
          TaxAdjustment(
            3400.5,
            Seq(
              TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
              TaxAdjustmentComponent(ConcessionalRelief, 100.5),
              TaxAdjustmentComponent(MaintenancePayments, 200),
              TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
              TaxAdjustmentComponent(DoubleTaxationRelief, 400),
              TaxAdjustmentComponent(ExcessGiftAidTax, 100),
              TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
              TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
              TaxAdjustmentComponent(ChildBenefit, 300),
              TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
              TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
              TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
              TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300),
              TaxAdjustmentComponent(PersonalPensionPayment, 600),
              TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
              TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
            )
          )
        )
      }
    }

    "return empty tax adjustment components" when {
      "no tax adjustment component is present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.taxAdjustmentComponents(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }

  "Reliefs Giving Back Tax Components" must {
    "return only reliefs giving back tax components" in {
      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))

      val sut = createSUT()
      val result = sut.reliefsGivingBackTaxComponents(nino, TaxYear()).futureValue

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
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.reliefsGivingBackTaxComponents(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }

  "Other Tax Due Components" must {
    "return only other tax due components" in {
      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))

      val sut = createSUT()
      val result = sut.otherTaxDueComponents(nino, TaxYear()).futureValue

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
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.otherTaxDueComponents(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }

  "Already Taxed At Sources Components" must {
    "return only already taxed at source components" in {
      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))

      val sut = createSUT()
      val result = sut.alreadyTaxedAtSourceComponents(nino, TaxYear()).futureValue

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
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.alreadyTaxedAtSourceComponents(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }

  "Tax on other income Component" must {
    "return only tax on other income components" in {
      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))

      val sut = createSUT()
      val result = sut.taxOnOtherIncome(nino, TaxYear()).futureValue

      result mustBe Some(40)
    }

    "return empty list " when {
      "tax on other income is not present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.taxOnOtherIncome(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }

  "Tax Reliefs Component" must {
    "return tax relief components including gift aid payment" in {
      val jsonWithGiftAidPayment = taxAccountSummaryNpsJson ++ Json.obj(
        "incomeSources" -> Json.arr(
          Json.obj(
            "allowances" -> Json.arr(
              Json.obj(
                "npsDescription" -> "Gift aid payment",
                "amount"         -> 100,
                "type"           -> 6,
                "sourceAmount"   -> 100
              )
            )
          )
        )
      )

      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(jsonWithGiftAidPayment))

      val sut = createSUT()
      val result = sut.taxReliefComponents(nino, TaxYear()).futureValue

      result mustBe Some(
        TaxAdjustment(
          1000,
          Seq(
            TaxAdjustmentComponent(PersonalPensionPayment, 600),
            TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
            TaxAdjustmentComponent(GiftAidPaymentsRelief, 200),
            TaxAdjustmentComponent(GiftAidPayments, 100)
          )
        )
      )
    }

    "return tax relief components excluding gift aid payment" in {
      when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
        .thenReturn(Future.successful(taxAccountSummaryNpsJson))

      val sut = createSUT()
      val result = sut.taxReliefComponents(nino, TaxYear()).futureValue

      result mustBe Some(
        TaxAdjustment(
          900,
          Seq(
            TaxAdjustmentComponent(PersonalPensionPayment, 600),
            TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
            TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
          )
        )
      )
    }

    "return empty list" when {
      "there is no data" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(Json.obj()))

        val sut = createSUT()
        val result = sut.taxReliefComponents(nino, TaxYear()).futureValue

        result mustBe None
      }
    }
  }
}
