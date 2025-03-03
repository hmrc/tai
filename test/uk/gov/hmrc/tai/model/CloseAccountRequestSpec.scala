/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*

import java.time.LocalDate

class CloseAccountRequestSpec extends PlaySpec {

  "CloseAccountRequest JSON serialization" must {
    "serialize and deserialize correctly with interestEarnedThisTaxYear" in {
      val request = CloseAccountRequest(
        date = LocalDate.of(2024, 3, 1),
        interestEarnedThisTaxYear = Some(BigDecimal(100.50))
      )

      val json = Json.toJson(request)
      json.as[CloseAccountRequest] mustBe request
    }

    "serialize and deserialize correctly without interestEarnedThisTaxYear" in {
      val request = CloseAccountRequest(
        date = LocalDate.of(2024, 3, 1),
        interestEarnedThisTaxYear = None
      )

      val json = Json.toJson(request)
      json.as[CloseAccountRequest] mustBe request
    }
  }
}
