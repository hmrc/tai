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

package uk.gov.hmrc.tai.model.domain.taxAdjustments

import org.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}

class TaxAdjustmentComponentSquidReadsSpec extends PlaySpec with MockitoSugar {
  "taxAdjustmentComponentReads" must {
    "return empty components" when {
      "relief giving back tax is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }

    "return reliefs giving back tax details" when {
      "relief details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> 100,
              "concessionalRelief"               -> 100.50,
              "maintenancePayments"              -> 200,
              "marriedCouplesAllowance"          -> 300,
              "doubleTaxationRelief"             -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
          TaxAdjustmentComponent(ConcessionalRelief, 100.5),
          TaxAdjustmentComponent(MaintenancePayments, 200),
          TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
          TaxAdjustmentComponent(DoubleTaxationRelief, 400)
        )
      }

      "partial details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> 100,
              "concessionalRelief"               -> 100.50,
              "maintenancePayments"              -> JsNull,
              "marriedCouplesAllowance"          -> 300,
              "doubleTaxationRelief"             -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
          TaxAdjustmentComponent(ConcessionalRelief, 100.5),
          TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
          TaxAdjustmentComponent(DoubleTaxationRelief, 400)
        )
      }

      "relief details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> JsNull,
              "concessionalRelief"               -> JsNull,
              "maintenancePayments"              -> JsNull,
              "marriedCouplesAllowance"          -> JsNull,
              "doubleTaxationRelief"             -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }
  }

  "taxAdjustmentComponentReads" must {
    "return none" when {
      "other tax due is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }

    "return other tax due details" when {
      "other tax due details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax"          -> 100,
              "excessWidowsAndOrphans"    -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit"              -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(ExcessGiftAidTax, 100),
          TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
          TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
          TaxAdjustmentComponent(ChildBenefit, 300)
        )
      }

      "partial details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax"          -> 100,
              "excessWidowsAndOrphans"    -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit"              -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(ExcessGiftAidTax, 100),
          TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
          TaxAdjustmentComponent(PensionPaymentsAdjustment, 200)
        )
      }

      "other tax due details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax"          -> JsNull,
              "excessWidowsAndOrphans"    -> JsNull,
              "pensionPaymentsAdjustment" -> JsNull,
              "childBenefit"              -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }
  }

  "TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads" must {
    "return none" when {
      "already taxed details is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }

    "return already tax source details" when {
      "already tax details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest"               -> 100,
              "taxCreditOnUKDividends"            -> 100,
              "taxCreditOnForeignInterest"        -> 200,
              "taxCreditOnForeignIncomeDividends" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
          TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
          TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
          TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
        )
      }

      "partial details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest"               -> JsNull,
              "taxCreditOnUKDividends"            -> 100,
              "taxCreditOnForeignInterest"        -> 200,
              "taxCreditOnForeignIncomeDividends" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
          TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200)
        )
      }

      "already tax details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest"               -> JsNull,
              "taxCreditOnUKDividends"            -> JsNull,
              "taxCreditOnForeignInterest"        -> JsNull,
              "taxCreditOnForeignIncomeDividends" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }
  }

  "taxAdjustmentComponentReads" must {
    "return liability components" when {
      "only reliefs are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> 100,
              "concessionalRelief"               -> 100.50,
              "maintenancePayments"              -> 200,
              "marriedCouplesAllowance"          -> 300,
              "doubleTaxationRelief"             -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](
          TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads
        ) must contain theSameElementsAs Seq(
          TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100),
          TaxAdjustmentComponent(ConcessionalRelief, 100.5),
          TaxAdjustmentComponent(MaintenancePayments, 200),
          TaxAdjustmentComponent(MarriedCouplesAllowance, 300),
          TaxAdjustmentComponent(DoubleTaxationRelief, 400)
        )
      }

      "only other tax dues are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax"          -> 100,
              "excessWidowsAndOrphans"    -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit"              -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](
          TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads
        ) must contain theSameElementsAs Seq(
          TaxAdjustmentComponent(ExcessGiftAidTax, 100),
          TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
          TaxAdjustmentComponent(PensionPaymentsAdjustment, 200),
          TaxAdjustmentComponent(ChildBenefit, 300)
        )
      }

      "only already taxed sources are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest"               -> 100,
              "taxCreditOnUKDividends"            -> 100,
              "taxCreditOnForeignInterest"        -> 200,
              "taxCreditOnForeignIncomeDividends" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](
          TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads
        ) must contain theSameElementsAs Seq(
          TaxAdjustmentComponent(TaxOnBankBSInterest, 100),
          TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
          TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200),
          TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
        )
      }

      "all the details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
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
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment"       -> 600,
              "personalPensionPaymentRelief" -> 100,
              "giftAidPaymentsRelief"        -> 200
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](
          TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads
        ) must contain theSameElementsAs Seq(
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
      }
    }
  }

  "taxAdjustmentComponentReads" must {
    "return empty sequence" when {
      "basic rate extension is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }

      "no details in basic rate extension is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment"       -> JsNull,
              "personalPensionPaymentRelief" -> JsNull,
              "giftAidPaymentsRelief"        -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq
          .empty[TaxAdjustmentComponent]
      }
    }

    "return tax relief components" when {
      "basic rate extension is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment"       -> 600,
              "personalPensionPaymentRelief" -> 100,
              "giftAidPaymentsRelief"        -> 200
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(PersonalPensionPayment, 600),
          TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100),
          TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
        )
      }

      "partial details in basic rate extension is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment" -> 600,
              "giftAidPaymentsRelief"  -> 200
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](TaxAdjustmentComponentSquidReads.taxAdjustmentComponentReads) mustBe Seq(
          TaxAdjustmentComponent(PersonalPensionPayment, 600),
          TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
        )
      }
    }
  }
}
