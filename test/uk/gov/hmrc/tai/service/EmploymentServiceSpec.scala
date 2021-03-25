/*
 * Copyright 2021 HM Revenue & Customs
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

import java.nio.file.{Files, Paths}

import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, contains, eq => meq}
import org.mockito.Mockito.{doNothing, times, verify, when}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{EmploymentRepository, PersonRepository}
import uk.gov.hmrc.tai.util.{BaseSpec, IFormConstants}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class EmploymentServiceSpec extends BaseSpec {

  "EmploymentService" should {
    "return employments for passed nino and year" in {
      val employmentsForYear = Seq(employment)

      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employmentsForYear(any(), any())(any()))
        .thenReturn(Future.successful(Employments(employmentsForYear)))

      val sut = createSut(
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = Await.result(sut.employments(nino, TaxYear())(HeaderCarrier()), 5.seconds)

      employments mustBe employmentsForYear
    }

    "return employment for passed nino, year and id" in {
      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employment(any(), any())(any()))
        .thenReturn(Future.successful(Right(employment)))

      val sut = createSut(
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = Await.result(sut.employment(nino, 2)(HeaderCarrier()), 5.seconds)

      employments mustBe Right(employment)
      verify(mockEmploymentRepository, times(1)).employment(any(), meq(2))(any())
    }

    "return the correct Error Type when the employment doesn't exist" in {
      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employment(any(), any())(any()))
        .thenReturn(Future.successful(Left(EmploymentNotFound)))

      val sut = createSut(
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = Await.result(sut.employment(nino, 5)(HeaderCarrier()), 5.seconds)

      employments mustBe Left(EmploymentNotFound)
    }
  }

  "endEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val endEmployment = EndEmployment(new LocalDate(2017, 6, 20), "1234", Some("123456789"))

        val mockEmploymentRepository = mock[EmploymentRepository]
        when(mockEmploymentRepository.employment(any(), any())(any()))
          .thenReturn(Future.successful(Right(employment)))

        val mockPersonRepository = mock[PersonRepository]
        when(mockPersonRepository.getPerson(any())(any()))
          .thenReturn(Future.successful(person))

        val mockPdfService = mock[PdfService]
        when(mockPdfService.generatePdf(any()))
          .thenReturn(Future.successful(pdfBytes))

        val mockFileUploadService = mock[FileUploadService]
        when(mockFileUploadService.createEnvelope())
          .thenReturn(Future.successful("1"))
        when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mockEmploymentRepository,
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable)

        Await.result(sut.endEmployment(nino, 2, endEmployment), 5 seconds) mustBe "1"

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-EndEmployment-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"),
          any())(any())
      }
    }

    "Send end journey audit event for envelope id" in {
      val endEmployment = EndEmployment(new LocalDate(2017, 6, 20), "1234", Some("123456789"))

      val mockEmploymentRepository = mock[EmploymentRepository]
      when(mockEmploymentRepository.employment(any(), any())(any()))
        .thenReturn(Future.successful(Right(employment)))

      val mockPersonRepository = mock[PersonRepository]
      when(mockPersonRepository.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.successful(pdfBytes))

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.createEnvelope())
        .thenReturn(Future.successful("111"))
      when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mockEmploymentRepository,
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable)
      Await.result(sut.endEmployment(nino, 2, endEmployment), 5 seconds)

      verify(mockAuditable, times(1)).sendDataEvent(
        meq("EndEmploymentRequest"),
        meq(Map("nino" -> nino.nino, "envelope Id" -> "111", "end-date" -> "2017-06-20")))(any())
    }
  }

  "addEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val addEmployment = AddEmployment("testName", new LocalDate(2017, 8, 1), "1234", "Yes", Some("123456789"))

        val mockPersonRepository = mock[PersonRepository]
        when(mockPersonRepository.getPerson(any())(any()))
          .thenReturn(Future.successful(person))

        val mockPdfService = mock[PdfService]
        when(mockPdfService.generatePdf(any()))
          .thenReturn(Future.successful(pdfBytes))

        val mockFileUploadService = mock[FileUploadService]
        when(mockFileUploadService.createEnvelope())
          .thenReturn(Future.successful("1"))
        when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mock[EmploymentRepository],
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable)
        val result = Await.result(sut.addEmployment(nino, addEmployment), 5 seconds)

        result mustBe "1"

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-AddEmployment-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"),
          any())(any())
      }
    }

    "Send add journey audit event for envelope id" in {
      val addEmployment = AddEmployment("testName", new LocalDate(2017, 8, 1), "1234", "Yes", Some("123456789"))

      val mockPersonRepository = mock[PersonRepository]
      when(mockPersonRepository.getPerson(any())(any()))
        .thenReturn(Future.successful(person))

      val mockPdfService = mock[PdfService]
      when(mockPdfService.generatePdf(any()))
        .thenReturn(Future.successful(pdfBytes))

      val mockFileUploadService = mock[FileUploadService]
      when(mockFileUploadService.createEnvelope())
        .thenReturn(Future.successful("111"))
      when(mockFileUploadService.uploadFile(any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HttpResponse(200, "")))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mock[EmploymentRepository],
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable)
      Await.result(sut.addEmployment(nino, addEmployment), 5 seconds)

      verify(mockAuditable, times(1)).sendDataEvent(
        meq(IFormConstants.AddEmploymentAuditTxnName),
        meq(
          Map(
            "nino"         -> nino.nino,
            "envelope Id"  -> "111",
            "start-date"   -> "2017-08-01",
            "payrollNo"    -> "1234",
            "employerName" -> "testName"))
      )(any())
    }
  }

  "incorrectEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val employment = IncorrectEmployment("whatYouToldUs", "No", None)

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(
          mockIFormSubmissionService
            .uploadIForm(meq(nino), meq(IFormConstants.IncorrectEmploymentSubmissionKey), meq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mock[EmploymentRepository],
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable)
        val result = Await.result(sut.incorrectEmployment(nino, 1, employment), 5 seconds)

        result mustBe "1"
      }
    }

    "Send incorrect employment journey audit event" in {
      val employment = IncorrectEmployment("whatYouToldUs", "Yes", Some("1234567"))
      val map = Map(
        "nino"                    -> nino.nino,
        "envelope Id"             -> "1",
        "what-you-told-us"        -> employment.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> employment.telephoneContactAllowed,
        "telephoneNumber"         -> employment.telephoneNumber.getOrElse("")
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(
        mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.IncorrectEmploymentSubmissionKey), meq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mock[EmploymentRepository],
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable)
      Await.result(sut.incorrectEmployment(nino, 1, employment), 5 seconds)

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.IncorrectEmploymentAuditTxnName), meq(map))(any())
    }
  }

  "updatePreviousYearIncome" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val employment = IncorrectEmployment("whatYouToldUs", "No", None)

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.UpdatePreviousYearIncomeSubmissionKey), meq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mock[EmploymentRepository],
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable)
        val result = Await.result(sut.updatePreviousYearIncome(nino, TaxYear(2016), employment), 5 seconds)

        result mustBe "1"
      }
    }

    "Send incorrect employment journey audit event" in {
      val employment = IncorrectEmployment("whatYouToldUs", "Yes", Some("1234567"))
      val map = Map(
        "nino"                    -> nino.nino,
        "envelope Id"             -> "1",
        "taxYear"                 -> "2016",
        "what-you-told-us"        -> employment.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> employment.telephoneContactAllowed,
        "telephoneNumber"         -> employment.telephoneNumber.getOrElse("")
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(
        mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.UpdatePreviousYearIncomeSubmissionKey), meq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mock[EmploymentRepository],
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable)
      Await.result(sut.updatePreviousYearIncome(nino, TaxYear(2016), employment), 5 seconds)

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.UpdatePreviousYearIncomeAuditTxnName), meq(map))(any())
    }
  }

  private val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))

  private val person = Person(nino, "", "", None, Address("", "", "", "", ""))

  private val employment = Employment(
    "TEST",
    Live,
    Some("12345"),
    LocalDate.now(),
    None,
    List(AnnualAccount("", TaxYear(), Available, Nil, Nil)),
    "",
    "",
    2,
    Some(100),
    hasPayrolledBenefit = false,
    receivingOccupationalPension = false
  )

  private def createSut(
    employmentRepository: EmploymentRepository,
    personRepository: PersonRepository,
    iFormSubmissionService: IFormSubmissionService,
    fileUploadService: FileUploadService,
    pdfService: PdfService,
    auditable: Auditor) =
    new EmploymentService(
      employmentRepository,
      personRepository,
      iFormSubmissionService,
      fileUploadService,
      pdfService,
      auditable)
}
