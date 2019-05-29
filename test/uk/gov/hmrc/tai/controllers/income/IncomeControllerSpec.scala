/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.income

import org.joda.time.LocalDate
import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.api.ApiFormats
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateResponse, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{EmploymentService, IncomeService, TaxAccountService}

import scala.concurrent.Future
import scala.util.Random

class IncomeControllerSpec extends PlaySpec
  with MockitoSugar
  with TaxCodeIncomeSourceAPIFormatters
  with MockAuthenticationPredicate
  with ApiFormats {

  val employmentId = 1
  val mockTaxAccountService: TaxAccountService = generateMockAccountServiceWithAnyResponse
  val expectedJsonEmpty: JsObject = Json.obj(
    "data" -> Json.arr(),
    "links" -> Json.arr()
  )

  val mockIncomeService = mock[IncomeService]
  val mockEmploymentService = mock[EmploymentService]

  val taxCodeIncomes: Seq[TaxCodeIncome] = Seq(TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1100),
    PensionIncome.toString, "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
    TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
      EmploymentIncome.toString, "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
    TaxCodeIncome(EmploymentIncome, Some(3), BigDecimal(0),
      EmploymentIncome.toString, "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

  "untaxedInterest" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
        val result = sut.untaxedInterest(nino)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return OK with untaxed interest" when {
      "untaxed interest is returned by income service" in {
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(Some(untaxedInterest)))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "incomeComponentType" -> "UntaxedInterestIncome",
            "amount" -> 123,
            "description" -> "Untaxed Interest",
            "bankAccounts" -> JsArray()
          )
          ,
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }

    "return Not Found" when {
      "None is returned by income service" in {
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(None))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.failed(new NotFoundException("Error")))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }
  }

  "taxCodeIncomesForYear" must {

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
        val result = sut.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return Not Found" when {
      "Nil is returned by income service" in {
        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.failed(new NotFoundException("Error")))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }

    "return Ok with tax code incomes" when {
      "a list of tax code incomes is returned by income service" in {
        val taxCodeIncomesNoPension = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(1100),
          EmploymentIncome.toString, "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            EmploymentIncome.toString, "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.successful(taxCodeIncomesNoPension))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "componentType" -> EmploymentIncome.toString,
              "employmentId" -> 1,
              "amount" -> 1100,
              "description" -> EmploymentIncome.toString,
              "taxCode" -> "1150L",
              "name" -> "Employer1",
              "basisOperation" -> "Week1Month1BasisOperation",
              "status" -> Live.toString,
              "inYearAdjustmentIntoCY" -> 0,
              "totalInYearAdjustment" -> 0,
              "inYearAdjustmentIntoCYPlusOne" -> 0),
            Json.obj(
              "componentType" -> EmploymentIncome.toString,
              "employmentId" -> 2,
              "amount" -> 0,
              "description" -> EmploymentIncome.toString,
              "taxCode" -> "1100L",
              "name" -> "Employer2",
              "basisOperation" -> "OtherBasisOperation",
              "status" -> PotentiallyCeased.toString,
              "inYearAdjustmentIntoCY" -> 321.12,
              "totalInYearAdjustment" -> 0,
              "inYearAdjustmentIntoCYPlusOne" -> 0)
          )
          ,
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "matchedTaxCodeIncomesForYear" must {

    val employment = Employment("company name", Some("888"), new LocalDate(TaxYear().next.year, 5, 26),
      None, Nil, "", "", 2, Some(100), hasPayrolledBenefit = false, receivingOccupationalPension = true)
    val employments = Seq(employment, employment.copy(sequenceNumber = 1))
    val employmentWithDifferentSeqNumber = Seq(employment.copy(sequenceNumber = 99))

    "return tax code incomes and employments JSON" in {
      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), Matchers.eq(TaxYear().next), any(), any())(any()))
        .thenReturn(Future.successful(Seq(IncomeSource(taxCodeIncomes(1), employment))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      val expectedJson = Json.obj(
        "data" -> Json.arr(Json.toJson(IncomeSource(taxCodeIncomes(1), employment))),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {

      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), Matchers.eq(TaxYear().next), any(), any())(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {

      when(mockIncomeService.matchedTaxCodeIncomesForYear(any(), Matchers.eq(TaxYear().next), any(), any())(any()))
        .thenReturn(Future.failed(new BadRequestException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, EmploymentIncome, Live)(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }

  "nonMatchingCeasedEmployments" must {
    val employment = Employment("company name", Some("888"), new LocalDate(TaxYear().next.year, 5, 26),
      None, Nil, "", "", 2, Some(100), hasPayrolledBenefit = false, receivingOccupationalPension = true)

    "return non matching ceased employments JSON" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1, endDate = Some(new LocalDate(TaxYear().next.year, 8, 10))))

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(employments))

      val nextTaxYear = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, nextTaxYear)(FakeRequest())


      val expectedJson = Json.obj(
        "data" -> employments,
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {

      when(mockIncomeService.nonMatchingCeasedEmployments(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new BadRequestException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }

  "incomes" must {
    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
        val result = sut.income(nino, TaxYear())(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return Ok with income" when {
      "income returned by IncomeService" in {

        val income = uk.gov.hmrc.tai.model.domain.income.Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(None, Seq(
          OtherNonTaxCodeIncome(Profit, None, 100, "Profit")
        )))

        when(mockIncomeService.incomes(any(), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(income))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.income(nino, TaxYear())(FakeRequest())

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "taxCodeIncomes" -> JsArray(),
            "nonTaxCodeIncomes" -> Json.obj(
              "otherNonTaxCodeIncomes" -> Json.arr(
                Json.obj(
                  "incomeComponentType" -> "Profit",
                  "amount" -> 100,
                  "description" -> "Profit"
                )
              )
            )
          )
          ,
          "links" -> Json.arr()
        )

        contentAsJson(result) mustBe expectedJson
      }
    }
  }

  "updateTaxCodeIncome" must {

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
        val result = sut.updateTaxCodeIncome(nino, TaxYear(), 1)(FakeRequest("POST", "/",
          FakeHeaders(), JsNull)
          .withHeaders(("content-type", "application/json")))
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }

    "return a bad request" when {
      "an invalid update amount is provided" in {
        val SUT = setup(Future.successful(InvalidAmount("")), mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe BAD_REQUEST
        verify(mockTaxAccountService, times(0)).invalidateTaiCacheData()(any())
      }
    }

    "return internal server error" when {

      "income update exception has been thrown" in {
        val SUT = setup(Future.successful(IncomeUpdateFailed("Failed")), mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(0)).invalidateTaiCacheData()(any())
      }

      "any exception has been thrown" in {
        val SUT = setup(Future.failed(new RuntimeException("Error")), mockTaxAccountService)
        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(0)).invalidateTaiCacheData()(any())
      }
    }
  }

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  private val nino = new Generator(new Random).nextNino

  private val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, None, 123, "Untaxed Interest", Seq.empty[BankAccount])

  private def createSUT(incomeService: IncomeService = mock[IncomeService],
                        taxAccountService: TaxAccountService = mock[TaxAccountService],
                        employmentService: EmploymentService = mock[EmploymentService],
                        authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new IncomeController(incomeService, taxAccountService, employmentService, authentication, cc)

  private def fakeTaxCodeIncomeRequest: FakeRequest[JsValue] = {
    val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)
    FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
      .withHeaders(("content-type", "application/json"))
  }

  private def generateMockAccountServiceWithAnyResponse: TaxAccountService = {
    val mockTaxAccountService = mock[TaxAccountService]
    when(mockTaxAccountService.version(any(), any())(any())).thenReturn(Future.successful(Some(1)))
    mockTaxAccountService
  }

  private def setup(response: Future[IncomeUpdateResponse], mockTaxAccountService: TaxAccountService): IncomeController = {
    val mockIncomeService: IncomeService = {
      val mockIncomeService: IncomeService = mock[IncomeService]
      when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any())).thenReturn(response)
      mockIncomeService
    }
    createSUT(mockIncomeService, mockTaxAccountService)
  }
}