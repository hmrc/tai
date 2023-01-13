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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.mockito.Mockito
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.FileUploadConnector
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class FileUploadServiceSpec extends BaseSpec {

  "FileUploadService" must {

    "able to create envelope" in {
      val mockAuditor = mock[Auditor]

      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.createEnvelope(any()))
        .thenReturn(Future.successful("123"))

      val sut = createSUT(mockFileUploadConnector, mockAuditor)
      val envelopeId = sut.createEnvelope().futureValue

      envelopeId mustBe "123"
    }

    "able to upload the file" in {
      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.uploadFile(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val sut = createSUT(mockFileUploadConnector, mock[Auditor])
      val result = sut.uploadFile(new Array[Byte](1), "123", fileName, contentType).futureValue

      result.status mustBe 200

      verify(mockFileUploadConnector, Mockito.times(1))
        .uploadFile(any(), meq(s"$fileName"), meq(contentType), any(), meq(s"$fileId"), any())(any())
    }

    "able to close the envelope" in {
      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.closeEnvelope(any())(any()))
        .thenReturn(Future.successful("123"))

      val sut = createSUT(mockFileUploadConnector, mock[Auditor])
      val result = sut.closeEnvelope("123").futureValue

      result mustBe "123"
    }

    "close the envelope" when {
      "files are available" in {
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.closeEnvelope(any())(any()))
          .thenReturn(Future.successful("123"))
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result =
          sut.fileUploadCallback(FileUploadCallback("123", "metadata", "AVAILABLE", None)).futureValue

        result mustBe Closed
        verify(mockFileUploadConnector, times(1))
          .closeEnvelope(meq("123"))(any())
      }
    }

    "not close the envelope" when {
      "received multiple callback " in {
        val envelopeSummary =
          EnvelopeSummary("123", "CLOSED", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.fileUploadCallback(FileUploadCallback("123", "EndEmploymentiform", "AVAILABLE", None)).futureValue

        result mustBe Open
        verify(mockFileUploadConnector, never())
          .closeEnvelope(meq("123"))(any())
      }

      "received status other than Available or Error" in {
        val mockFileUploadConnector = mock[FileUploadConnector]

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.fileUploadCallback(FileUploadCallback("123", "EndEmploymentiform", "INFECTED", None)).futureValue

        result mustBe Open
        verify(mockFileUploadConnector, never())
          .closeEnvelope(meq("123"))(any())
      }
    }

    "generate an audit event" when {
      "file upload is failed" in {
        val details = FileUploadCallback("123", "11", "ERROR", Some("VIRUS"))

        val mockAuditor = mock[Auditor]
        doNothing()
          .when(mockAuditor)
          .sendDataEvent(any(), any())(any())

        val sut = createSUT(mock[FileUploadConnector], mockAuditor)
        sut.fileUploadCallback(details).futureValue

        verify(mockAuditor, times(1))
          .sendDataEvent(meq("FileUploadFailure"), any())(any())
      }

      "file upload is success" in {
        val details = FileUploadCallback("123", "11", "AVAILABLE", None)
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))
        when(mockFileUploadConnector.closeEnvelope(any())(any()))
          .thenReturn(Future.successful("123"))

        val mockAuditor = mock[Auditor]
        doNothing()
          .when(mockAuditor)
          .sendDataEvent(any(), any())(any())

        val sut = createSUT(mockFileUploadConnector, mockAuditor)
        sut.fileUploadCallback(details).futureValue

        verify(mockAuditor, times(1))
          .sendDataEvent(meq("FileUploadSuccess"), any())(any())
      }
    }

  }

  "envelope Status" must {
    "return Open" when {
      "envelope status is CLOSED" in {
        val envelopeSummary =
          EnvelopeSummary("123", "CLOSED", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Open
      }

      "both file are unavailable" in {
        val envelopeSummary = EnvelopeSummary("123", "", Nil)

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Open
      }

      "both file are in progress" in {
        val envelopeSummary =
          EnvelopeSummary("123", "", Seq(EnvelopeFile("123", "PROGRESS"), EnvelopeFile("123", "PROGRESS")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Open
      }

      "envelope is not available" in {
        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Open
      }

      "one file is available and other in progress" in {
        val envelopeSummary =
          EnvelopeSummary("123", "", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "PROGRESS")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Open
      }
    }

    "return Close" when {
      "both file are available" in {
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(meq("123")))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = sut.envelopeStatus("123").futureValue

        result mustBe Closed
      }
    }

  }

  private val fileName = "EndEmployment.pdf"
  private val fileId = "EndEmployment"
  private val contentType = MimeContentType.ApplicationPdf

  private def createSUT(fileUploadConnector: FileUploadConnector, Auditor: Auditor) =
    new FileUploadService(fileUploadConnector, Auditor)
}
