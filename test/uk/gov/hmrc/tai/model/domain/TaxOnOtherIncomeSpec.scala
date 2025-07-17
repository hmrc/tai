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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsNull, Json}
import uk.gov.hmrc.tai.model.hip.reads.TaxOnOtherIncomeHipReads

class TaxOnOtherIncomeSpec extends PlaySpec with MockitoSugar {

  "taxAccountSummaryReads" must {
    "return the totalEstTax from the hods response" when {
      "totalLiability val is present in totalLiability section" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "totalLiability" -> 1234.56
          )
        )

        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(1234.56)
      }
    }
    "return zero totalEstTax" when {
      "totalLiability section is NOT present" in {
        val json = Json.obj()
        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is null" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> JsNull
        )
        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is present but the totalLiability value is not present inside" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj()
        )
        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(0)
      }
      "totalLiability section is present but the totalLiability value is null inside" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> JsNull
        )
        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(0)
      }
    }

    "return totalEstTax" when {
      "tax on other income is present" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "totalLiability" -> 1234.56,
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
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

        json.as[BigDecimal](TaxOnOtherIncomeHipReads.taxAccountSummaryReads) mustBe BigDecimal(1194.56)
      }

    }
  }

  "TaxOnOtherIncomeFormatters" must {
    "return tax on other income" when {
      "non-coded income is present and highest rate is 40%" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
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

        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe Some(40)
      }

      "non-coded income is present and equal to highest rate income " in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 1000,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
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

        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe Some(400)
      }

      "non-coded income is present and scattered in multiple rate bands " in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 10000,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
                Seq(
                  Json.obj(
                    "bandType" -> "B",
                    "income"   -> 5000,
                    "taxCode"  -> "BR",
                    "rate"     -> 40
                  ),
                  Json.obj(
                    "bandType" -> "D0",
                    "taxCode"  -> "BR",
                    "income"   -> 1000,
                    "rate"     -> 20
                  ),
                  Json.obj(
                    "bandType" -> "D0",
                    "taxCode"  -> "BR",
                    "income"   -> 8000,
                    "rate"     -> 10
                  )
                )
              )
            )
          )
        )

        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe Some(2600)
      }

      "non-coded income is present and highest rate is 20%" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
                Seq(
                  Json.obj(
                    "bandType" -> "B",
                    "income"   -> 1000,
                    "taxCode"  -> "BR",
                    "rate"     -> 20
                  )
                )
              )
            )
          )
        )

        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe Some(20)
      }

      "non-coded income is not present" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
                Seq(
                  Json.obj(
                    "bandType" -> "B",
                    "income"   -> 1000,
                    "taxCode"  -> "BR",
                    "rate"     -> 20
                  )
                )
              )
            )
          )
        )
        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe None
      }

      "non-coded income is present and tax bands are not present" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsNull
            )
          )
        )

        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe None
      }

      "non-coded income is present but tax bands income is null" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
                Seq(
                  Json.obj(
                    "bandType" -> "B",
                    "income"   -> JsNull,
                    "taxCode"  -> "BR",
                    "rate"     -> 20
                  )
                )
              )
            )
          )
        )
        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe None
      }
    }
  }

  "taxOnOtherIncomeRead" must {
    "return income" when {
      "non-coded income is present" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Non-Coded Income (019)",
                      "npsDescription" -> "Non-Coded Income",
                      "employmentId"   -> JsNull
                    ),
                    Json.obj(
                      "amount"         -> 100,
                      "type"           -> "Job Seekers Allowance (084)",
                      "npsDescription" -> "Job-Seeker Allowance",
                      "employmentId"   -> JsNull
                    )
                  )
                )
              ),
              "taxBandDetails" -> JsArray(
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
        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe Some(40)
      }
    }

    "return none" when {
      "details are not present" in {
        val json = Json.obj(
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray()
              ),
              "taxBandDetails" -> JsArray(
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
        json.as[Option[BigDecimal]](TaxOnOtherIncomeHipReads.taxOnOtherIncomeTaxValueReads) mustBe None
      }
    }
  }

}
