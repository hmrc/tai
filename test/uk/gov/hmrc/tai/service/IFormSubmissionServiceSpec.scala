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

import org.mockito.ArgumentMatchers.{any, contains}
import org.mockito.Mockito.{never, times, verify, when}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.util.BaseSpec

import java.nio.file.{Files, Paths}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

class IFormSubmissionServiceSpec extends BaseSpec {
  private val iformSubmissionKey = "testSubmissionKey"
  private val iformId = "testIformId"

  private val person: Person = Person(nino, "", "", None, Address("", "", "", "", ""))

  private val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))

  private val formatter = DateTimeFormatter.ofPattern("YYYYMMdd")

  private def createSUT(
    citizenDetailsConnector: CitizenDetailsConnector,
    pdfService: PdfService,
    fileUploadService: FileUploadService
  ) =
    new IFormSubmissionService(citizenDetailsConnector, pdfService, fileUploadService)

  "IFormSubmissionService" should {
    "create and submit an iform and return an envelope id after submission" in {
      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.successful(pdfBytes))

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.createEnvelope()(hc))
        .thenReturn(Future.successful("1"))
      when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200, responseBody)))

      val sut = createSUT(mockCitizenDetailsConnector, mockPdfService, mockFileUploadService)
      val messageId = sut.uploadIForm(nino, iformSubmissionKey, iformId, (_: Person) => Future(""))(hc).futureValue

      messageId mustBe "1"

      verify(mockFileUploadService, times(1)).uploadFile(
        any(),
        any(),
        contains(s"1-$iformSubmissionKey-${LocalDate.now().format(formatter)}-iform.pdf"),
        any()
      )(any())
      verify(mockFileUploadService, times(1)).uploadFile(
        any(),
        any(),
        contains(s"1-$iformSubmissionKey-${LocalDate.now().format(formatter)}-metadata.xml"),
        any()
      )(any())
    }

    "abort the iform submission when the iform creation fails" in {
      val mockFileUploadService = mock[FileUploadService]

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.failed(new RuntimeException("Error")))

      val sut = createSUT(mockCitizenDetailsConnector, mockPdfService, mockFileUploadService)

      val result = sut.uploadIForm(nino, iformSubmissionKey, iformId, (_: Person) => Future(""))(hc).failed.futureValue

      result mustBe a[RuntimeException]

      verify(mockFileUploadService, never).uploadFile(
        any(),
        any(),
        contains(s"1-$iformSubmissionKey-${LocalDate.now().format(formatter)}-iform.pdf"),
        any()
      )(any())
      verify(mockFileUploadService, never).uploadFile(
        any(),
        any(),
        contains(s"1-$iformSubmissionKey-${LocalDate.now().format(formatter)}-metadata.xml"),
        any()
      )(any())
    }
  }
}
