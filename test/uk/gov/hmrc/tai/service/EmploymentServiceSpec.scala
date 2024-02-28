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

import cats.data.EitherT

import java.time.LocalDate
import org.mockito.ArgumentMatchers.{any, contains, eq => meq}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{HodResponse, RtiConnector}
import uk.gov.hmrc.tai.connectors.deprecated.NpsConnector
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.error.EmploymentNotFound
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.{EmploymentRepository, PersonRepository}
import uk.gov.hmrc.tai.util.{BaseSpec, IFormConstants}

import java.nio.file.{Files, Paths}
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class EmploymentServiceSpec extends BaseSpec {

  val mockNpsConnector = mock[NpsConnector]
  val mockRtiConnector = mock[RtiConnector]
  val mockEmploymentBuilder = mock[EmploymentBuilder]


  "EmploymentService" should {
    "return employments for passed nino and year" in {
      val employmentsForYear = Seq(employment)

      val mockEmploymentRepository = mock[EmploymentRepository]

      val jsonEmployment = Json.parse("""{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}""")
      when(mockNpsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYearAsEitherT(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any())(any())).thenReturn(
        Employments(Seq.empty, None)
      )

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = sut.employmentsAsEitherT(nino, TaxYear())(HeaderCarrier(), FakeRequest()).value.futureValue

      employments mustBe Right(employmentsForYear)
    }

    "return employment for passed nino, year and id" in {
      val mockEmploymentRepository = mock[EmploymentRepository]

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = sut.employmentAsEitherT(nino, 2)(HeaderCarrier(), FakeRequest()).value.futureValue

      employments mustBe Right(employment)
    }

    "return the correct Error Type when the employment doesn't exist" in {
      val mockEmploymentRepository = mock[EmploymentRepository]

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mockEmploymentRepository,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor])
      val employments = sut.employmentAsEitherT(nino, 5)(HeaderCarrier(), FakeRequest()).value.futureValue

      employments mustBe Left(EmploymentNotFound)
    }
  }

  "endEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val endEmployment = EndEmployment(LocalDate.of(2017, 6, 20), "1234", Some("123456789"))

        val mockEmploymentRepository = mock[EmploymentRepository]

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
          .thenReturn(Future.successful(HttpResponse(200, responseBody)))

        val mockAuditable = mock[Auditor]
        doNothing
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val jsonEmployment = Json.parse("""{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}""")
        when(mockNpsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
          EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
        )
        when(mockRtiConnector.getPaymentsForYearAsEitherT(any(), any())(any(), any())).thenReturn(
          EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
        )
        when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any())(any())).thenReturn(
          Employments(Seq.empty, None)
        )

        val sut = createSut(
          mockNpsConnector,
          mockRtiConnector,
          inject[EmploymentBuilder],
          mockEmploymentRepository,
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable)

        Await.result(sut.endEmployment(nino, 2, endEmployment)(implicitly, FakeRequest()), 5.seconds) mustBe "1"

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-EndEmployment-${LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))}-iform.pdf"),
          any())(any())
      }
    }

    "Send end journey audit event for envelope id" in {
      val endEmployment = EndEmployment(LocalDate.of(2017, 6, 20), "1234", Some("123456789"))

      val mockEmploymentRepository = mock[EmploymentRepository]

      val jsonEmployment = Json.parse("""{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}""")
      when(mockNpsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYearAsEitherT(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any())(any())).thenReturn(
        Employments(Seq.empty, None)
      )

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
        .thenReturn(Future.successful(HttpResponse(200, responseBody)))

      val mockAuditable = mock[Auditor]
      doNothing
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        inject[EmploymentBuilder],
        mockEmploymentRepository,
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable)
      Await.result(sut.endEmployment(nino, 2, endEmployment)(implicitly, FakeRequest()), 5.seconds)

      verify(mockAuditable, times(1)).sendDataEvent(
        meq("EndEmploymentRequest"),
        meq(Map("nino" -> nino.nino, "envelope Id" -> "111", "end-date" -> "2017-06-20")))(any())
    }
  }

  "addEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val addEmployment = AddEmployment("testName", LocalDate.of(2017, 8, 1), "1234", "Yes", Some("123456789"))

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
          .thenReturn(Future.successful(HttpResponse(200, responseBody)))

        val mockAuditable = mock[Auditor]
        doNothing
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mockNpsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mock[EmploymentRepository],
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable)
        val result = sut.addEmployment(nino, addEmployment).futureValue

        result mustBe "1"

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-AddEmployment-${LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))}-iform.pdf"),
          any())(any())
      }
    }

    "Send add journey audit event for envelope id" in {
      val addEmployment = AddEmployment("testName", LocalDate.of(2017, 8, 1), "1234", "Yes", Some("123456789"))

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
        .thenReturn(Future.successful(HttpResponse(200, responseBody)))

      val mockAuditable = mock[Auditor]
      doNothing
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[EmploymentRepository],
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable)
      sut.addEmployment(nino, addEmployment).futureValue

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
        doNothing
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mockNpsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mock[EmploymentRepository],
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable)
        val result = sut.incorrectEmployment(nino, 1, employment)(implicitly, FakeRequest()).futureValue

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
      doNothing
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[EmploymentRepository],
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable)
      sut.incorrectEmployment(nino, 1, employment)(implicitly, FakeRequest()).futureValue

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
        doNothing
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mockNpsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mock[EmploymentRepository],
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable)
        val result = sut.updatePreviousYearIncome(nino, TaxYear(2016), employment).futureValue

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
      doNothing
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mockNpsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[EmploymentRepository],
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable)
      sut.updatePreviousYearIncome(nino, TaxYear(2016), employment).futureValue

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
    List(AnnualAccount(0, TaxYear(), Available, Nil, Nil)),
    "",
    "",
    2,
    Some(100),
    false,
    false)

  private def createSut(
                         npsConnector: NpsConnector,
                         rtiConnector: RtiConnector,
                         employmentBuilder: EmploymentBuilder,
    employmentRepository: EmploymentRepository,
    personRepository: PersonRepository,
    iFormSubmissionService: IFormSubmissionService,
    fileUploadService: FileUploadService,
    pdfService: PdfService,
    auditable: Auditor) =
    new EmploymentService(
      npsConnector,
      rtiConnector,
      employmentBuilder,
      employmentRepository,
      personRepository,
      iFormSubmissionService,
      fileUploadService,
      pdfService,
      auditable)
}
