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

package uk.gov.hmrc.tai.model.upscan

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata

import java.time.Instant

class UpscanModelsSpec extends PlaySpec {

  "UpscanCallback" must {

    "read a READY callback from Upscan" in {
      val json = Json.parse("""{
          |  "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
          |  "fileStatus": "READY",
          |  "downloadUrl": "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
          |  "uploadDetails": {
          |    "uploadTimestamp": "2018-04-24T09:30:00Z",
          |    "checksum": "396f101dd52e8b2ace0dcf5ed09b1d1f030e603c",
          |    "fileName": "test.pdf",
          |    "fileMimeType": "application/pdf",
          |    "size": 987
          |  }
          |}""".stripMargin)

      json.as[UpscanCallback] mustBe UpscanCallback(
        reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
        fileStatus = UpscanFileStatus.Ready,
        downloadUrl = Some("https://bucketName.s3.eu-west-2.amazonaws.com?1235676"),
        uploadDetails = Some(
          UpscanUploadDetails(
            fileName = "test.pdf",
            fileMimeType = "application/pdf",
            uploadTimestamp = "2018-04-24T09:30:00Z",
            checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e603c",
            size = 987L
          )
        )
      )
    }

    "read a FAILED callback from Upscan" in {
      val json = Json.parse("""{
          |  "reference": "11370e18-6e24-453e-b45a-76d3e32ea33d",
          |  "fileStatus": "FAILED",
          |  "failureDetails": {
          |    "failureReason": "QUARANTINE",
          |    "message": "This file has a virus"
          |  }
          |}""".stripMargin)

      json.as[UpscanCallback] mustBe UpscanCallback(
        reference = "11370e18-6e24-453e-b45a-76d3e32ea33d",
        fileStatus = UpscanFileStatus.Failed,
        failureDetails = Some(UpscanFailureDetails("QUARANTINE", "This file has a virus"))
      )
    }

    "reject a callback with no reference" in {
      Json.obj("fileStatus" -> "READY").validate[UpscanCallback].isError mustBe true
    }
  }

  "UpscanInitiateResponse" must {

    "read the reference and upload request, including the form fields" in {
      val json = Json.obj(
        "reference"     -> "ref-1",
        "uploadRequest" -> Json.obj(
          "href"   -> "https://bucketName.s3.eu-west-2.amazonaws.com",
          "fields" -> Json.obj("acl" -> "private", "key" -> "ref-1")
        )
      )

      json.as[UpscanInitiateResponse] mustBe UpscanInitiateResponse(
        "ref-1",
        UpscanUploadRequest("https://bucketName.s3.eu-west-2.amazonaws.com", Map("acl" -> "private", "key" -> "ref-1"))
      )
    }

    "round-trip through JSON" in {
      val response = UpscanInitiateResponse("ref-1", UpscanUploadRequest("https://upload", Map("k" -> "v")))

      Json.toJson(response).as[UpscanInitiateResponse] mustBe response
    }
  }

  "UpscanInitiateRequest" must {

    "write only the fields that have a value" in {
      Json.toJson(UpscanInitiateRequest("http://callback", minimumFileSize = Some(1))) mustBe Json.obj(
        "callbackUrl"     -> "http://callback",
        "minimumFileSize" -> 1
      )
    }

    "write every field when all are set" in {
      val request = UpscanInitiateRequest(
        callbackUrl = "http://callback",
        successRedirect = Some("http://success"),
        errorRedirect = Some("http://error"),
        minimumFileSize = Some(1),
        maximumFileSize = Some(10),
        consumingService = Some("tai")
      )

      Json.toJson(request) mustBe Json.obj(
        "callbackUrl"      -> "http://callback",
        "successRedirect"  -> "http://success",
        "errorRedirect"    -> "http://error",
        "minimumFileSize"  -> 1,
        "maximumFileSize"  -> 10,
        "consumingService" -> "tai"
      )
    }
  }

  "UpscanSubmission" must {

    def file(status: String) = UpscanUploadedFile("ref", "file.pdf", status)

    "not be complete when it has no files" in {
      UpscanSubmission("id").isComplete mustBe false
    }

    "be complete when every file is READY" in {
      UpscanSubmission("id", Seq(file(UpscanFileStatus.Ready), file(UpscanFileStatus.Ready))).isComplete mustBe true
    }

    "not be complete while any file is still outstanding" in {
      val submission = UpscanSubmission("id", Seq(file(UpscanFileStatus.Ready), file(UpscanFileStatus.Initiated)))

      submission.isComplete mustBe false
    }

    "report a failure when any file has FAILED, and not be complete" in {
      val submission = UpscanSubmission("id", Seq(file(UpscanFileStatus.Ready), file(UpscanFileStatus.Failed)))

      submission.hasFailure mustBe true
      submission.isComplete mustBe false
    }

    "not report a failure when no file has FAILED" in {
      UpscanSubmission("id", Seq(file(UpscanFileStatus.Initiated))).hasFailure mustBe false
    }

    "round-trip through JSON" in {
      val submission = UpscanSubmission(
        "id",
        Seq(UpscanUploadedFile("ref", "file.pdf", UpscanFileStatus.Ready, Some("https://download"), None)),
        createdAt = Instant.parse("2026-10-05T12:00:00Z")
      )

      Json.toJson(submission).as[UpscanSubmission] mustBe submission
    }

    "round-trip through JSON with DMS metadata" in {
      val metadata =
        DmsSubmissionMetadata("AB123456", "TES1", "TAI", "PSA-DFS TES", "PSA", Instant.parse("2026-10-05T12:00:00Z"))
      val submission = UpscanSubmission(
        "id",
        Seq(UpscanUploadedFile("ref", "file.pdf", UpscanFileStatus.Ready, Some("https://download"), None)),
        Some(metadata),
        Instant.parse("2026-10-05T12:00:00Z")
      )

      Json.toJson(submission).as[UpscanSubmission] mustBe submission
    }
  }
}
