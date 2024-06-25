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

import play.api.libs.json.{JsArray, JsNull, Json}
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxAccountHelperSpec extends BaseSpec {
  private def createSUT() = new TaxAccountHelper()

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

  private val taxAccountDetails = Future.successful(taxAccountSummaryNpsJson)
  private val emptyTaxAccountDetails = Future.successful(Json.obj())

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

      val taxAccountWithGiftAidPayment = Future.successful(jsonWithGiftAidPayment)
      val sut = createSUT()
      val result = sut.taxReliefComponents(taxAccountWithGiftAidPayment).futureValue

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
      val sut = createSUT()
      val result = sut.taxReliefComponents(taxAccountDetails).futureValue

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
        val sut = createSUT()
        val result = sut.taxReliefComponents(emptyTaxAccountDetails).futureValue

        result mustBe None
      }
    }
  }

  "Tax Adjustment Components" must {
    "return tax adjustment components" when {
      "components are present" in {
        val sut = createSUT()
        val result = sut.taxAdjustmentComponents(taxAccountDetails).futureValue

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
        val sut = createSUT()
        val result = sut.taxAdjustmentComponents(emptyTaxAccountDetails).futureValue

        result mustBe None
      }
    }
  }
}
