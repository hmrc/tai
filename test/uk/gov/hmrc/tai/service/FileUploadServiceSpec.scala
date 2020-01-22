/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.{Matchers, Mockito}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.FileUploadConnector
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FileUploadServiceSpec extends PlaySpec with MockitoSugar {

  "FileUploadService" must {

    "able to create envelope" in {
      val mockAuditor = mock[Auditor]

      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.createEnvelope(any()))
        .thenReturn(Future.successful("123"))

      val sut = createSUT(mockFileUploadConnector, mockAuditor)
      val envelopeId = Await.result(sut.createEnvelope(), 5.seconds)

      envelopeId mustBe "123"
    }

    "able to upload the file" in {
      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.uploadFile(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200)))

      val sut = createSUT(mockFileUploadConnector, mock[Auditor])
      val result = Await.result(sut.uploadFile(new Array[Byte](1), "123", fileName, contentType), 5.seconds)

      result.status mustBe 200

      verify(mockFileUploadConnector, Mockito.times(1))
        .uploadFile(any(), Matchers.eq(s"$fileName"), Matchers.eq(contentType), any(), Matchers.eq(s"$fileId"), any())(
          any())
    }

    "able to close the envelope" in {
      val mockFileUploadConnector = mock[FileUploadConnector]
      when(mockFileUploadConnector.closeEnvelope(any())(any()))
        .thenReturn(Future.successful("123"))

      val sut = createSUT(mockFileUploadConnector, mock[Auditor])
      val result = Await.result(sut.closeEnvelope("123"), 5.seconds)

      result mustBe "123"
    }

    "close the envelope" when {
      "files are available" in {
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.closeEnvelope(any())(any()))
          .thenReturn(Future.successful("123"))
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result =
          Await.result(sut.fileUploadCallback(FileUploadCallback("123", "metadata", "AVAILABLE", None)), 5.seconds)

        result mustBe Closed
        verify(mockFileUploadConnector, times(1))
          .closeEnvelope(Matchers.eq("123"))(any())
      }
    }

    "not close the envelope" when {
      "received multiple callback " in {
        val envelopeSummary =
          EnvelopeSummary("123", "CLOSED", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await
          .result(sut.fileUploadCallback(FileUploadCallback("123", "EndEmploymentiform", "AVAILABLE", None)), 5.seconds)

        result mustBe Open
        verify(mockFileUploadConnector, never())
          .closeEnvelope(Matchers.eq("123"))(any())
      }

      "received status other than Available or Error" in {
        val mockFileUploadConnector = mock[FileUploadConnector]

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await
          .result(sut.fileUploadCallback(FileUploadCallback("123", "EndEmploymentiform", "INFECTED", None)), 5.seconds)

        result mustBe Open
        verify(mockFileUploadConnector, never())
          .closeEnvelope(Matchers.eq("123"))(any())
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
        Await.result(sut.fileUploadCallback(details), 5.seconds)

        verify(mockAuditor, times(1))
          .sendDataEvent(Matchers.eq("FileUploadFailure"), any())(any())
      }

      "file upload is success" in {
        val details = FileUploadCallback("123", "11", "AVAILABLE", None)
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))
        when(mockFileUploadConnector.closeEnvelope(any())(any()))
          .thenReturn(Future.successful("123"))

        val mockAuditor = mock[Auditor]
        doNothing()
          .when(mockAuditor)
          .sendDataEvent(any(), any())(any())

        val sut = createSUT(mockFileUploadConnector, mockAuditor)
        Await.result(sut.fileUploadCallback(details), 5.seconds)

        verify(mockAuditor, times(1))
          .sendDataEvent(Matchers.eq("FileUploadSuccess"), any())(any())
      }
    }

  }

  "envelope Status" must {
    "return Open" when {
      "envelope status is CLOSED" in {
        val envelopeSummary =
          EnvelopeSummary("123", "CLOSED", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Open
      }

      "both file are unavailable" in {
        val envelopeSummary = EnvelopeSummary("123", "", Nil)

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Open
      }

      "both file are in progress" in {
        val envelopeSummary =
          EnvelopeSummary("123", "", Seq(EnvelopeFile("123", "PROGRESS"), EnvelopeFile("123", "PROGRESS")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Open
      }

      "envelope is not available" in {
        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Open
      }

      "one file is available and other in progress" in {
        val envelopeSummary =
          EnvelopeSummary("123", "", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "PROGRESS")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Open
      }
    }

    "return Close" when {
      "both file are available" in {
        val envelopeSummary =
          EnvelopeSummary("123", "OPEN", Seq(EnvelopeFile("123", "AVAILABLE"), EnvelopeFile("123", "AVAILABLE")))

        val mockFileUploadConnector = mock[FileUploadConnector]
        when(mockFileUploadConnector.envelope(Matchers.eq("123"))(any()))
          .thenReturn(Future.successful(Some(envelopeSummary)))

        val sut = createSUT(mockFileUploadConnector, mock[Auditor])
        val result = Await.result(sut.envelopeStatus("123"), 5.seconds)

        result mustBe Closed
      }
    }

  }

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private val fileName = "EndEmployment.pdf"
  private val fileId = "EndEmployment"
  private val contentType = MimeContentType.ApplicationPdf

  private def createSUT(fileUploadConnector: FileUploadConnector, Auditor: Auditor) =
    new FileUploadService(fileUploadConnector, Auditor)
}
