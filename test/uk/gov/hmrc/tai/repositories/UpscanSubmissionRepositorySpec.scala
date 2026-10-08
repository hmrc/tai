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

package uk.gov.hmrc.tai.repositories

import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.tai.config.UpscanConfig
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.upscan.{UpscanFileStatus, UpscanSubmission, UpscanUploadedFile}
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant

class UpscanSubmissionRepositorySpec
    extends BaseSpec with DefaultPlayMongoRepositorySupport[UpscanSubmission] with IntegrationPatience {

  override val repository: UpscanSubmissionRepository =
    new UpscanSubmissionRepository(mongoComponent, inject[UpscanConfig])

  private val submissionId = "submission-1"
  private val pdf = UpscanUploadedFile("ref-pdf", "file.pdf")
  private val xml = UpscanUploadedFile("ref-xml", "metadata.xml")

  "create" must {

    "return a new submission with no files" in {
      val result = repository.create(submissionId).futureValue

      result.submissionId mustBe submissionId
      result.files mustBe Seq.empty
    }
  }

  "create with DMS metadata" must {

    "persist the metadata so it can be read back after the file is added" in {
      val metadata =
        DmsSubmissionMetadata("AB123456", "TES1", "TAI", "PSA-DFS TES", "PSA", Instant.parse("2026-10-05T12:00:00Z"))

      repository.create(submissionId, Seq.empty, Some(metadata)).futureValue.dmsMetadata mustBe Some(metadata)

      repository.addFile(submissionId, pdf).futureValue.flatMap(_.dmsMetadata) mustBe Some(metadata)
      repository
        .updateFileStatus(pdf.reference, UpscanFileStatus.Ready, Some("https://download"))
        .futureValue
        .flatMap(_.dmsMetadata) mustBe Some(metadata)
    }
  }

  "addFile" must {

    "append each file to the submission and persist them" in {
      repository.create(submissionId).futureValue

      repository.addFile(submissionId, pdf).futureValue.map(_.files) mustBe Some(Seq(pdf))
      repository.addFile(submissionId, xml).futureValue.map(_.files) mustBe Some(Seq(pdf, xml))

      repository
        .updateFileStatus(pdf.reference, UpscanFileStatus.Initiated)
        .futureValue
        .map(_.files.map(_.reference)) mustBe Some(Seq("ref-pdf", "ref-xml"))
    }

    "return None when the submission does not exist" in {
      repository.addFile("unknown", pdf).futureValue mustBe None
    }
  }

  "updateFileStatus" must {

    "update only the file with the matching reference" in {
      repository.create(submissionId, Seq(pdf, xml)).futureValue

      val result = repository
        .updateFileStatus(pdf.reference, UpscanFileStatus.Ready, downloadUrl = Some("https://download/pdf"))
        .futureValue
        .value

      result.files mustBe Seq(
        pdf.copy(status = UpscanFileStatus.Ready, downloadUrl = Some("https://download/pdf")),
        xml
      )
      result.isComplete mustBe false
    }

    "make the submission complete once every file is READY" in {
      repository.create(submissionId, Seq(pdf, xml)).futureValue

      repository.updateFileStatus(pdf.reference, UpscanFileStatus.Ready).futureValue
      val result = repository.updateFileStatus(xml.reference, UpscanFileStatus.Ready).futureValue.value

      result.isComplete mustBe true
    }

    "record the reason when a file has FAILED" in {
      repository.create(submissionId, Seq(pdf)).futureValue

      val result = repository
        .updateFileStatus(pdf.reference, UpscanFileStatus.Failed, failureReason = Some("QUARANTINE: virus"))
        .futureValue
        .value

      result.files.head.status mustBe UpscanFileStatus.Failed
      result.files.head.failureReason mustBe Some("QUARANTINE: virus")
      result.hasFailure mustBe true
    }

    "keep an existing download url when a later update does not supply one" in {
      repository.create(submissionId, Seq(pdf)).futureValue
      repository
        .updateFileStatus(pdf.reference, UpscanFileStatus.Ready, downloadUrl = Some("https://download/pdf"))
        .futureValue

      val result = repository.updateFileStatus(pdf.reference, UpscanFileStatus.Ready).futureValue.value

      result.files.head.downloadUrl mustBe Some("https://download/pdf")
    }

    "return None when no submission contains the reference" in {
      repository.updateFileStatus("unknown", UpscanFileStatus.Ready).futureValue mustBe None
    }
  }
}
