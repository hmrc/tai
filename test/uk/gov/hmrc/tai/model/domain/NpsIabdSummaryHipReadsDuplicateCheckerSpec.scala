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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsPath, JsResultException, JsonValidationError}
import uk.gov.hmrc.tai.model.domain.NpsIabdSummaryHipReads._

class NpsIabdSummaryHipReadsDuplicateCheckerSpec extends PlaySpec {

  "checkForDuplicates" must {
    "not throw an exception" when {
      "there are no duplicates in the provided items" in {
        val items = Seq(
          NpsIabdSummary(27, Some(1), BigDecimal(1000), "Description 1"),
          NpsIabdSummary(28, Some(2), BigDecimal(2000), "Description 2"),
          NpsIabdSummary(29, Some(3), BigDecimal(3000), "Description 3")
        )

        checkForDuplicates[NpsIabdSummary](items, item => (item.employmentId, item.componentType))
      }
    }

    "throw a JsResultException" when {
      "there are duplicate items with the same employmentId and componentType" in {
        val items = Seq(
          NpsIabdSummary(27, Some(1), BigDecimal(1000), "Description 1"),
          NpsIabdSummary(27, Some(1), BigDecimal(1500), "Description 1 - Duplicate"),
          NpsIabdSummary(28, Some(2), BigDecimal(2000), "Description 2"),
          NpsIabdSummary(28, Some(2), BigDecimal(2500), "Description 2 - Duplicate")
        )

        val exception = the[JsResultException] thrownBy {
          checkForDuplicates[NpsIabdSummary](items, item => (item.employmentId, item.componentType))
        }

        exception.errors mustBe Seq(
          (
            JsPath,
            Seq(
              JsonValidationError(
                "Duplicate entries found for employmentSequenceNumber: Some(1) and componentType: 27; employmentSequenceNumber: Some(2) and componentType: 28"
              )
            )
          )
        )
      }

      "there are duplicate items with None as employmentId" in {
        val items = Seq(
          NpsIabdSummary(27, None, BigDecimal(1000), "Description 1"),
          NpsIabdSummary(27, None, BigDecimal(1500), "Description 1 - Duplicate"),
          NpsIabdSummary(28, Some(1), BigDecimal(2000), "Description 2")
        )

        val exception = the[JsResultException] thrownBy {
          checkForDuplicates[NpsIabdSummary](items, item => (item.employmentId, item.componentType))
        }

        exception.errors mustBe Seq(
          (
            JsPath,
            Seq(JsonValidationError("Duplicate entries found for employmentSequenceNumber: None and componentType: 27"))
          )
        )
      }
    }
  }
}
