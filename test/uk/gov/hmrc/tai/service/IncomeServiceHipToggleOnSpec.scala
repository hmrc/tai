/*
 * Copyright 2024 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.ArgumentMatchersSugar.eqTo
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CitizenDetailsConnector, TaxAccountConnector}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.model.ETag
import uk.gov.hmrc.tai.model.admin.HipToggleTaxAccount
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxCodeIncomeHelper
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.LocalDate
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source

class IncomeServiceHipToggleOnSpec extends BaseSpec {
  private val basePath = "test/resources/data/TaxAccount/IncomeService/hip/"
  private def readFile(fileName: String): JsValue = {
    val jsonFilePath = basePath + fileName
    val bufferedSource = Source.fromFile(jsonFilePath)
    val source = bufferedSource.mkString("")
    bufferedSource.close()
    Json.parse(source)
  }
  private val etag = ETag("1")

  implicit val authenticatedRequest: AuthenticatedRequest[AnyContentAsEmpty.type] =
    AuthenticatedRequest(FakeRequest(), nino)

  private def taxAccountJsonWithIabds(
    incomeIabdSummaries: Seq[JsObject] = Seq.empty[JsObject],
    allowReliefIabdSummaries: Seq[JsObject] = Seq.empty[JsObject]
  ): JsObject =
    Json.obj(
      "taxAccountId" -> "id",
      "nino"         -> nino.nino,
      "totalLiability" -> Json.obj(
        "nonSavings" -> Json.obj(
          "totalIncome" -> Json.obj(
            "iabdSummaries" -> JsArray(incomeIabdSummaries)
          ),
          "allowReliefDeducts" -> Json.obj(
            "iabdSummaries" -> JsArray(allowReliefIabdSummaries)
          )
        )
      )
    )

  private def npsIabdSummaries(types: Seq[Int]): Seq[JsObject] =
    types.map { tp =>
      Json.obj(
        "amount"             -> 100,
        "type"               -> tp,
        "npsDescription"     -> "desc",
        "employmentId"       -> 1,
        "estimatesPaySource" -> 1
      )
    }
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private def createSUT(
    employmentService: EmploymentService = mock[EmploymentService],
    citizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector],
    taxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector],
    iabdService: IabdService = mock[IabdService],
    taxCodeIncomeHelper: TaxCodeIncomeHelper = mock[TaxCodeIncomeHelper],
    auditor: Auditor = mock[Auditor]
  ) =
    new IncomeService(
      employmentService,
      citizenDetailsConnector,
      taxAccountConnector,
      iabdService,
      taxCodeIncomeHelper,
      auditor,
      mockFeatureFlagService
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipToggleTaxAccount))).thenReturn(
      Future.successful(FeatureFlag(HipToggleTaxAccount, isEnabled = true))
    )
  }

  "untaxedInterest" must {
    val mockTaxAccountConnector = mock[TaxAccountConnector]

    "return untaxed interest" when {
      "it is present" in {
        val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "Untaxed Interest")
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(readFile("TC01.json")))

        val SUT = createSUT(taxAccountConnector = mockTaxAccountConnector)
        val result = SUT.untaxedInterest(nino)(HeaderCarrier()).futureValue

        result mustBe Some(untaxedInterest)
      }
    }

    "return None" when {
      "untaxed interest is not present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any())).thenReturn(Future.successful(Json.arr()))

        val SUT = createSUT(taxAccountConnector = mockTaxAccountConnector)
        val result = SUT.untaxedInterest(nino)(HeaderCarrier()).futureValue

        result mustBe None
      }
    }
  }

  "taxCodeIncome" must {
    "return the list of taxCodeIncomes for passed nino" in {
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      )

      val employment = Employment(
        "company name",
        Ceased,
        Some("888"),
        LocalDate.of(TaxYear().next.year, 5, 26),
        None,
        Nil,
        "",
        "",
        1,
        Some(100),
        hasPayrolledBenefit = false,
        receivingOccupationalPension = true
      )
      val employment2 = Employment(
        "company name",
        Ceased,
        Some("888"),
        LocalDate.of(TaxYear().next.year, 5, 26),
        None,
        Nil,
        "",
        "",
        2,
        Some(100),
        hasPayrolledBenefit = false,
        receivingOccupationalPension = true
      )

      val mockEmploymentService = mock[EmploymentService]
      val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(any[Nino], any[TaxYear])(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(Seq(employment, employment2), None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = sut.taxCodeIncomes(nino, TaxYear())(HeaderCarrier(), FakeRequest()).futureValue

      result mustBe taxCodeIncomes.map(_.copy(status = Ceased))
    }

    "return the list of taxCodeIncomes for passed nino even if employments is nil" in {
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      )

      val mockEmploymentService = mock[EmploymentService]
      val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(any[Nino], any[TaxYear])(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(Seq.empty, None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = sut.taxCodeIncomes(nino, TaxYear())(HeaderCarrier(), FakeRequest()).futureValue

      result mustBe taxCodeIncomes
    }

    "return the list of taxCodeIncomes for passed nino even if employments fails" in {
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      )

      val mockEmploymentService = mock[EmploymentService]
      val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(any[Nino], any[TaxYear])(any[HeaderCarrier], any()))
        .thenReturn(EitherT.leftT(UpstreamErrorResponse("not found", NOT_FOUND)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = sut.taxCodeIncomes(nino, TaxYear())(HeaderCarrier(), FakeRequest()).futureValue

      result mustBe taxCodeIncomes
    }
  }

  "matchedTaxCodeIncomesForYear" must {
    val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
    val mockEmploymentService = mock[EmploymentService]

    val taxCodeIncome = TaxCodeIncome(
      EmploymentIncome,
      Some(2),
      BigDecimal(0),
      EmploymentIncome.toString,
      "1100L",
      "Employer2",
      OtherBasisOperation,
      Live,
      BigDecimal(321.12),
      BigDecimal(0),
      BigDecimal(0)
    )

    val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(
      TaxCodeIncome(
        PensionIncome,
        Some(1),
        BigDecimal(1100),
        PensionIncome.toString,
        "1150L",
        "Employer1",
        Week1Month1BasisOperation,
        Live,
        BigDecimal(0),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(2),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(3),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      )
    )

    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      LocalDate.of(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true
    )
    val employments = Seq(employment, employment.copy(sequenceNumber = 1))
    val employmentWithDifferentSeqNumber = Seq(employment.copy(sequenceNumber = 99))

    "return a list of live and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            taxCodeIncomes
          )
        )

      when(mockEmploymentService.employmentsAsEitherT(any[Nino], any[TaxYear])(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments :+ employment.copy(employmentStatus = Ceased), None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(HeaderCarrier(), FakeRequest())
          .value
          .futureValue

      val expectedResult = Seq(IncomeSource(taxCodeIncomes(1), employment))

      result mustBe Right(expectedResult)
    }

    "return a list of ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(
          EitherT.rightT(
            Employments(
              Seq(
                employment.copy(employmentStatus = Ceased),
                employment.copy(employmentStatus = Live)
              ),
              None
            )
          )
        )

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Ceased)(HeaderCarrier(), FakeRequest())
          .value
          .futureValue

      val expectedResult = Seq(IncomeSource(taxCodeIncome, employment.copy(employmentStatus = Ceased)))

      result mustBe Right(expectedResult)
    }

    "return a list of potentially ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(
          EitherT.rightT(
            Employments(
              Seq(
                employment.copy(employmentStatus = PotentiallyCeased),
                employment.copy(employmentStatus = Live)
              ),
              None
            )
          )
        )

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, PotentiallyCeased)(
            HeaderCarrier(),
            FakeRequest()
          )
          .value
          .futureValue

      val expectedResult = Seq(IncomeSource(taxCodeIncome, employment.copy(employmentStatus = PotentiallyCeased)))

      result mustBe Right(expectedResult)
    }

    "return a list of not live and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            taxCodeIncomes
          )
        )

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(
          EitherT.rightT(
            Employments(
              Seq(
                employment.copy(employmentStatus = Ceased),
                employment.copy(employmentStatus = PotentiallyCeased),
                employment.copy(employmentStatus = Live)
              ),
              None
            )
          )
        )

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, NotLive)(HeaderCarrier(), FakeRequest())
          .value
          .futureValue

      val expectedResult =
        Seq(
          IncomeSource(taxCodeIncomes(1), employment.copy(employmentStatus = Ceased)),
          IncomeSource(taxCodeIncomes(1), employment.copy(employmentStatus = PotentiallyCeased))
        )

      result mustBe Right(expectedResult)
    }

    "return empty JSON when no records match" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(taxCodeIncome.copy(employmentId = None))))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, PotentiallyCeased)(
            HeaderCarrier(),
            FakeRequest()
          )
          .value
          .futureValue

      result mustBe Right(Seq.empty)

    }

    "return list of live and matched pension TaxCodeIncomes for a given year" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = Await
        .result(
          sut
            .matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier(), FakeRequest())
            .value,
          5.seconds
        )

      val expectedResult =
        Seq(
          IncomeSource(
            TaxCodeIncome(
              componentType = PensionIncome,
              employmentId = Some(1),
              amount = 1100,
              description = "PensionIncome",
              taxCode = "1150L",
              name = "Employer1",
              basisOperation = Week1Month1BasisOperation,
              status = Live,
              inYearAdjustmentIntoCY = 0,
              totalInYearAdjustment = 0,
              inYearAdjustmentIntoCYPlusOne = 0
            ),
            Employment(
              name = "company name",
              employmentStatus = Live,
              payrollNumber = Some("888"),
              startDate = LocalDate.parse(s"${TaxYear().next.year}-05-26"),
              endDate = None,
              annualAccounts = Seq.empty,
              taxDistrictNumber = "",
              payeNumber = "",
              sequenceNumber = 1,
              cessationPay = Some(100),
              hasPayrolledBenefit = false,
              receivingOccupationalPension = true
            )
          )
        )

      result mustBe Right(expectedResult)

    }

    "return empty json when there are no matching live employments" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employmentWithDifferentSeqNumber, None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result =
        sut
          .matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(HeaderCarrier(), FakeRequest())
          .value
          .futureValue

      result mustBe Right(Seq.empty)
    }

    "return empty json when there are no matching live pensions" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employmentWithDifferentSeqNumber, None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = Await
        .result(
          sut
            .matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier(), FakeRequest())
            .value,
          5.seconds
        )

      result mustBe Right(Seq.empty)
    }

    "return empty json when there are no TaxCodeIncome records for a given nino" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(Seq(employment), None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = Await
        .result(
          sut
            .matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier(), FakeRequest())
            .value,
          5.seconds
        )

      result mustBe Right(Seq.empty)
    }

    "return empty json when there are no employment records for a given nino" in {
      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(Seq.empty[Employment], None)))

      val sut = createSUT(employmentService = mockEmploymentService, taxCodeIncomeHelper = mockTaxCodeIncomeHelper)
      val result = Await
        .result(
          sut
            .matchedTaxCodeIncomesForYear(nino, TaxYear().next, PensionIncome, Live)(HeaderCarrier(), FakeRequest())
            .value,
          5.seconds
        )

      result mustBe Right(Seq.empty)
    }
  }

  "nonMatchingCeasedEmployments" must {
    val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
    val mockEmploymentService = mock[EmploymentService]

    val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(
      TaxCodeIncome(
        PensionIncome,
        Some(1),
        BigDecimal(1100),
        PensionIncome.toString,
        "1150L",
        "Employer1",
        Week1Month1BasisOperation,
        Live,
        BigDecimal(0),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(2),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      ),
      TaxCodeIncome(
        EmploymentIncome,
        Some(3),
        BigDecimal(0),
        EmploymentIncome.toString,
        "1100L",
        "Employer2",
        OtherBasisOperation,
        Live,
        BigDecimal(321.12),
        BigDecimal(0),
        BigDecimal(0)
      )
    )

    val employment = Employment(
      "company name",
      Live,
      Some("888"),
      LocalDate.of(TaxYear().next.year, 5, 26),
      None,
      Nil,
      "",
      "",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = true
    )

    "return list of non matching ceased employments when some employments do have an end date" in {
      val employments =
        Seq(
          employment,
          employment.copy(
            employmentStatus = Ceased,
            sequenceNumber = 1,
            endDate = Some(LocalDate.of(TaxYear().next.year, 8, 10))
          )
        )

      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(taxCodeIncomeHelper = mockTaxCodeIncomeHelper, employmentService = mockEmploymentService)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier(), FakeRequest()).value.futureValue

      val expectedResult =
        Seq(
          employment.copy(
            employmentStatus = Ceased,
            sequenceNumber = 1,
            endDate = Some(LocalDate.of(TaxYear().next.year, 8, 10))
          )
        )

      result mustBe Right(expectedResult)
    }

    "return empty json when no employments have an end date" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(taxCodeIncomeHelper = mockTaxCodeIncomeHelper, employmentService = mockEmploymentService)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier(), FakeRequest()).value.futureValue

      result mustBe Right(Seq.empty)
    }

    "return empty json when TaxCodeIncomes do not have an Id" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            Seq(
              TaxCodeIncome(
                EmploymentIncome,
                None,
                BigDecimal(0),
                EmploymentIncome.toString,
                "1100L",
                "Employer2",
                OtherBasisOperation,
                Ceased,
                BigDecimal(321.12),
                BigDecimal(0),
                BigDecimal(0)
              )
            )
          )
        )

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(taxCodeIncomeHelper = mockTaxCodeIncomeHelper, employmentService = mockEmploymentService)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier(), FakeRequest()).value.futureValue

      result mustBe Right(Seq.empty)
    }

    "return empty Json when there are no TaxCodeIncome records for a given nino" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(employments, None)))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(taxCodeIncomeHelper = mockTaxCodeIncomeHelper, employmentService = mockEmploymentService)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier(), FakeRequest()).value.futureValue

      result mustBe Right(Seq.empty)
    }

    "return empty json when there are no employment records for a given nino" in {

      when(mockTaxCodeIncomeHelper.fetchTaxCodeIncomes(any(), meq(TaxYear().next))(any()))
        .thenReturn(
          Future.successful(
            Seq(
              TaxCodeIncome(
                EmploymentIncome,
                None,
                BigDecimal(0),
                EmploymentIncome.toString,
                "1100L",
                "Employer2",
                OtherBasisOperation,
                Ceased,
                BigDecimal(321.12),
                BigDecimal(0),
                BigDecimal(0)
              )
            )
          )
        )

      when(mockEmploymentService.employmentsAsEitherT(meq(nino), meq(TaxYear().next))(any[HeaderCarrier], any()))
        .thenReturn(EitherT.rightT(Employments(Seq.empty[Employment], None)))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(taxCodeIncomeHelper = mockTaxCodeIncomeHelper, employmentService = mockEmploymentService)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(HeaderCarrier(), FakeRequest()).value.futureValue

      result mustBe Right(Seq.empty)
    }
  }

  "incomes" must {
    val mockTaxAccountConnector = mock[TaxAccountConnector]

    "return empty sequence of tax-code and non-tax code income" when {
      "there is no non-tax-code income present" in {

        when(mockTaxAccountConnector.taxAccount(any(), any())(any())).thenReturn(Future.successful(Json.arr()))

        val sut = createSUT(taxAccountConnector = mockTaxAccountConnector)

        val result = sut.incomes(nino, TaxYear()).futureValue

        result.taxCodeIncomes mustBe Seq.empty[TaxCodeIncome]
        result.nonTaxCodeIncomes mustBe NonTaxCodeIncome(None, Seq.empty[OtherNonTaxCodeIncome])
      }
    }

    "return non-tax-code incomes" when {
      "there is non-tax-code income present and bank-accounts are not present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(readFile("TC02.json")))

        val sut = createSUT(taxAccountConnector = mockTaxAccountConnector)

        val result = sut.incomes(nino, TaxYear()).futureValue

        result.nonTaxCodeIncomes.otherNonTaxCodeIncomes mustBe Seq(
          OtherNonTaxCodeIncome(NonCodedIncome, Some(1), 100, "Non-Coded Income"),
          OtherNonTaxCodeIncome(Commission, Some(1), 100, "Commission"),
          OtherNonTaxCodeIncome(OtherIncomeEarned, Some(1), 100, "Other Income (Earned)"),
          OtherNonTaxCodeIncome(OtherIncomeNotEarned, Some(1), 100, "Other Income (Not Earned)"),
          OtherNonTaxCodeIncome(PartTimeEarnings, Some(1), 100, "Part Time Earnings"),
          OtherNonTaxCodeIncome(Tips, Some(1), 100, "Tips"),
          OtherNonTaxCodeIncome(OtherEarnings, Some(1), 100, "Other Earnings"),
          OtherNonTaxCodeIncome(CasualEarnings, Some(1), 100, "Casual Earnings"),
          OtherNonTaxCodeIncome(ForeignDividendIncome, Some(1), 100, "Foreign Dividend Income"),
          OtherNonTaxCodeIncome(ForeignPropertyIncome, Some(1), 100, "Foreign Property Income"),
          OtherNonTaxCodeIncome(ForeignInterestAndOtherSavings, Some(1), 100, "Foreign Interest & Other Savings"),
          OtherNonTaxCodeIncome(ForeignPensionsAndOtherIncome, Some(1), 100, "Foreign Pensions & Other Income"),
          OtherNonTaxCodeIncome(StatePension, Some(1), 100, "State Pension"),
          OtherNonTaxCodeIncome(OccupationalPension, Some(1), 100, "Occupational Pension"),
          OtherNonTaxCodeIncome(PublicServicesPension, Some(1), 100, "Public Services Pension"),
          OtherNonTaxCodeIncome(ForcesPension, Some(1), 100, "Forces Pension"),
          OtherNonTaxCodeIncome(PersonalPensionAnnuity, Some(1), 100, "Personal Pension Annuity"),
          OtherNonTaxCodeIncome(Profit, Some(1), 100, "Profit"),
          OtherNonTaxCodeIncome(BankOrBuildingSocietyInterest, Some(1), 100, "Taxed Interest"),
          OtherNonTaxCodeIncome(UkDividend, Some(1), 100, "UK Dividend"),
          OtherNonTaxCodeIncome(UnitTrust, Some(1), 100, "Unit Trust"),
          OtherNonTaxCodeIncome(StockDividend, Some(1), 100, "Stock Dividend"),
          OtherNonTaxCodeIncome(NationalSavings, Some(1), 100, "National Savings"),
          OtherNonTaxCodeIncome(SavingsBond, Some(1), 100, "Savings Bond"),
          OtherNonTaxCodeIncome(PurchasedLifeAnnuities, Some(1), 100, "Purchased Life Annuities"),
          OtherNonTaxCodeIncome(IncapacityBenefit, Some(1), 100, "Incapacity Benefit"),
          OtherNonTaxCodeIncome(JobSeekersAllowance, Some(1), 100, "Job Seekers Allowance"),
          OtherNonTaxCodeIncome(EmploymentAndSupportAllowance, Some(1), 100, "Employment and Support Allowance")
        )

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(
          UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "Untaxed Interest")
        )

      }

      "non-tax-code income and bank accounts are present" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(readFile("TC03.json")))

        val sut = createSUT(taxAccountConnector = mockTaxAccountConnector)

        val result = sut.incomes(nino, TaxYear()).futureValue

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(
          UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "Untaxed Interest")
        )



      }

      "bypass any bank account retrieval and return no untaxed interest" when {
        "no UntaxedInterestIncome is present" in {
          when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
            .thenReturn(Future.successful(readFile("TC04.json")))

          val sut = createSUT(taxAccountConnector = mockTaxAccountConnector)

          val result = sut.incomes(nino, TaxYear()).futureValue

          result.nonTaxCodeIncomes.untaxedInterest mustBe None
        }
      }

      "bbsi api throws exception" in {
        when(mockTaxAccountConnector.taxAccount(any(), any())(any()))
          .thenReturn(Future.successful(readFile("TC05.json")))

        val sut = createSUT(taxAccountConnector = mockTaxAccountConnector)

        val result = sut.incomes(nino, TaxYear()).futureValue

        result.nonTaxCodeIncomes.untaxedInterest mustBe Some(
          UntaxedInterest(UntaxedInterestIncome, Some(1), 100, "Untaxed Interest")
        )
      }
    }
  }

  "Employments" must {
    "return sequence of employments when taxCodeIncomes is not empty" in {
      val mockEmploymentService = mock[EmploymentService]
      val emp = Employment(
        "company name",
        Live,
        Some("888"),
        LocalDate.of(2017, 5, 26),
        None,
        Nil,
        "",
        "",
        2,
        Some(100),
        hasPayrolledBenefit = false,
        receivingOccupationalPension = true
      )
      val taxCodeIncomes = Seq(
        TaxCodeIncome(
          EmploymentIncome,
          Some(1),
          BigDecimal(0),
          "EmploymentIncome",
          "1150L",
          "Employer1",
          Week1Month1BasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        ),
        TaxCodeIncome(
          EmploymentIncome,
          Some(2),
          BigDecimal(0),
          "EmploymentIncome",
          "1100L",
          "Employer2",
          OtherBasisOperation,
          Live,
          BigDecimal(0),
          BigDecimal(0),
          BigDecimal(0)
        )
      )

      when(mockEmploymentService.employmentsAsEitherT(any(), any())(any(), any()))
        .thenReturn(EitherT.rightT(Employments(Seq(emp), None)))

      val sut = createSUT(employmentService = mockEmploymentService)

      val result = sut.employments(taxCodeIncomes, nino, TaxYear().next)(implicitly, FakeRequest()).value.futureValue

      result mustBe Right(Employments(Seq(emp), None))
    }

    "return empty sequence of when taxCodeIncomes is  empty" in {
      val taxCodeIncomes = Seq.empty[TaxCodeIncome]

      val sut = createSUT()

      val result = sut.employments(taxCodeIncomes, nino, TaxYear())(implicitly, FakeRequest()).value.futureValue

      result mustBe Right(Employments(Seq.empty[Employment], None))
    }
  }

  "updateTaxCodeIncome" must {
    "for current year" must {
      "return an income update success" when {
        "a valid update amount is provided" in {
          val taxYear = TaxYear()
          val incomeAmount = Some("123.45")

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
            .thenReturn(
              EitherT.rightT(
                Employment(
                  "",
                  Live,
                  None,
                  LocalDate.now(),
                  None,
                  Seq.empty[AnnualAccount],
                  "",
                  "",
                  0,
                  Some(100),
                  hasPayrolledBenefit = false,
                  receivingOccupationalPension = false
                )
              )
            )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
          when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
            .thenReturn(Future.successful(incomeAmount))

          val mockIabdService = mock[IabdService]
          when(mockIabdService.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any())(any(), any()))
            .thenReturn(
              Future.successful(IncomeUpdateSuccess)
            )

          val mockAuditor = mock[Auditor]
          doNothing
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
            iabdService = mockIabdService,
            auditor = mockAuditor
          )

          val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly).futureValue

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "123.45"
          )
          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())
        }

        "the current amount is not provided due to no incomes returned" in {

          val taxYear = TaxYear()

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
            .thenReturn(
              EitherT.rightT(
                Employment(
                  "",
                  Live,
                  None,
                  LocalDate.now(),
                  None,
                  Seq.empty[AnnualAccount],
                  "",
                  "",
                  0,
                  Some(100),
                  hasPayrolledBenefit = false,
                  receivingOccupationalPension = false
                )
              )
            )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
          when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
            .thenReturn(Future.successful(None))

          val mockIabdService = mock[IabdService]
          when(mockIabdService.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any())(any(), any()))
            .thenReturn(
              Future.successful(IncomeUpdateSuccess)
            )

          val mockAuditor = mock[Auditor]
          doNothing
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
            iabdService = mockIabdService,
            auditor = mockAuditor
          )

          val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly).futureValue

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "Unknown"
          )

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())

        }

        "the current amount is not provided due to an income mismatch" in {

          val taxYear = TaxYear()
          val incomeAmount = None

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
            .thenReturn(
              EitherT.rightT(
                Employment(
                  "",
                  Live,
                  None,
                  LocalDate.now(),
                  None,
                  Seq.empty[AnnualAccount],
                  "",
                  "",
                  0,
                  Some(100),
                  hasPayrolledBenefit = false,
                  receivingOccupationalPension = false
                )
              )
            )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
          when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
            .thenReturn(Future.successful(incomeAmount))

          val mockIabdService = mock[IabdService]
          when(mockIabdService.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any())(any(), any()))
            .thenReturn(
              Future.successful(IncomeUpdateSuccess)
            )

          val mockAuditor = mock[Auditor]
          doNothing
            .when(mockAuditor)
            .sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
            iabdService = mockIabdService,
            auditor = mockAuditor
          )

          val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly).futureValue

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "Unknown"
          )

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())

        }
      }

      "return an error indicating a CY update failure" when {
        "the hod update fails for a CY update" in {
          val taxYear = TaxYear()
          val incomeAmount = Some("123.45")

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
            .thenReturn(
              EitherT.rightT(
                Employment(
                  "",
                  Live,
                  None,
                  LocalDate.now(),
                  None,
                  Seq.empty[AnnualAccount],
                  "",
                  "",
                  0,
                  Some(100),
                  hasPayrolledBenefit = false,
                  receivingOccupationalPension = false
                )
              )
            )

          val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
          when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
            .thenReturn(Future.successful(incomeAmount))

          val mockIabdService = mock[IabdService]
          when(mockIabdService.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any())(any(), any()))
            .thenReturn(
              Future.successful(IncomeUpdateFailed(s"Hod update failed for ${taxYear.year} update"))
            )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
            iabdService = mockIabdService,
            citizenDetailsConnector = citizenDetailsConnector
          )

          val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly).futureValue

          result mustBe IncomeUpdateFailed(s"Hod update failed for ${taxYear.year} update")
        }
      }
    }

    "for next year" must {
      "return an income success" when {
        "an update amount is provided" in {
          val taxYear = TaxYear().next
          val incomeAmount = Some("123.45")

          val mockEmploymentSvc = mock[EmploymentService]
          when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
            .thenReturn(
              EitherT.rightT(
                Employment(
                  "",
                  Live,
                  None,
                  LocalDate.now(),
                  None,
                  Seq.empty[AnnualAccount],
                  "",
                  "",
                  0,
                  Some(100),
                  hasPayrolledBenefit = false,
                  receivingOccupationalPension = false
                )
              )
            )

          val citizenDetailsConnector = mock[CitizenDetailsConnector]
          when(citizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(etag)))

          val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
          when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
            .thenReturn(Future.successful(incomeAmount))

          val mockIabdService = mock[IabdService]
          when(mockIabdService.updateTaxCodeAmount(any(), meq[TaxYear](taxYear), any(), any(), any())(any(), any()))
            .thenReturn(
              Future.successful(IncomeUpdateSuccess)
            )

          val mockAuditor = mock[Auditor]
          doNothing.when(mockAuditor).sendDataEvent(any(), any())(any())

          val SUT = createSUT(
            employmentService = mockEmploymentSvc,
            citizenDetailsConnector = citizenDetailsConnector,
            taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
            iabdService = mockIabdService,
            auditor = mockAuditor
          )

          val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly).futureValue

          result mustBe IncomeUpdateSuccess

          val auditMap = Map(
            "nino"          -> nino.value,
            "year"          -> taxYear.toString,
            "employmentId"  -> "1",
            "newAmount"     -> "1234",
            "currentAmount" -> "123.45"
          )

          verify(mockAuditor).sendDataEvent(meq("Update Multiple Employments Data"), meq(auditMap))(any())
        }
      }
    }

    "return a IncomeUpdateFailed if there is no etag" in {
      val taxYear = TaxYear()

      val mockEmploymentSvc = mock[EmploymentService]
      when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
        .thenReturn(
          EitherT.rightT(
            Employment(
              "",
              Live,
              None,
              LocalDate.now(),
              None,
              Seq.empty[AnnualAccount],
              "",
              "",
              0,
              Some(100),
              hasPayrolledBenefit = false,
              receivingOccupationalPension = false
            )
          )
        )
      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(None))

      val mockAuditor = mock[Auditor]
      doNothing.when(mockAuditor).sendDataEvent(any(), any())(any())

      val SUT = createSUT(
        employmentService = mockEmploymentSvc,
        citizenDetailsConnector = mockCitizenDetailsConnector,
        auditor = mockAuditor
      )

      val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly)
      result.futureValue mustBe IncomeUpdateFailed("Could not find an ETag")
    }

    "return a IncomeUpdateFailed if the etag is not an int" in {
      val taxYear = TaxYear()
      val incomeAmount = Some("123.45")

      val mockEmploymentSvc = mock[EmploymentService]
      when(mockEmploymentSvc.employmentAsEitherT(any(), any())(any(), any()))
        .thenReturn(
          EitherT.rightT(
            Employment(
              "",
              Live,
              None,
              LocalDate.now(),
              None,
              Seq.empty[AnnualAccount],
              "",
              "",
              0,
              Some(100),
              hasPayrolledBenefit = false,
              receivingOccupationalPension = false
            )
          )
        )

      val mockTaxCodeIncomeHelper = mock[TaxCodeIncomeHelper]
      when(mockTaxCodeIncomeHelper.incomeAmountForEmploymentId(any(), any(), any())(any()))
        .thenReturn(Future.successful(incomeAmount))

      val mockAuditor = mock[Auditor]
      doNothing.when(mockAuditor).sendDataEvent(any(), any())(any())

      val mockCitizenDetailsConnector = mock[CitizenDetailsConnector]
      when(mockCitizenDetailsConnector.getEtag(any())(any())).thenReturn(Future.successful(Some(ETag("not an ETag"))))

      val SUT = createSUT(
        employmentService = mockEmploymentSvc,
        citizenDetailsConnector = mockCitizenDetailsConnector,
        taxCodeIncomeHelper = mockTaxCodeIncomeHelper,
        auditor = mockAuditor
      )

      val result = SUT.updateTaxCodeIncome(nino, taxYear, 1, 1234)(HeaderCarrier(), implicitly)
      result.futureValue mustBe IncomeUpdateFailed("Could not parse etag")
    }
  }
}
