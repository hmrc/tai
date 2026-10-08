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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.{inOrder as mockitoInOrder, never, reset, verify, verifyNoInteractions, when}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{DmsSubmissionConnector, UpscanConnector}
import uk.gov.hmrc.tai.model.dms.{DmsSubmissionMetadata, DmsSubmissionResponse}
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.upscan.*
import uk.gov.hmrc.tai.repositories.UpscanSubmissionRepository
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant
import java.util.UUID
import scala.concurrent.Future

class UpscanSubmissionServiceSpec extends BaseSpec {

  private val mockUpscanConnector = mock[UpscanConnector]
  private val mockDmsSubmissionConnector = mock[DmsSubmissionConnector]
  private val mockRepository = mock[UpscanSubmissionRepository]
  private val mockAuditor = mock[Auditor]

  private val sut =
    new UpscanSubmissionService(mockUpscanConnector, mockDmsSubmissionConnector, mockRepository, mockAuditor)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUpscanConnector, mockDmsSubmissionConnector, mockRepository, mockAuditor)
  }

  private val submissionId = "submission-1"
  private val downloadUrl = "https://upscan.example.com/bucket/file.pdf?signature=abc"
  private val checksum = "a142ed16d596494528e264ffdd5bfbd1188243e0ed1afc8768bcd5d76eb9c4f1"

  private val uploadRequest = UpscanUploadRequest("https://upscan.example.com/upload", Map("key" -> "ref-1"))
  private val initiateResponse = UpscanInitiateResponse("ref-1", uploadRequest)

  private def readyCallback(
    reference: String = "ref-1",
    url: Option[String] = Some(downloadUrl),
    details: Option[UpscanUploadDetails] =
      Some(UpscanUploadDetails("file.pdf", "application/pdf", "2026-10-05T12:00:00Z", checksum, 100L))
  ) = UpscanCallback(reference, UpscanFileStatus.Ready, url, details)

  private val pdfBytes = "pdf".getBytes
  private val dmsMetadata =
    DmsSubmissionMetadata("AB123456", "TES1", "TAI", "PSA-DFS TES", "PSA", Instant.parse("2026-10-05T12:00:00Z"))

  private def submissionWith(statuses: (String, String)*) =
    UpscanSubmission(
      submissionId,
      statuses.map { case (reference, status) =>
        UpscanUploadedFile(
          reference,
          s"$reference-name",
          status,
          if (status == UpscanFileStatus.Ready) Some(downloadUrl) else None
        )
      },
      Some(dmsMetadata)
    )

  private def stubDms(): Unit = {
    when(mockUpscanConnector.download(any())(any())).thenReturn(Future.successful(pdfBytes))
    when(mockDmsSubmissionConnector.submit(any(), any(), any())(any()))
      .thenReturn(Future.successful(DmsSubmissionResponse("dms-1")))
  }

  private def stubUpdateFileStatus(result: Option[UpscanSubmission]): Unit =
    when(mockRepository.updateFileStatus(any(), any(), any(), any())).thenReturn(Future.successful(result))

  "initiateSubmission" must {

    "create a tracking record and return its id" in {
      when(mockRepository.create(any(), any(), any())).thenReturn(Future.successful(UpscanSubmission(submissionId)))

      val id = sut.initiateSubmission(dmsMetadata).futureValue

      UUID.fromString(id).toString mustBe id
      verify(mockRepository).create(meq(id), meq(Seq.empty), meq(Some(dmsMetadata)))
    }
  }

  "uploadFile" must {

    "initiate with Upscan, record the reference against the submission, then upload the bytes" in {
      val bytes = "file-content".getBytes
      when(mockUpscanConnector.initiate(any())).thenReturn(Future.successful(initiateResponse))
      when(mockRepository.addFile(any(), any())).thenReturn(Future.successful(None))
      when(mockUpscanConnector.upload(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(204, "")))

      val response = sut.uploadFile(bytes, submissionId, "file.pdf", MimeContentType.ApplicationPdf).futureValue

      response.status mustBe 204

      val order = mockitoInOrder(mockUpscanConnector, mockRepository)
      order.verify(mockUpscanConnector).initiate(any())
      order.verify(mockRepository).addFile(meq(submissionId), meq(UpscanUploadedFile("ref-1", "file.pdf")))
      order
        .verify(mockUpscanConnector)
        .upload(meq(uploadRequest), meq(bytes), meq("file.pdf"), meq(MimeContentType.ApplicationPdf))(any())
    }

    "fail and not touch the repository when Upscan initiate fails" in {
      when(mockUpscanConnector.initiate(any())).thenReturn(Future.failed(new RuntimeException("initiate failed")))

      val result = sut
        .uploadFile(Array.emptyByteArray, submissionId, "file.pdf", MimeContentType.ApplicationPdf)
        .failed
        .futureValue

      result.getMessage mustBe "initiate failed"
      verifyNoInteractions(mockRepository)
    }

    "fail when the upload to Upscan fails" in {
      when(mockUpscanConnector.initiate(any())).thenReturn(Future.successful(initiateResponse))
      when(mockRepository.addFile(any(), any())).thenReturn(Future.successful(None))
      when(mockUpscanConnector.upload(any(), any(), any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("upload failed")))

      val result = sut
        .uploadFile(Array.emptyByteArray, submissionId, "file.pdf", MimeContentType.ApplicationPdf)
        .failed
        .futureValue

      result.getMessage mustBe "upload failed"
    }
  }

  "handleCallback" when {

    "the file is READY" must {

      "send it to dms-submission and audit success once every file is READY" in {
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Ready, "ref-2" -> UpscanFileStatus.Ready)))
        stubDms()

        sut.handleCallback(readyCallback()).futureValue

        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Ready),
          meq(Some(downloadUrl)),
          meq(None)
        )
        verify(mockUpscanConnector).download(meq(downloadUrl))(any())
        verify(mockDmsSubmissionConnector).submit(meq(pdfBytes), meq("ref-1-name"), meq(dmsMetadata))(any())
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadSuccess"),
          meq(
            Map(
              "envelopId"       -> submissionId,
              "status"          -> "READY",
              "file-Id"         -> "ref-1,ref-2",
              "dmsSubmissionId" -> "dms-1"
            )
          )
        )(any())
      }

      "not send anything to dms-submission while other files are still outstanding" in {
        stubUpdateFileStatus(
          Some(submissionWith("ref-1" -> UpscanFileStatus.Ready, "ref-2" -> UpscanFileStatus.Initiated))
        )

        sut.handleCallback(readyCallback()).futureValue

        verifyNoInteractions(mockDmsSubmissionConnector)
        verify(mockAuditor, never).sendDataEvent(any(), any())(any())
      }

      "record a failure and not audit success when the file cannot be downloaded from Upscan" in {
        val reason = "Sending the file to DMS failed: download failed"
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Ready)))
        when(mockUpscanConnector.download(any())(any()))
          .thenReturn(Future.failed(new RuntimeException("download failed")))

        sut.handleCallback(readyCallback()).futureValue

        verifyNoInteractions(mockDmsSubmissionConnector)
        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Failed),
          meq(None),
          meq(Some(reason))
        )
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadFailure"),
          meq(Map("reference" -> "ref-1", "status" -> "FAILED", "reason" -> reason))
        )(any())
        verify(mockAuditor, never).sendDataEvent(meq("FileUploadSuccess"), any())(any())
      }

      "record a failure and not audit success when dms-submission rejects the file" in {
        val reason = "Sending the file to DMS failed: boom"
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Ready)))
        when(mockUpscanConnector.download(any())(any())).thenReturn(Future.successful(pdfBytes))
        when(mockDmsSubmissionConnector.submit(any(), any(), any())(any()))
          .thenReturn(Future.failed(new RuntimeException("boom")))

        sut.handleCallback(readyCallback()).futureValue

        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Failed),
          meq(None),
          meq(Some(reason))
        )
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadFailure"),
          meq(Map("reference" -> "ref-1", "status" -> "FAILED", "reason" -> reason))
        )(any())
        verify(mockAuditor, never).sendDataEvent(meq("FileUploadSuccess"), any())(any())
      }

      "audit success without calling dms-submission when the submission has no DMS metadata" in {
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Ready).copy(dmsMetadata = None)))

        sut.handleCallback(readyCallback()).futureValue

        verifyNoInteractions(mockDmsSubmissionConnector)
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadSuccess"),
          meq(Map("envelopId" -> submissionId, "status" -> "READY", "file-Id" -> "ref-1", "dmsSubmissionId" -> ""))
        )(any())
      }

      "do nothing further when the reference is not recognised" in {
        stubUpdateFileStatus(None)

        sut.handleCallback(readyCallback(reference = "unknown")).futureValue

        verifyNoInteractions(mockDmsSubmissionConnector)
        verifyNoInteractions(mockAuditor)
      }

      "record a failure and not audit success when the callback has no download url" in {
        val reason = "Upscan READY callback was missing downloadUrl, so the file could not be sent to DMS"
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Ready, "ref-2" -> UpscanFileStatus.Ready)))

        sut.handleCallback(readyCallback(url = None, details = None)).futureValue

        verifyNoInteractions(mockDmsSubmissionConnector)
        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Failed),
          meq(None),
          meq(Some(reason))
        )
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadFailure"),
          meq(Map("reference" -> "ref-1", "status" -> "FAILED", "reason" -> reason))
        )(any())
        verify(mockAuditor, never).sendDataEvent(meq("FileUploadSuccess"), any())(any())
      }
    }

    "the file has FAILED" must {

      "record the failure with its reason, audit it and not transfer anything" in {
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Failed)))
        val callback = UpscanCallback(
          reference = "ref-1",
          fileStatus = UpscanFileStatus.Failed,
          failureDetails = Some(UpscanFailureDetails("QUARANTINE", "virus found"))
        )

        sut.handleCallback(callback).futureValue

        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Failed),
          meq(None),
          meq(Some("QUARANTINE: virus found"))
        )
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadFailure"),
          meq(Map("reference" -> "ref-1", "status" -> "FAILED", "reason" -> "QUARANTINE: virus found"))
        )(any())
        verifyNoInteractions(mockDmsSubmissionConnector)
      }

      "cope with a callback that has no failure details" in {
        stubUpdateFileStatus(Some(submissionWith("ref-1" -> UpscanFileStatus.Failed)))

        sut.handleCallback(UpscanCallback("ref-1", UpscanFileStatus.Failed)).futureValue

        verify(mockRepository).updateFileStatus(
          meq("ref-1"),
          meq(UpscanFileStatus.Failed),
          meq(None),
          meq(None)
        )
        verify(mockAuditor).sendDataEvent(
          meq("FileUploadFailure"),
          meq(Map("reference" -> "ref-1", "status" -> "FAILED", "reason" -> ""))
        )(any())
      }
    }

    "the file status is not recognised" must {

      "ignore the callback" in {
        sut.handleCallback(UpscanCallback("ref-1", "SOMETHING_ELSE")).futureValue

        verifyNoInteractions(mockRepository)
        verifyNoInteractions(mockDmsSubmissionConnector)
        verifyNoInteractions(mockAuditor)
      }
    }
  }
}
