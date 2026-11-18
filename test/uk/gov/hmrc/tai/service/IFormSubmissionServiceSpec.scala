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

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{never, times, verify, when}
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.domain.{AddEmployment, Address, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, PdfSubmission}
import uk.gov.hmrc.tai.service.PdfService.EmploymentIFormReportRequest
import uk.gov.hmrc.tai.util.BaseSpec

import java.nio.file.{Files, Paths}
import java.time.LocalDate
import scala.concurrent.Future

class IFormSubmissionServiceSpec extends BaseSpec {
  private val iformSubmissionKey = "testSubmissionKey"
  private val iformId = "testIformId"

  private val person: Person = Person(nino, "", "", None, Address("", "", "", "", ""))

  private val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))

  private def createSUT(
    citizenDetailsConnector: CitizenDetailsConnector,
    pdfService: PdfService,
    documentSubmissionService: DocumentSubmissionService
  ) =
    new IFormSubmissionService(citizenDetailsConnector, pdfService, documentSubmissionService)

  "IFormSubmissionService" should {
    "create and submit an iform and return a submission id after submission" in {
      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.successful(pdfBytes))

      val mockDocumentSubmissionService = mock[DocumentSubmissionService]
      when(mockDocumentSubmissionService.submit(any(), any(), any())(any()))
        .thenReturn(Future.successful("1"))

      val sut = createSUT(mockCitizenDetailsConnector, mockPdfService, mockDocumentSubmissionService)
      val addEmployment =
        AddEmployment("employerName", LocalDate.parse("2018-12-13"), "12345", "Yes", Some("123456789"))
      val templateModel =
        EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, employment = addEmployment)
      val messageId = sut
        .uploadIForm(
          nino,
          iformSubmissionKey,
          iformId,
          (_: Person) => Future(EmploymentIFormReportRequest(templateModel))
        )(hc)
        .futureValue

      messageId mustBe "1"

      val captor = ArgumentCaptor.forClass(classOf[PdfSubmission])
      verify(mockDocumentSubmissionService, times(1))
        .submit(meq(pdfBytes), meq(iformSubmissionKey), captor.capture())(any())
      captor.getValue.customerId mustBe nino.withoutSuffix
      captor.getValue.formId mustBe iformId
    }

    "abort the iform submission when the iform creation fails" in {
      val mockDocumentSubmissionService = mock[DocumentSubmissionService]

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.failed(new RuntimeException("Error")))

      val sut = createSUT(mockCitizenDetailsConnector, mockPdfService, mockDocumentSubmissionService)
      val addEmployment =
        AddEmployment("employerName", LocalDate.parse("2018-12-13"), "12345", "Yes", Some("123456789"))
      val templateModel =
        EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, employment = addEmployment)

      val result = sut
        .uploadIForm(
          nino,
          iformSubmissionKey,
          iformId,
          (_: Person) => Future(EmploymentIFormReportRequest(templateModel))
        )(hc)
        .failed
        .futureValue

      result mustBe a[RuntimeException]

      verify(mockDocumentSubmissionService, never).submit(any(), any(), any())(any())
    }
  }
}
