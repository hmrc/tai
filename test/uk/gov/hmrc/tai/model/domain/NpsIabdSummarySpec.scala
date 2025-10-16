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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.NpsIabdSummaryHipReads.iabdsFromTotalLiabilityReads

import scala.util.Random

class NpsIabdSummarySpec extends PlaySpec {

  private val nino: Nino = new Generator(new Random).nextNino

  private def npsIabdSummaries(noOfIabds: Int, iabdType: Int = 1): Seq[JsObject] =
    for {
      _ <- 1 to noOfIabds
    } yield Json.obj(
      "amount"                   -> 1,
      "type"                     -> s"desc ($iabdType)",
      "npsDescription"           -> "desc",
      "employmentSequenceNumber" -> 1,
      "estimatesPaySource"       -> 1
    )

  "iabdsFromTotalLiabilityReads" must {

    "extract iabd summaries from a single category" in {
      val iabdSummaries = npsIabdSummaries(2)
      val json = Json.obj(
        "taxAccountId" -> "id",
        "nino"         -> nino.nino,
        "totalLiabilityDetails" -> Json.obj(
          "nonSavings" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(iabdSummaries)
            )
          )
        )
      )
      val result = json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads)
      result mustBe Seq(NpsIabdSummary(1, Some(1), 1, "desc"), NpsIabdSummary(1, Some(1), 1, "desc"))
    }

    "extract iabd summaries of type 'totalIncome' from six categories of interest" in {
      val json = Json.obj(
        "taxAccountId" -> "id",
        "nino"         -> nino.nino,
        "totalLiabilityDetails" -> Json.obj(
          "nonSavings" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(3))
            )
          ),
          "untaxedInterest" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(4))
            )
          ),
          "bankInterest" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(5))
            )
          ),
          "ukDividends" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(6))
            )
          ),
          "foreignInterest" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(7))
            )
          ),
          "foreignDividends" -> Json.obj(
            "totalIncomeDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(8))
            )
          )
        )
      )
      json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads).size mustBe 33
    }

    "extract iabd summaries of type 'allowReliefDeducts' from six categories of interest" in {
      val json = Json.obj(
        "taxAccountId" -> "id",
        "nino"         -> nino.nino,
        "totalLiabilityDetails" -> Json.obj(
          "nonSavings" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(3))
            )
          ),
          "untaxedInterest" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(4))
            )
          ),
          "bankInterest" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(5))
            )
          ),
          "ukDividends" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(6))
            )
          ),
          "foreignInterest" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(7))
            )
          ),
          "foreignDividends" -> Json.obj(
            "allowanceReliefDeductionsDetails" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(8))
            )
          )
        )
      )
      json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads).size mustBe 33
    }

    "return empty sequence" when {

      "none of the iabd's present declare a 'type'" in {

        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(
                  Seq(
                    Json.obj(
                      "amount"             -> 1,
                      "npsDescription"     -> "desc",
                      "employmentId"       -> 1,
                      "estimatesPaySource" -> 1
                    )
                  )
                )
              )
            )
          )
        )
        json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads) mustBe empty
      }

      "no iabd summaries are present within any of the six categories for each of 'totalIncome' or 'allowanceReliefDeductionsDetails'" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "type" -> 1
              )
            ),
            "untaxedInterest" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray()
              )
            ),
            "nonSavings" -> Json.obj(
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "type" -> 1
              )
            ),
            "untaxedInterest" -> Json.obj(
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray()
              )
            ),
            "basicRateExtensions" -> Json.obj(
              "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(2))
            )
          )
        )
        json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads) mustBe empty
      }
    }

    "extract all expected iabd summaries" when {

      "spread across all six categories for each of 'totalIncome' and 'allowanceReliefDeductionsDetails'" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "totalLiabilityDetails" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(3))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(3))
              )
            ),
            "untaxedInterest" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(4))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(4))
              )
            ),
            "bankInterest" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(5))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(5))
              )
            ),
            "ukDividends" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(6))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(6))
              )
            ),
            "foreignInterest" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(7))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(7))
              )
            ),
            "foreignDividends" -> Json.obj(
              "totalIncomeDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(8))
              ),
              "allowanceReliefDeductionsDetails" -> Json.obj(
                "summaryIABDEstimatedPayDetailsList" -> JsArray(npsIabdSummaries(8))
              )
            )
          )
        )
        json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads).size mustBe 66
      }
    }
  }
}
