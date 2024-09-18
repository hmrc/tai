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
import uk.gov.hmrc.tai.model.domain.NpsIabdSummarySquidReads.iabdsFromTotalLiabilityReads

import scala.util.Random

class NpsIabdSummarySpec extends PlaySpec {

  "iabdsFromTotalLiabilityReads" must {

    "extract iabd summaries from a single category" in {
      val iabdSummaries = npsIabdSummaries(2)
      val json = Json.obj(
        "taxAccountId" -> "id",
        "nino"         -> nino.nino,
        "totalLiability" -> Json.obj(
          "nonSavings" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(iabdSummaries)
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
        "totalLiability" -> Json.obj(
          "nonSavings" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(3))
            )
          ),
          "untaxedInterest" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(4))
            )
          ),
          "bankInterest" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(5))
            )
          ),
          "ukDividends" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(6))
            )
          ),
          "foreignInterest" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(7))
            )
          ),
          "foreignDividends" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(8))
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
        "totalLiability" -> Json.obj(
          "nonSavings" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(3))
            )
          ),
          "untaxedInterest" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(4))
            )
          ),
          "bankInterest" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(5))
            )
          ),
          "ukDividends" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(6))
            )
          ),
          "foreignInterest" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(7))
            )
          ),
          "foreignDividends" -> Json.obj(
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(8))
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
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(
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

      "no iabd summaries are present within any of the six categories for each of 'totalIncome' or 'allowReliefDeducts'" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "type" -> 1
              )
            ),
            "untaxedInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray()
              )
            ),
            "nonSavings" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "type" -> 1
              )
            ),
            "untaxedInterest" -> Json.obj(
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray()
              )
            ),
            "basicRateExtensions" -> Json.obj(
              "iabdSummaries" -> JsArray(npsIabdSummaries(2))
            )
          )
        )
        json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads) mustBe empty
      }
    }

    "extract all expected iabd summaries" when {

      "spread across all six categories for each of 'totalIncome' and 'allowReliefDeducts'" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "totalLiability" -> Json.obj(
            "nonSavings" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(3))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(3))
              )
            ),
            "untaxedInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(4))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(4))
              )
            ),
            "bankInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(5))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(5))
              )
            ),
            "ukDividends" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(6))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(6))
              )
            ),
            "foreignInterest" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(7))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(7))
              )
            ),
            "foreignDividends" -> Json.obj(
              "totalIncome" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(8))
              ),
              "allowReliefDeducts" -> Json.obj(
                "iabdSummaries" -> JsArray(npsIabdSummaries(8))
              )
            )
          )
        )
        json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads).size mustBe 66
      }
    }
  }
  private val nino: Nino = new Generator(new Random).nextNino

  private def npsIabdSummaries(noOfIabds: Int, iabdType: Int = 1): Seq[JsObject] =
    for {
      _ <- 1 to noOfIabds
    } yield Json.obj(
      "amount"             -> 1,
      "type"               -> iabdType,
      "npsDescription"     -> "desc",
      "employmentId"       -> 1,
      "estimatesPaySource" -> 1
    )
}
