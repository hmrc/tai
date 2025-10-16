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

class IabdUpdateAmountSpec extends PlaySpec {

  "IabdUpdateAmount JSON serialization" must {

    "serialize and deserialize correctly" in {
      val iabdUpdateAmount = IabdUpdateAmount(
        employmentSequenceNumber = Some(123),
        grossAmount = 1000,
        netAmount = Some(800),
        receiptDate = Some("01/01/2024"),
        source = Some(1),
        currentOptimisticLock = Some(2)
      )

      val json = Json.toJson(iabdUpdateAmount)
      json.as[IabdUpdateAmount] mustBe iabdUpdateAmount
    }

    "fail to create an instance when grossAmount is negative" in {
      assertThrows[IllegalArgumentException] {
        IabdUpdateAmount(grossAmount = -100)
      }
    }

    "handle optional fields correctly" in {
      val iabdUpdateAmount = IabdUpdateAmount(
        employmentSequenceNumber = None,
        grossAmount = 5000,
        netAmount = None,
        receiptDate = None,
        source = None,
        currentOptimisticLock = None
      )

      val json = Json.toJson(iabdUpdateAmount)
      json.as[IabdUpdateAmount] mustBe iabdUpdateAmount
    }
  }

  "writesHip transformation" must {

    "add source field with value 'Cutover'" in {
      val iabdUpdateAmount = IabdUpdateAmount(
        employmentSequenceNumber = Some(123),
        grossAmount = 1000,
        netAmount = Some(800),
        receiptDate = Some("01/01/2024"),
        source = Some(1),
        currentOptimisticLock = Some(2)
      )

      val json = Json.toJson(iabdUpdateAmount)(IabdUpdateAmount.writesHip)
      (json \ "source").as[String] mustBe "Cutover"
    }
  }
}
