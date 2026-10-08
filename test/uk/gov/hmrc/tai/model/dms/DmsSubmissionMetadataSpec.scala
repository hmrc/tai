/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.dms

import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.{Instant, LocalDateTime}

class DmsSubmissionMetadataSpec extends BaseSpec {

  "DmsSubmissionMetadata" must {

    "be built from a PdfSubmission, converting the London receipt time to an instant" in {
      val summer = PdfSubmission("AB123456", "TES1", 2).copy(hmrcReceivedAt = LocalDateTime.of(2026, 7, 1, 12, 0))
      val winter = PdfSubmission("AB123456", "TES1", 2).copy(hmrcReceivedAt = LocalDateTime.of(2026, 12, 1, 12, 0))

      val summerMetadata = DmsSubmissionMetadata.from(summer)

      summerMetadata.customerId mustBe "AB123456"
      summerMetadata.formId mustBe "TES1"
      summerMetadata.source mustBe "TAI"
      summerMetadata.classificationType mustBe "PSA-DFS TES"
      summerMetadata.businessArea mustBe "PSA"
      summerMetadata.timeOfReceipt mustBe Instant.parse("2026-07-01T11:00:00Z")
      DmsSubmissionMetadata.from(winter).timeOfReceipt mustBe Instant.parse("2026-12-01T12:00:00Z")
    }

    "round trip through JSON" in {
      val metadata =
        DmsSubmissionMetadata("AB123456", "TES1", "TAI", "PSA-DFS TES", "PSA", Instant.parse("2026-10-05T12:00:00Z"))

      Json.toJson(metadata).as[DmsSubmissionMetadata] mustBe metadata
    }
  }

  "DmsCallback" must {

    "read a Processed callback ignoring the object summary" in {
      val json = Json.obj("id" -> "dms-1", "status" -> "Processed", "objectSummary" -> Json.obj("location" -> "x"))

      json.as[DmsCallback] mustBe DmsCallback("dms-1", "Processed")
    }

    "read a Failed callback" in {
      val json = Json.obj("id" -> "dms-1", "status" -> "Failed", "failureReason" -> "bad", "failureType" -> "Unknown")

      json.as[DmsCallback] mustBe DmsCallback("dms-1", "Failed", Some("bad"), Some("Unknown"))
    }
  }

  "DmsSubmissionResponse" must {

    "read the id" in {
      Json.obj("id" -> "dms-1").as[DmsSubmissionResponse] mustBe DmsSubmissionResponse("dms-1")
    }
  }
}
