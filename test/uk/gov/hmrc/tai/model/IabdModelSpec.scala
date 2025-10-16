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

class IabdModelSpec extends PlaySpec {

  "TransactionId JSON serialization" must {
    "serialize and deserialize correctly" in {
      val transaction = TransactionId("12345-oid")
      val json = Json.toJson(transaction)

      json.as[TransactionId] mustBe transaction
    }
  }

  "IabdEditDataRequest JSON serialization" must {
    "serialize and deserialize correctly" in {
      val editRequest = IabdEditDataRequest(version = 1, newAmount = 1000)
      val json = Json.toJson(editRequest)

      json.as[IabdEditDataRequest] mustBe editRequest
    }
  }

  "IabdUpdateResponse JSON serialization" must {
    "serialize and deserialize correctly" in {
      val response = IabdUpdateResponse(TransactionId("12345-oid"), version = 2, iabdType = 100, newAmount = 5000)
      val json = Json.toJson(response)

      json.as[IabdUpdateResponse] mustBe response
    }
  }

  "EmploymentAmount JSON serialization" must {
    "serialize and deserialize correctly" in {
      val employmentAmount = EmploymentAmount(
        name = "Software Engineer",
        description = "Annual Salary",
        employmentId = 1,
        newAmount = 50000,
        oldAmount = 48000,
        worksNumber = Some("W123"),
        jobTitle = Some("Engineer"),
        startDate = Some(LocalDate.of(2023, 4, 1)),
        endDate = None
      )

      val json = Json.toJson(employmentAmount)
      json.as[EmploymentAmount] mustBe employmentAmount
    }
  }

  "IabdUpdateEmploymentsRequest JSON serialization" must {
    "serialize and deserialize correctly" in {
      val request = IabdUpdateEmploymentsRequest(
        version = 3,
        newAmounts = List(
          EmploymentAmount(
            name = "Software Engineer",
            description = "Annual Salary",
            employmentId = 1,
            newAmount = 50000,
            oldAmount = 48000
          )
        )
      )

      val json = Json.toJson(request)
      json.as[IabdUpdateEmploymentsRequest] mustBe request
    }
  }

  "IabdUpdateEmploymentsResponse JSON serialization" must {
    "serialize and deserialize correctly" in {
      val response = IabdUpdateEmploymentsResponse(
        transaction = TransactionId("7890-oid"),
        version = 4,
        iabdType = 200,
        newAmounts = List(
          EmploymentAmount(
            name = "Software Engineer",
            description = "Annual Salary",
            employmentId = 1,
            newAmount = 60000,
            oldAmount = 55000
          )
        )
      )

      val json = Json.toJson(response)
      json.as[IabdUpdateEmploymentsResponse] mustBe response
    }
  }

  "PayAnnualisationRequest JSON serialization" must {
    "serialize and deserialize correctly" in {
      val request = PayAnnualisationRequest(
        amountYearToDate = BigDecimal(25000),
        employmentStartDate = LocalDate.of(2023, 4, 1),
        paymentDate = LocalDate.of(2024, 3, 1)
      )

      val json = Json.toJson(request)
      json.as[PayAnnualisationRequest] mustBe request
    }
  }

  "PayAnnualisationResponse JSON serialization" must {
    "serialize and deserialize correctly" in {
      val response = PayAnnualisationResponse(annualisedAmount = BigDecimal(60000))
      val json = Json.toJson(response)

      json.as[PayAnnualisationResponse] mustBe response
    }
  }

  "IabdUpdateExpensesRequest JSON serialization" must {
    "serialize and deserialize correctly" in {
      val request = IabdUpdateExpensesRequest(version = 5, grossAmount = 1500)
      val json = Json.toJson(request)

      json.as[IabdUpdateExpensesRequest] mustBe request
    }
  }
}
