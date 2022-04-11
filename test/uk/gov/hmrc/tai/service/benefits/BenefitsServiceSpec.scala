/*
 * Copyright 2022 HM Revenue & Customs
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

import java.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import uk.gov.hmrc.http.UnprocessableEntityException
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.CompanyCarConnector
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.benefits.{GenericBenefit, _}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.CompanyCarBenefitRepository
import uk.gov.hmrc.tai.service._
import uk.gov.hmrc.tai.util.{BaseSpec, IFormConstants}

import scala.concurrent.Future

class BenefitsServiceSpec extends BaseSpec {
  "companyCarBenefit" must {
    "return Nil" when {
      "the repository returned Nil" in {

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq.empty[CodingComponent]))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        sut.companyCarBenefits(nino)(hc).futureValue mustBe Seq.empty[CompanyCarBenefit]
      }
    }

    "return sequence of companyCarBenefit" when {
      "the repository returned sequence of companyCarBenefit with no fuel benefit" in {
        val result = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefits(nino)(hc).futureValue mustBe result

      }

      "the repository returned sequence of companyCarBenefit with a fuel benefit" in {
        val result = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                Some(LocalDate.parse("2014-06-10")),
                None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefits(nino)(hc).futureValue mustBe result
      }
    }

    "return the first matching companyCarBenefit for a given employment sequence number" when {
      "the repository returned sequence of companyCarBenefit with no matching employment sequence number" in {
        val result = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefitForEmployment(nino, 11)(hc).futureValue mustBe None
      }

      "the repository returned sequence of companyCarBenefit with one matching employment sequence number" in {
        val result = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))))

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefitForEmployment(nino, 12)(hc).futureValue mustBe Some(result.head)
      }

      "the repository returned sequence of multiple companyCarBenefits with one matching employment sequence number" in {
        val result = Seq(
          CompanyCarBenefit(
            12,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))),
          CompanyCarBenefit(
            11,
            400,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None)))
        )

        val taxFreeAmountComponents =
          Seq(
            CodingComponent(CarBenefit, Some(12), 200, "some other description"),
            CodingComponent(CarBenefit, Some(11), 400, "some other description")
          )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(result))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefitForEmployment(nino, 11)(hc).futureValue mustBe Some(result.last)
      }

      "the repository returned sequence of multiple companyCarBenefits with multiple matching employment sequence numbers" in {
        val result = Seq(
          CompanyCarBenefit(
            11,
            200,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))),
          CompanyCarBenefit(
            11,
            400,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None))),
          CompanyCarBenefit(
            11,
            600,
            Seq(
              CompanyCar(
                10,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None)))
        )

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
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          mockCompanyCarBenefitRepository,
          mock[CompanyCarConnector],
          mockCodingComponentService)
        sut.companyCarBenefitForEmployment(nino, 11)(hc).futureValue mustBe Some(result.head)
      }
    }
  }

  "remove company car and fuel" must {
    "successfully call company car service (PAYE) and remove company car from db" when {
      "PAYE company car returns a successful response with id" in {
        val expectedResult = "id"
        val currentTaxYear = TaxYear().year
        val carWithdrawDate = LocalDate.of(currentTaxYear, 4, 24)
        val fuelWithdrawDate = Some(LocalDate.of(currentTaxYear, 4, 24))
        val carSeqNum = 10
        val employmentSeqNum = 11
        val taxYear = TaxYear()
        val removeCarAndFuel = WithdrawCarAndFuel(10, carWithdrawDate, fuelWithdrawDate)

        val mockCompanyCarConnector = mock[CompanyCarConnector]

        when(
          mockCompanyCarConnector.withdrawCarBenefit(nino, taxYear, employmentSeqNum, carSeqNum, removeCarAndFuel)(hc))
          .thenReturn(Future.successful(expectedResult))

        val cacheService = mock[CacheService]

        val sut = createSUT(mock[CompanyCarBenefitRepository], mockCompanyCarConnector, cacheService = cacheService)
        sut.withdrawCompanyCarAndFuel(nino, employmentSeqNum, carSeqNum, removeCarAndFuel)(hc).futureValue mustBe expectedResult

        verify(cacheService, times(1)).invalidateTaiCacheData(meq(nino))(any())
        verify(mockCompanyCarConnector, times(1)).withdrawCarBenefit(any(), any(), any(), any(), any())(any())
      }
    }
  }

  "benefits" must {
    "return empty list of other benefits" when {
      "there is no benefits coming from coding components" in {
        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponentsWithoutBenefits))
        val sut = createSUT(codingComponentService = mockCodingComponentService)

        sut.benefits(nino, TaxYear())(hc).futureValue.otherBenefits mustBe Seq.empty[GenericBenefit]
      }
    }
    "return all types of other benefits" when {
      "there is otherBenefits coming from coding components" in {
        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        sut.benefits(nino, TaxYear())(hc).futureValue.otherBenefits mustBe
          createGenericBenefitList(allBenefitTypesExceptCompanyCar)
      }
    }

    "return empty list of company cars" when {
      "there is no company car coming from coding components" in {
        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar)

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))
        val sut = createSUT(codingComponentService = mockCodingComponentService)

        sut.benefits(nino, TaxYear())(hc).futureValue.companyCarBenefits mustBe Seq
          .empty[CompanyCarBenefit]
      }
    }

    "return the list of company car benefits with the car list and version as empty" when {
      "there is company cars coming from coding components but couldn't match them with the company cars from repository" in {
        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(Seq.empty[CompanyCarBenefit]))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        sut.benefits(nino, TaxYear())(hc).futureValue.companyCarBenefits mustBe
          Seq(CompanyCarBenefit(126, 100, Nil, None))
      }

      "the company car repository returns an exception in response to the request for the given NINO and tax year" in {
        val taxFreeAmountComponents = taxFreeAmountComponentsWithoutBenefits ++
          createBenefitList(allBenefitTypesExceptCompanyCar :+ CarBenefit)

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.failed(new UnprocessableEntityException(
            "An exception occurred during processing of PAYE URI  [/paye/<nino>/car-benefits/<tax-year>]")))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        sut.benefits(nino, TaxYear())(hc).futureValue.companyCarBenefits mustBe
          Seq(CompanyCarBenefit(126, 100, Nil, None))
      }
    }

    "return the list of company car benefits and get cars and version from car benefit if there is a matching benefit from repo" when {
      "there is company cars coming from coding components and some matching company cars coming from repository" in {
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
          CompanyCarBenefit(
            12,
            500,
            Seq(
              CompanyCar(
                12,
                "Company car",
                hasActiveFuelBenefit = false,
                Some(LocalDate.parse("2014-06-10")),
                None,
                None)),
            Some(123)),
          CompanyCarBenefit(
            13,
            600,
            Seq(
              CompanyCar(
                12,
                "Company car",
                hasActiveFuelBenefit = true,
                Some(LocalDate.parse("2015-06-10")),
                None,
                None)),
            None)
        )

        val mockCompanyCarBenefitRepository = mock[CompanyCarBenefitRepository]
        when(mockCompanyCarBenefitRepository.carBenefit(any(), any())(any()))
          .thenReturn(Future.successful(carBenefitsFromRepo))

        val mockCodingComponentService = mock[CodingComponentService]
        when(mockCodingComponentService.codingComponents(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(taxFreeAmountComponents))

        val sut = createSUT(
          codingComponentService = mockCodingComponentService,
          companyCarBenefitRepository = mockCompanyCarBenefitRepository)

        sut.benefits(nino, TaxYear())(hc).futureValue.companyCarBenefits mustBe
          Seq(
            CompanyCarBenefit(
              12,
              200,
              Seq(
                CompanyCar(
                  12,
                  "Company car",
                  hasActiveFuelBenefit = false,
                  Some(LocalDate.parse("2014-06-10")),
                  None,
                  None)),
              Some(123)),
            CompanyCarBenefit(
              13,
              300,
              Seq(
                CompanyCar(
                  12,
                  "Company car",
                  hasActiveFuelBenefit = true,
                  Some(LocalDate.parse("2015-06-10")),
                  None,
                  None)),
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
        val removeCompanyBenefit =
          RemoveCompanyBenefit("Mileage", "On Or After 6 April 2017", Some("1200"), "Yes", Some("123456789"))

        val mockIFormSubmissionService = mock[IFormSubmissionService]
        when(
          mockIFormSubmissionService
            .uploadIForm(meq(nino), meq(IFormConstants.RemoveCompanyBenefitSubmissionKey), meq("TES1"), any())(any()))
          .thenReturn(Future.successful("1"))

        val mockAuditable = mock[Auditor]
        doNothing()
          .when(mockAuditable)
          .sendDataEvent(any(), any())(any())

        val sut = createSUT(
          mock[CompanyCarBenefitRepository],
          mock[CompanyCarConnector],
          mock[CodingComponentService],
          mockIFormSubmissionService,
          mock[CacheService],
          mockAuditable
        )
        val result =
          sut.removeCompanyBenefits(nino, employmentId, removeCompanyBenefit)(hc).futureValue

        result mustBe "1"
      }
    }
    "send remove company benefit journey audit event" in {
      val employmentId = 1
      val removeCompanyBenefit =
        RemoveCompanyBenefit("Mileage", "On Or After 6 April 2017", Some("1200"), "Yes", Some("123456789"))
      val map = Map(
        "nino"                      -> nino.nino,
        "envelope Id"               -> "1",
        "telephone contact allowed" -> removeCompanyBenefit.contactByPhone,
        "telephone number"          -> removeCompanyBenefit.phoneNumber.getOrElse(""),
        "Company Benefit Name"      -> removeCompanyBenefit.benefitType,
        "Amount Received"           -> removeCompanyBenefit.valueOfBenefit.getOrElse(""),
        "Date Ended"                -> removeCompanyBenefit.stopDate
      )

      val mockIFormSubmissionService = mock[IFormSubmissionService]
      when(
        mockIFormSubmissionService
          .uploadIForm(meq(nino), meq(IFormConstants.RemoveCompanyBenefitSubmissionKey), meq("TES1"), any())(any()))
        .thenReturn(Future.successful("1"))

      val mockAuditable = mock[Auditor]
      doNothing()
        .when(mockAuditable)
        .sendDataEvent(any(), any())(any())

      val sut = createSUT(
        mock[CompanyCarBenefitRepository],
        mock[CompanyCarConnector],
        mock[CodingComponentService],
        mockIFormSubmissionService,
        mock[CacheService],
        mockAuditable
      )
      sut.removeCompanyBenefits(nino, employmentId, removeCompanyBenefit)(hc).futureValue mustBe "1"

      verify(mockAuditable, times(1))
        .sendDataEvent(meq(IFormConstants.RemoveCompanyBenefitAuditTxnName), meq(map))(any())
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

  private def createSUT(
    companyCarBenefitRepository: CompanyCarBenefitRepository = mock[CompanyCarBenefitRepository],
    companyCarConnector: CompanyCarConnector = mock[CompanyCarConnector],
    codingComponentService: CodingComponentService = mock[CodingComponentService],
    iFormSubmissionService: IFormSubmissionService = mock[IFormSubmissionService],
    cacheService: CacheService = mock[CacheService],
    auditable: Auditor = mock[Auditor]) =
    new BenefitsService(
      companyCarBenefitRepository,
      companyCarConnector,
      codingComponentService,
      iFormSubmissionService,
      cacheService,
      auditable
    )
}
