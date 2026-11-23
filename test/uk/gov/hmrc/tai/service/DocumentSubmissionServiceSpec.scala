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

import cats.data.EitherT
import cats.instances.future.*
import org.mockito.ArgumentMatchers.{any, contains, eq as meq}
import org.mockito.Mockito.{reset, times, verify, verifyNoInteractions, when}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.model.admin.FeatureFlagNameTai.UpscanSubmissionToggle
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

class DocumentSubmissionServiceSpec extends BaseSpec {

  private val mockFeatureFlagService = mock[FeatureFlagService]
  private val mockFileUploadService = mock[FileUploadService]
  private val mockUpscanSubmissionService = mock[UpscanSubmissionService]

  private val sut =
    new DocumentSubmissionService(mockFeatureFlagService, mockFileUploadService, mockUpscanSubmissionService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService, mockFileUploadService, mockUpscanSubmissionService)
    when(mockFileUploadService.createEnvelope()(any())).thenReturn(Future.successful("env-1"))
    when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
      .thenReturn(Future.successful(HttpResponse(200, "")))
    when(mockUpscanSubmissionService.initiateSubmission(any())(any())).thenReturn(Future.successful("sub-1"))
    when(mockUpscanSubmissionService.uploadFile(any(), any(), any(), any())(any()))
      .thenReturn(Future.successful(HttpResponse(200, "")))
  }

  private val pdf = "pdf".getBytes
  private val pdfSubmission = PdfSubmission("AB123456", "TES1", 2)
  private val today = LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))

  private def toggleIs(enabled: Boolean): Unit =
    when(mockFeatureFlagService.getAsEitherT(meq[FeatureFlagName](UpscanSubmissionToggle)))
      .thenReturn(EitherT.rightT(FeatureFlag(UpscanSubmissionToggle, enabled)))

  "submit" when {

    "the toggle is on" must {

      "send the pdf through Upscan with the DMS metadata and return the submission id" in {
        toggleIs(enabled = true)

        sut.submit(pdf, "AddEmployment", pdfSubmission).futureValue mustBe "sub-1"

        verify(mockUpscanSubmissionService).initiateSubmission(meq(DmsSubmissionMetadata.from(pdfSubmission)))(any())
        verify(mockUpscanSubmissionService, times(1)).uploadFile(
          meq(pdf),
          meq("sub-1"),
          meq(s"sub-1-AddEmployment-$today-iform.pdf"),
          meq(MimeContentType.ApplicationPdf)
        )(any())
        verifyNoInteractions(mockFileUploadService)
      }
    }

    "the toggle is off" must {

      "send the pdf and metadata xml through File Upload Service and return the envelope id" in {
        toggleIs(enabled = false)

        sut.submit(pdf, "EndEmployment", pdfSubmission).futureValue mustBe "env-1"

        verify(mockFileUploadService).createEnvelope()(any())
        verify(mockFileUploadService, times(1)).uploadFile(
          meq(pdf),
          meq("env-1"),
          meq(s"env-1-EndEmployment-$today-iform.pdf"),
          meq(MimeContentType.ApplicationPdf)
        )(any())
        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          meq("env-1"),
          meq(s"env-1-EndEmployment-$today-metadata.xml"),
          meq(MimeContentType.ApplicationXml)
        )(any())
        verifyNoInteractions(mockUpscanSubmissionService)
      }

      "put the submission details in the metadata xml" in {
        toggleIs(enabled = false)

        sut.submit(pdf, "EndEmployment", pdfSubmission).futureValue

        val xml = org.mockito.ArgumentCaptor.forClass(classOf[Array[Byte]])
        verify(mockFileUploadService).uploadFile(
          xml.capture(),
          any(),
          contains("metadata.xml"),
          meq(MimeContentType.ApplicationXml)
        )(any())
        val content = new String(xml.getValue)
        content must include(pdfSubmission.customerId)
        content must include(pdfSubmission.formId)
      }
    }

    "the toggle cannot be read" must {

      "fall back to File Upload Service" in {
        when(mockFeatureFlagService.getAsEitherT(meq[FeatureFlagName](UpscanSubmissionToggle)))
          .thenReturn(EitherT(Future.failed(new RuntimeException("boom"))))

        sut.submit(pdf, "AddEmployment", pdfSubmission).futureValue mustBe "env-1"

        verifyNoInteractions(mockUpscanSubmissionService)
      }
    }

    "the chosen route fails" must {

      "fail with the same error when Upscan fails" in {
        toggleIs(enabled = true)
        when(mockUpscanSubmissionService.initiateSubmission(any())(any()))
          .thenReturn(Future.failed(new RuntimeException("mongo down")))

        sut.submit(pdf, "AddEmployment", pdfSubmission).failed.futureValue.getMessage mustBe "mongo down"
      }

      "fail with the same error when File Upload Service fails" in {
        toggleIs(enabled = false)
        when(mockFileUploadService.createEnvelope()(any()))
          .thenReturn(Future.failed(new RuntimeException("fus down")))

        sut.submit(pdf, "AddEmployment", pdfSubmission).failed.futureValue.getMessage mustBe "fus down"
      }
    }
  }
}
