/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service.benefits

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, UnprocessableEntityException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.CompanyCarConnector
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.benefits.{GenericBenefit, _}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.CompanyCarBenefitRepository
import uk.gov.hmrc.tai.service._
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class BenefitsServiceSpec extends PlaySpec with MockitoSugar {
  "companyCarBenefit" must {
    "return Nil" when {
      "the repository returned Nil" in {
        val nino = randomNino

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(Seq.empty[CodingComponent]))


        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        Await.result(sut.companyCarBenefits(nino)(hc), 5 seconds) mustBe Seq.empty[CompanyCarBenefit]
      }
    }

    "return sequence of companyCarBenefit" when {
      "the repository returned sequence of companyCarBenefit with no fuel benefit" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(12, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = false,
          Some(LocalDate.parse("2014-06-10")), None, None))))

        val taxFreeAmountComponents =
            Seq(
              CodingComponent(CarBenefit, Some(12), 200, "some other description")
            )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector],mockCodingComponentService)
        Await.result(sut.companyCarBenefits(nino)(hc), 5 seconds) mustBe  result

      }

      "the repository returned sequence of companyCarBenefit with a fuel benefit" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(12, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
          Some(LocalDate.parse("2014-06-10")), Some(LocalDate.parse("2014-06-10")), None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector], mockCodingComponentService)
        Await.result(sut.companyCarBenefits(nino)(hc), 5 seconds) mustBe result
      }
    }

    "return the first matching companyCarBenefit for a given employment sequence number" when {
      "the repository returned sequence of companyCarBenefit with no matching employment sequence number" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(12, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
          Some(LocalDate.parse("2014-06-10")), None, None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector],mockCodingComponentService)
        Await.result(sut.companyCarBenefitForEmployment(nino, 11)(hc), 5 seconds) mustBe None
      }

      "the repository returned sequence of companyCarBenefit with one matching employment sequence number" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(12, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
          Some(LocalDate.parse("2014-06-10")), None, None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector],mockCodingComponentService)
        Await.result(sut.companyCarBenefitForEmployment(nino, 12)(hc), 5 seconds) mustBe Some(result.head)
      }

      "the repository returned sequence of multiple companyCarBenefits with one matching employment sequence number" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(12, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
          Some(LocalDate.parse("2014-06-10")), None, None))),
          CompanyCarBenefit(11, 400, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = false,
            Some(LocalDate.parse("2014-06-10")), None, None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description"),
            CodingComponent(CarBenefit, Some(11), 400, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector],mockCodingComponentService)
        Await.result(sut.companyCarBenefitForEmployment(nino, 11)(hc), 5 seconds) mustBe Some(result.last)
      }

      "the repository returned sequence of multiple companyCarBenefits with multiple matching employment sequence numbers" in {
        val nino = randomNino
        val result = Seq(CompanyCarBenefit(11, 200, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
          Some(LocalDate.parse("2014-06-10")), None, None))),
          CompanyCarBenefit(11, 400, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = false,
            Some(LocalDate.parse("2014-06-10")), None, None))),
          CompanyCarBenefit(11, 600, Seq(CompanyCar(10, "Company car", hasActiveFuelBenefit = true,
            Some(LocalDate.parse("2014-06-10")), None, None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(11), 200, "some other description"),
            CodingComponent(CarBenefit, Some(11), 400, "some other description"),
            CodingComponent(CarBenefit, Some(11), 600, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(mock[TaxAccountService], mockCompanyCarBenefitRepository, mock[CompanyCarConnector],mockCodingComponentService)
        Await.result(sut.companyCarBenefitForEmployment(nino, 11)(hc), 5 seconds) mustBe Some(result.head)
      }
    }
  }

  "remove company car and fuel" must {
    "successfully call company car service (PAYE) and remove company car from db" when {
      "PAYE company car returns a successful response with id" in {
        val expectedResult = "id"
        val currentTaxYear = TaxYear().year
        val carWithdrawDate = new LocalDate(currentTaxYear, 4, 24)
        val fuelWithdrawDate = Some(new LocalDate(currentTaxYear, 4, 24))
        val nino = randomNino
        val carSeqNum = 10
        val employmentSeqNum = 11
        val taxYear = TaxYear()
        val removeCarAndFuel = WithdrawCarAndFuel(10, carWithdrawDate, fuelWithdrawDate)

        val mockTaxAccountService = mock[TaxAccountService]

        val mockCompanyCarConnector = mock[CompanyCarConnector]
        when(mockCompanyCarConnector.withdrawCarBenefit(nino, taxYear, employmentSeqNum, carSeqNum, removeCarAndFuel)(hc))
          .thenReturn(Future.successful(expectedResult))

        val sut = createSUT(mockTaxAccountService, mock[CompanyCarBenefitRepository], mockCompanyCarConnector)
        Await.result(sut.withdrawCompanyCarAndFuel(nino, employmentSeqNum, carSeqNum, removeCarAndFuel)(hc), 5 seconds) mustBe expectedResult

        verify(mockTaxAccountService, times(1)).invalidateTaiCacheData()(any())
        verify(mockCompanyCarConnector, times(1)).withdrawCarBenefit(any(), any(), any(), any(), any())(any())
      }
    }
  }

  "benefits" must {
    "return empty list of other benefits" when {
      "there is no benefits coming from coding components" in {
        val nino = randomNino

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponentsWithoutBenefits))
        val sut = createSUT(codingComponentService = mockCodingComponentService)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).otherBenefits mustBe Seq.empty[GenericBenefit]
      }
    }
    "return all types of other benefits" when {
      "there is otherBenefits coming from coding components" in {
        val nino = randomNino

        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).otherBenefits mustBe
          createGenericBenefitList(allBenefitTypesExceptCompanyCar)
      }
    }

    "return empty list of company cars" when {
      "there is no company car coming from coding components" in {
        val nino = randomNino

        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar)

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))
        val sut = createSUT(codingComponentService = mockCodingComponentService)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).companyCarBenefits mustBe Seq.empty[CompanyCarBenefit]
      }
    }

    "return the list of company car benefits with the car list and version as empty" when {
      "there is company cars coming from coding components but couldn't match them with the company cars from repository" in {
        val nino = randomNino

        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).companyCarBenefits mustBe
          Seq(CompanyCarBenefit(126, 100, Nil, None))
      }

      "the company car repository returns an exception in response to the request for the given NINO and tax year" in {
        val nino = randomNino

        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.failed(
            new UnprocessableEntityException("An exception occurred during processing of PAYE URI  [/paye/<nino>/car-benefits/<tax-year>]")))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).companyCarBenefits mustBe
          Seq(CompanyCarBenefit(126, 100, Nil, None))
      }
    }

    "return the list of company car benefits and get cars and version from car benefit if there is a matching benefit from repo" when {
      "there is company cars coming from coding components and some matching company cars coming from repository" in {
        val nino = randomNino

        val taxFreeAmountComponents =
          taxFreeAmountComponentsWithoutBenefits ++
            createBenefitList(allBenefitTypesExceptCompanyCar) ++
            Seq(
              CodingComponent(CarBenefit, Some(12), 200, "some other description"),
              CodingComponent(CarBenefit, Some(13), 300, "some other description"),
              CodingComponent(CarBenefit, None, 800, "some other description"),
              CodingComponent(CarBenefit, Some(15), 900, "some other description")
            )

        val carBenefitsFromRepo = Seq(
          CompanyCarBenefit(12, 500, Seq(
            CompanyCar(12, "Company car", hasActiveFuelBenefit = false, Some(LocalDate.parse("2014-06-10")), None, None)),
            Some(123)),
          CompanyCarBenefit(13, 600, Seq(
            CompanyCar(12, "Company car", hasActiveFuelBenefit = true, Some(LocalDate.parse("2015-06-10")), None, None)),
            None)
        )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(carBenefitsFromRepo))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(Matchers.eq(nino), Matchers.eq(TaxYear()))(any())).
          thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        Await.result(sut.benefits(nino, TaxYear())(hc), 5 seconds).companyCarBenefits mustBe
          Seq(
            CompanyCarBenefit(12, 200, Seq(
              CompanyCar(12, "Company car", hasActiveFuelBenefit = false, Some(LocalDate.parse("2014-06-10")), None, None)),
              Some(123)),
            CompanyCarBenefit(13, 300, Seq(
              CompanyCar(12, "Company car", hasActiveFuelBenefit = true, Some(LocalDate.parse("2015-06-10")), None, None)),
              None),
            CompanyCarBenefit(0, 800, Seq(), None),
            CompanyCarBenefit(15, 900, Seq(), None)
          )

      }
    }
  }

  "removeCompanyBenefits" must {
    "return an envelopeId" when {
      "given valid inputs" in {
        val employmentId = 1
        val removeCompanyBenefit = RemoveCompanyBenefit("Mileage", "Remove Company Benefit", "On Or After 6 April 2017", Some("1200"), "Yes", Some("123456789"))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(mockIFormSubmissionService.uploadIForm(Matchers.eq(randomNino), Matchers.eq(IFormConstants.RemoveCompanyBenefitSubmissionKey),
          Matchers.eq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing().when(mockAuditable)
          .sendDataEvent(any(), any(), any(), any())(any())

        val sut = createSUT(mock[TaxAccountService], mock[CompanyCarBenefitRepository], mock[CompanyCarConnector], mock[CodingComponentService], mockIFormSubmissionService, mock[FileUploadService], mock[PdfService], mockAuditable)
        val result = Await.result(sut.removeCompanyBenefits(randomNino, employmentId, removeCompanyBenefit)(hc), 5 seconds)

        result mustBe "1"
      }
    }
    "send remove company benefit journey audit event" in {
      val employmentId = 1
      val removeCompanyBenefit = RemoveCompanyBenefit("Mileage", "Remove Company Benefit", "On Or After 6 April 2017", Some("1200"), "Yes", Some("123456789"))
      val map = Map(
        "nino" -> randomNino.nino,
        "envelope Id" -> "1",
        "telephone contact allowed" -> removeCompanyBenefit.contactByPhone,
        "telephone number" -> removeCompanyBenefit.phoneNumber.getOrElse(""),
        "Company Benefit Name" -> removeCompanyBenefit.benefitType,
        "Amount Received" -> removeCompanyBenefit.valueOfBenefit.getOrElse(""),
        "Date Ended" -> removeCompanyBenefit.stopDate,
        "What you told us" -> removeCompanyBenefit.whatYouToldUs.length.toString
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(mockIFormSubmissionService.uploadIForm(Matchers.eq(randomNino), Matchers.eq(IFormConstants.RemoveCompanyBenefitSubmissionKey),
        Matchers.eq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing().when(mockAuditable)
        .sendDataEvent(any(), any(), any(), any())(any())

      val sut = createSUT(mock[TaxAccountService], mock[CompanyCarBenefitRepository], mock[CompanyCarConnector], mock[CodingComponentService], mockIFormSubmissionService, mock[FileUploadService], mock[PdfService], mockAuditable)
      Await.result(sut.removeCompanyBenefits(randomNino, employmentId, removeCompanyBenefit)(hc), 5 seconds) mustBe "1"

      verify(mockAuditable, times(1)).sendDataEvent(Matchers.eq(IFormConstants.RemoveCompanyBenefitAuditTxnName), any(), any(),
        Matchers.eq(map))(any())
    }
  }

  val allBenefitTypesExceptCompanyCar = Seq(
    EmployerProvidedServices,
    BenefitInKind,
    CarFuelBenefit,
    MedicalInsurance,
    Telephone,
    ServiceBenefit,
    TaxableExpensesBenefit,
    VanBenefit,
    VanFuelBenefit,
    BeneficialLoan,
    Accommodation,
    Assets,
    AssetTransfer,
    EducationalServices,
    Entertaining,
    Expenses,
    Mileage,
    NonQualifyingRelocationExpenses,
    NurseryPlaces,
    OtherItems,
    PaymentsOnEmployeesBehalf,
    PersonalIncidentalExpenses,
    QualifyingRelocationExpenses,
    EmployerProvidedProfessionalSubscription,
    IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
    VouchersAndCreditCards,
    NonCashBenefit
  )

  def createBenefitList(typesList: Seq[BenefitComponentType], amount: BigDecimal = 100) =
    typesList.map(benefitType => CodingComponent(benefitType, Some(126), amount, "some other description"))

  def createGenericBenefitList(typesList: Seq[BenefitComponentType], amount: BigDecimal = 100) =
    typesList.map(benefitType => GenericBenefit(benefitType, Some(126), amount))

  val taxFreeAmountComponentsWithoutBenefits = Seq(
    CodingComponent(PersonalAllowancePA, Some(123), 12345, "some description"),
    CodingComponent(Commission, Some(125), 777, "some other description"),
    CodingComponent(BalancingCharge, Some(126), 999, "some other description")
  )

  private val randomNino = new Generator(new Random).nextNino

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private def createSUT(taxAccountService: TaxAccountService = mock[TaxAccountService],
                        companyCarBenefitRepository: CompanyCarBenefitRepository = mock[CompanyCarBenefitRepository],
                        companyCarConnector: CompanyCarConnector = mock[CompanyCarConnector],
                        codingComponentService: CodingComponentService = mock[CodingComponentService],
                        iFormSubmissionService: IFormSubmissionService = mock[IFormSubmissionService],
                        fileUploadService: FileUploadService = mock[FileUploadService],
                        pdfService: PdfService = mock[PdfService],
                        auditable: Auditor = mock[Auditor]) =
    new BenefitsService(taxAccountService,
      companyCarBenefitRepository,
      companyCarConnector,
      codingComponentService,
      iFormSubmissionService,
      fileUploadService,
      pdfService,
      auditable)
}