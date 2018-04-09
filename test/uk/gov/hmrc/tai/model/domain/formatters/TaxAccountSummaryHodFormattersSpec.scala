/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.formatters

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsNull, Json}
import uk.gov.hmrc.tai.model.domain.taxAdjustments._

class TaxAccountSummaryHodFormattersSpec extends PlaySpec with MockitoSugar with TaxAccountSummaryHodFormatters {

  "taxAccountSummaryReads" must {
    "return the totalEstTax from the hods response" when {
      "totalLiability val is present in totalLiability section" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "totalLiability" -> 1234.56
          )
        )

        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(1234.56)
      }
    }
    "return zero totalEstTax" when {
      "totalLiability section is NOT present" in {
        val json = Json.obj()
        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is null" in {
        val json = Json.obj(
          "totalLiability" -> JsNull
        )
        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is present but the totalLiability value is not present inside" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj()
        )
        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is present but the totalLiability value is null inside" in {
        val json = Json.obj(
          "totalLiability" -> JsNull
        )
        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(0)
      }
    }

    "return totalEstTax" when {
      "tax on other income is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "totalLiability" -> 1234.56,
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )))
            )))

        json.as[BigDecimal](taxAccountSummaryReads) mustBe BigDecimal(1194.56)
      }

    }
  }

  "ReliefsGivingBackTaxFormatters" must {
    "return empty components" when {
      "relief giving back tax is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }

    "return reliefs giving back tax details" when {
      "relief details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> 100,
              "concessionalRelief" -> 100.50,
              "maintenancePayments" -> 200,
              "marriedCouplesAllowance" -> 300,
              "doubleTaxationRelief" -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads) mustBe Seq(
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
              "concessionalRelief" -> 100.50,
              "maintenancePayments" -> JsNull,
              "marriedCouplesAllowance" -> 300,
              "doubleTaxationRelief" -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads) mustBe Seq(
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
              "concessionalRelief" -> JsNull,
              "maintenancePayments" -> JsNull,
              "marriedCouplesAllowance" -> JsNull,
              "doubleTaxationRelief" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }
  }

  "OtherTaxDueFormatters" must {
    "return none" when {
      "other tax due is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }

    "return other tax due details" when {
      "other tax due details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax" -> 100,
              "excessWidowsAndOrphans" -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads) mustBe Seq(
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
              "excessGiftAidTax" -> 100,
              "excessWidowsAndOrphans" -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads) mustBe Seq(
          TaxAdjustmentComponent(ExcessGiftAidTax, 100),
          TaxAdjustmentComponent(ExcessWidowsAndOrphans, 100),
          TaxAdjustmentComponent(PensionPaymentsAdjustment, 200)
        )
      }

      "other tax due details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax" -> JsNull,
              "excessWidowsAndOrphans" -> JsNull,
              "pensionPaymentsAdjustment" -> JsNull,
              "childBenefit" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }
  }

  "AlreadyTaxedAtSourceFormatters" must {
    "return none" when {
      "already taxed details is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }

    "return already tax source details" when {
      "already tax details are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest" -> 100,
              "taxCreditOnUKDividends" -> 100,
              "taxCreditOnForeignInterest" -> 200,
              "taxCreditOnForeignIncomeDividends" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads) mustBe Seq(
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
              "taxOnBankBSInterest" -> JsNull,
              "taxCreditOnUKDividends" -> 100,
              "taxCreditOnForeignInterest" -> 200,
              "taxCreditOnForeignIncomeDividends" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads) mustBe Seq(
          TaxAdjustmentComponent(TaxCreditOnUKDividends, 100),
          TaxAdjustmentComponent(TaxCreditOnForeignInterest, 200)
        )
      }

      "already tax details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest" -> JsNull,
              "taxCreditOnUKDividends" -> JsNull,
              "taxCreditOnForeignInterest" -> JsNull,
              "taxCreditOnForeignIncomeDividends" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }
  }

  "TaxOnOtherIncomeFormatters" must {
    "return tax on other income" when {
      "non-coded income is present and highest rate is 40%" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe Some(TaxOnOtherIncome(40))
      }

      "non-coded income is present and equal to highest rate income " in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 1000,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe Some(TaxOnOtherIncome(400))
      }

      "non-coded income is present and scattered in multiple rate bands " in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 10000,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 5000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )
                ,
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 8000,
                  "rate" -> 10
                )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe Some(TaxOnOtherIncome(2600))
      }

      "non-coded income is present and highest rate is 20%" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 20
              )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe Some(TaxOnOtherIncome(20))
      }

      "non-coded income is not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 84,
                  "npsDescription" -> "Job-Seeker Allowance",
                  "employmentId" -> JsNull
                ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 20
              )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe None
      }

      "non-coded income is present and tax bands are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsNull
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe None
      }

      "non-coded income is present but tax bands income is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> JsNull,
                "taxCode" -> "BR",
                "rate" -> 20
              )))
            )
          )
        )

        json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) mustBe None
      }
    }
  }

  "LiabilityComponentReads" must {
    "return liability components" when {
      "only reliefs are present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "reliefsGivingBackTax" -> Json.obj(
              "enterpriseInvestmentSchemeRelief" -> 100,
              "concessionalRelief" -> 100.50,
              "maintenancePayments" -> 200,
              "marriedCouplesAllowance" -> 300,
              "doubleTaxationRelief" -> 400
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads) must contain theSameElementsAs Seq(
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
              "excessGiftAidTax" -> 100,
              "excessWidowsAndOrphans" -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads) must contain theSameElementsAs Seq(
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
              "taxOnBankBSInterest" -> 100,
              "taxCreditOnUKDividends" -> 100,
              "taxCreditOnForeignInterest" -> 200,
              "taxCreditOnForeignIncomeDividends" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads) must contain theSameElementsAs Seq(
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
              "concessionalRelief" -> 100.50,
              "maintenancePayments" -> 200,
              "marriedCouplesAllowance" -> 300,
              "doubleTaxationRelief" -> 400
            ),
            "otherTaxDue" -> Json.obj(
              "excessGiftAidTax" -> 100,
              "excessWidowsAndOrphans" -> 100,
              "pensionPaymentsAdjustment" -> 200,
              "childBenefit" -> 300
            ),
            "alreadyTaxedAtSource" -> Json.obj(
              "taxOnBankBSInterest" -> 100,
              "taxCreditOnUKDividends" -> 100,
              "taxCreditOnForeignInterest" -> 200,
              "taxCreditOnForeignIncomeDividends" -> 300
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads) must contain theSameElementsAs Seq(
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
          TaxAdjustmentComponent(TaxCreditOnForeignIncomeDividends, 300)
        )
      }
    }
  }

  "taxOnOtherIncomeRead" must {
    "return income" when {
      "non-coded income is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(Seq(Json.obj(
                  "amount" -> 100,
                  "type" -> 19,
                  "npsDescription" -> "Non-Coded Income",
                  "employmentId" -> JsNull
                ),
                  Json.obj(
                    "amount" -> 100,
                    "type" -> 84,
                    "npsDescription" -> "Job-Seeker Allowance",
                    "employmentId" -> JsNull
                  ))

                )
              ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )))
            )
          )
        )
        json.as[Option[BigDecimal]](taxOnOtherIncomeRead) mustBe Some(40)
      }
    }

    "return none" when {
      "details are not present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray()
                ),
              "taxBands" -> JsArray(Seq(Json.obj(
                "bandType" -> "B",
                "income" -> 1000,
                "taxCode" -> "BR",
                "rate" -> 40
              ),
                Json.obj(
                  "bandType" -> "D0",
                  "taxCode" -> "BR",
                  "income" -> 1000,
                  "rate" -> 20
                )))
            )
          )
        )
        json.as[Option[BigDecimal]](taxOnOtherIncomeRead) mustBe None
      }
    }
  }

  "taxReliefComponentRead" must {
    "return empty sequence" when {
      "basic rate extension is null" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> JsNull
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxReliefFormattersReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }

      "no details in basic rate extension is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment" -> JsNull,
              "personalPensionPaymentRelief" -> JsNull,
              "giftAidPaymentsRelief" -> JsNull
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxReliefFormattersReads) mustBe Seq.empty[TaxAdjustmentComponent]
      }
    }

    "return tax relief components" when {
      "basic rate extension is present" in {
        val json = Json.obj(
          "totalLiability" -> Json.obj(
            "basicRateExtensions" -> Json.obj(
              "personalPensionPayment" -> 600,
              "personalPensionPaymentRelief" -> 100,
              "giftAidPaymentsRelief" -> 200
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxReliefFormattersReads) mustBe Seq(
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
              "giftAidPaymentsRelief" -> 200
            )
          )
        )

        json.as[Seq[TaxAdjustmentComponent]](taxReliefFormattersReads) mustBe Seq(
          TaxAdjustmentComponent(PersonalPensionPayment, 600),
          TaxAdjustmentComponent(GiftAidPaymentsRelief, 200)
        )
      }
    }
  }
}
