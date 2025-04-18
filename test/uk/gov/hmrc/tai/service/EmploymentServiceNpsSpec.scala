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
import cats.instances.future.*
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.ArgumentMatchers.{contains, eq as meq}
import org.mockito.Mockito.{doNothing, reset, times, verify, when}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{DefaultEmploymentDetailsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.HodResponse
import uk.gov.hmrc.tai.model.admin.HipToggleEmploymentDetails
import uk.gov.hmrc.tai.model.api.EmploymentCollection.employmentHodNpsReads
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.PersonRepository
import uk.gov.hmrc.tai.util.{BaseSpec, IFormConstants}

import java.nio.file.{Files, Paths}
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.jdk.CollectionConverters.ListHasAsScala

class EmploymentServiceNpsSpec extends BaseSpec {

  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  private val mocEmploymentDetailsConnector = mock[DefaultEmploymentDetailsConnector]
  private val mockRtiConnector = mock[RtiConnector]
  private val mockEmploymentBuilder = mock[EmploymentBuilder]

  private val pdfBytes = Files.readAllBytes(Paths.get("test/resources/sample.pdf"))

  private val person = Person(nino, "", "", None, Address("", "", "", "", ""))

  private def employment = Employment(
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
    false,
    PensionIncome
  )

  private def createSut(
    employmentDetailsConnector: DefaultEmploymentDetailsConnector,
    rtiConnector: RtiConnector,
    employmentBuilder: EmploymentBuilder,
    personRepository: PersonRepository,
    iFormSubmissionService: IFormSubmissionService,
    fileUploadService: FileUploadService,
    pdfService: PdfService,
    auditable: Auditor
  ) =
    new EmploymentService(
      employmentDetailsConnector,
      rtiConnector,
      employmentBuilder,
      personRepository,
      iFormSubmissionService,
      fileUploadService,
      pdfService,
      auditable,
      mockFeatureFlagService
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mocEmploymentDetailsConnector, mockRtiConnector, mockEmploymentBuilder, mockFeatureFlagService)
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleEmploymentDetails))).thenReturn(
      Future.successful(FeatureFlag(HipToggleEmploymentDetails, isEnabled = false))
    )
    ()
  }

  "EmploymentService.employments (HipToggle OFF)" should {
    "return employments for passed nino and year" in {
      val employmentsForYear = Seq(employment)

      val jsonEmployment = Json.parse(
        """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
      )
      when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
        Employments(employmentsForYear, None)
      )
      val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])
      val rtiAnnualAccountsCaptor = ArgumentCaptor.forClass(classOf[Seq[AnnualAccount]])
      val rtiIsRTIExceptionCaptor = ArgumentCaptor.forClass(classOf[Boolean])
      val ninoCaptor = ArgumentCaptor.forClass(classOf[Nino])
      val taxYearCaptor = ArgumentCaptor.forClass(classOf[TaxYear])

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor]
      )
      val employments = sut.employmentsAsEitherT(nino, TaxYear())(HeaderCarrier(), FakeRequest()).value.futureValue

      verify(mocEmploymentDetailsConnector, times(1)).getEmploymentDetailsAsEitherT(any(), any())(any())
      verify(mockEmploymentBuilder, times(1))
        .combineAccountsWithEmployments(
          employmentsCaptor.capture(),
          rtiAnnualAccountsCaptor.capture(),
          rtiIsRTIExceptionCaptor.capture(),
          ninoCaptor.capture(),
          taxYearCaptor.capture()
        )(any())
      val argsEmployments: Seq[Employment] = employmentsCaptor.getAllValues.asScala.toSeq.flatten
      val argsAccounts: Seq[AnnualAccount] = rtiAnnualAccountsCaptor.getValue
      val argsRTIException: Boolean = rtiIsRTIExceptionCaptor.getValue
      val argsNino: Seq[Nino] = ninoCaptor.getAllValues.asScala.toSeq
      val argsTaxYear: Seq[TaxYear] = taxYearCaptor.getAllValues.asScala.toSeq

      argsEmployments mustBe List(jsonEmployment.as[Employment](employmentHodNpsReads))
      argsAccounts mustBe List(AnnualAccount(0, TaxYear(), Available, List(), List()))
      argsRTIException mustBe false
      argsNino mustBe List(nino)
      argsTaxYear mustBe List(TaxYear())

      employments mustBe Right(Employments(employmentsForYear, None))
    }

    "ignore RTI when RTI is down" in {
      val employmentsForYear = Seq(employment.copy(annualAccounts = Seq.empty))

      val jsonEmployment = Json.parse(
        """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
      )
      when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
        EitherT.leftT(UpstreamErrorResponse("Server Error", INTERNAL_SERVER_ERROR))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
        Employments(employmentsForYear, None)
      )
      val employmentsCaptor = ArgumentCaptor.forClass(classOf[Seq[Employment]])
      val rtiAnnualAccountsCaptor = ArgumentCaptor.forClass(classOf[Seq[AnnualAccount]])
      val rtiIsRTIExceptionCaptor = ArgumentCaptor.forClass(classOf[Boolean])
      val ninoCaptor = ArgumentCaptor.forClass(classOf[Nino])
      val taxYearCaptor = ArgumentCaptor.forClass(classOf[TaxYear])

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor]
      )
      val employments = sut.employmentsAsEitherT(nino, TaxYear())(HeaderCarrier(), FakeRequest()).value.futureValue

      verify(mocEmploymentDetailsConnector, times(1)).getEmploymentDetailsAsEitherT(any(), any())(any())
      verify(mockEmploymentBuilder, times(1))
        .combineAccountsWithEmployments(
          employmentsCaptor.capture(),
          rtiAnnualAccountsCaptor.capture(),
          rtiIsRTIExceptionCaptor.capture(),
          ninoCaptor.capture(),
          taxYearCaptor.capture()
        )(any())
      val argsEmployments: Seq[Employment] = employmentsCaptor.getAllValues.asScala.toSeq.flatten
      val argsRTIException: Boolean = rtiIsRTIExceptionCaptor.getValue
      val argsAccounts: Seq[AnnualAccount] = rtiAnnualAccountsCaptor.getValue
      val argsNino: Seq[Nino] = ninoCaptor.getAllValues.asScala.toSeq
      val argsTaxYear: Seq[TaxYear] = taxYearCaptor.getAllValues.asScala.toSeq

      argsEmployments mustBe List(jsonEmployment.as[Employment](employmentHodNpsReads))
      argsAccounts mustBe List.empty
      argsRTIException mustBe true
      argsNino mustBe List(nino)
      argsTaxYear mustBe List(TaxYear())

      employments mustBe Right(Employments(employmentsForYear, None))
    }

  }

  "EmploymentService.employment" should {
    "return employment for passed nino, year and id" in {
      val jsonEmployment = Json.parse(
        """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
      )
      val employmentsForYear = Seq(employment)

      when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
        Employments(employmentsForYear, None)
      )

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor]
      )
      val employments = sut.employmentAsEitherT(nino, 2)(HeaderCarrier(), FakeRequest()).value.futureValue

      employments mustBe Right(Some(employment))
    }

    "return the correct Error Type when the employment doesn't exist" in {
      val jsonEmployment = Json.parse(
        """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
      )
      val employmentsForYear = Seq(employment)

      when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
        Employments(employmentsForYear, None)
      )

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mock[IFormSubmissionService],
        mock[FileUploadService],
        mock[PdfService],
        mock[Auditor]
      )
      val employments = sut.employmentAsEitherT(nino, 5)(HeaderCarrier(), FakeRequest()).value.futureValue

      employments mustBe Right(None)
    }
  }

  "endEmployment" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val endEmployment = EndEmployment(LocalDate.of(2017, 6, 20), "1234", Some("123456789"))

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
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val jsonEmployment = Json.parse(
          """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
        )
        when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
          EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
        )
        when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
          EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
        )
        when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
          Employments(Seq.empty, None)
        )

        val sut = createSut(
          mocEmploymentDetailsConnector,
          mockRtiConnector,
          inject[EmploymentBuilder],
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable
        )

        Await.result(
          sut.endEmployment(nino, 2, endEmployment)(implicitly, FakeRequest()).value,
          5.seconds
        ) mustBe Right("1")

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-EndEmployment-${LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))}-iform.pdf"),
          any()
        )(any())
      }
    }

    "Send end journey audit event for envelope id" in {
      val endEmployment = EndEmployment(LocalDate.of(2017, 6, 20), "1234", Some("123456789"))

      val jsonEmployment = Json.parse(
        """{"employerName": "employerName", "name":"TEST","employmentStatus": 1,"payrollNumber":"12345","startDate":"30/01/2024","taxDistrictNumber":"","payeNumber":"","sequenceNumber":2,"cessationPay":100,"hasPayrolledBenefit":false,"receivingOccupationalPension":false}"""
      )
      when(mocEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(any(), any())(any)).thenReturn(
        EitherT.rightT(HodResponse(Json.arr(jsonEmployment), None))
      )
      when(mockRtiConnector.getPaymentsForYear(any(), any())(any(), any())).thenReturn(
        EitherT.rightT(Seq(AnnualAccount(0, TaxYear(), Available, Nil, Nil)))
      )
      when(mockEmploymentBuilder.combineAccountsWithEmployments(any(), any(), any(), any(), any())(any())).thenReturn(
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
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        inject[EmploymentBuilder],
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable
      )
      Await.result(sut.endEmployment(nino, 2, endEmployment)(implicitly, FakeRequest()).value, 5.seconds)

      verify(mockAuditable, times(1)).sendDataEvent(
        meq("EndEmploymentRequest"),
        meq(Map("nino" -> nino.nino, "envelope Id" -> "111", "end-date" -> "2017-06-20"))
      )(any())
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
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mocEmploymentDetailsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mockPersonRepository,
          mock[IFormSubmissionService],
          mockFileUploadService,
          mockPdfService,
          mockAuditable
        )
        val result = sut.addEmployment(nino, addEmployment).futureValue

        result mustBe "1"

        verify(mockFileUploadService, times(1)).uploadFile(
          any(),
          any(),
          contains(s"1-AddEmployment-${LocalDate.now().format(DateTimeFormatter.ofPattern("YYYYMMdd"))}-iform.pdf"),
          any()
        )(any())
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
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mockPersonRepository,
        mock[IFormSubmissionService],
        mockFileUploadService,
        mockPdfService,
        mockAuditable
      )
      sut.addEmployment(nino, addEmployment).futureValue

      verify(mockAuditable, times(1)).sendDataEvent(
        meq(IFormConstants.AddEmploymentAuditTxnName),
        meq(
          Map(
            "nino"         -> nino.nino,
            "envelope Id"  -> "111",
            "start-date"   -> "2017-08-01",
            "payrollNo"    -> "1234",
            "employerName" -> "testName"
          )
        )
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
            .uploadIForm(meq(nino), meq(IFormConstants.IncorrectEmploymentSubmissionKey), meq("TES1"), any())(any())
        )
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mocEmploymentDetailsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable
        )
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
          .uploadIForm(meq(nino), meq(IFormConstants.IncorrectEmploymentSubmissionKey), meq("TES1"), any())(any())
      )
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable
      )
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
        when(
          mockIFormSubmissionService
            .uploadIForm(meq(nino), meq(IFormConstants.UpdatePreviousYearIncomeSubmissionKey), meq("TES1"), any())(
              any()
            )
        )
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSut(
          mocEmploymentDetailsConnector,
          mockRtiConnector,
          mockEmploymentBuilder,
          mock[PersonRepository],
          mockIFormSubmissionService,
          mock[FileUploadService],
          mock[PdfService],
          mockAuditable
        )
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
          .uploadIForm(meq(nino), meq(IFormConstants.UpdatePreviousYearIncomeSubmissionKey), meq("TES1"), any())(any())
      )
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSut(
        mocEmploymentDetailsConnector,
        mockRtiConnector,
        mockEmploymentBuilder,
        mock[PersonRepository],
        mockIFormSubmissionService,
        mock[FileUploadService],
        mock[PdfService],
        mockAuditable
      )
      sut.updatePreviousYearIncome(nino, TaxYear(2016), employment).futureValue

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.UpdatePreviousYearIncomeAuditTxnName), meq(map))(any())
    }
  }
}
