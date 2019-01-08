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
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsNull, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.api.ApiFormats
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{EmploymentService, IncomeService, TaxAccountService}

import scala.concurrent.Future
import scala.util.Random

class IncomeControllerSpec extends PlaySpec
  with MockitoSugar
  with TaxCodeIncomeSourceAPIFormatters
  with MockAuthenticationPredicate
  with ApiFormats {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  private val nino = new Generator(new Random).nextNino

  private val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, None, 123, "Untaxed Interest", Seq.empty[BankAccount])

  private def createSUT(incomeService: IncomeService = mock[IncomeService],
                        taxAccountService: TaxAccountService = mock[TaxAccountService],
                        employmentService: EmploymentService = mock[EmploymentService],
                        authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new IncomeController(incomeService, taxAccountService, employmentService, authentication)

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
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(Some(untaxedInterest)))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        status(result) mustBe OK

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
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.untaxedInterest(any())(any()))
          .thenReturn(Future.successful(None))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.untaxedInterest(nino)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        val mockIncomeService = mock[IncomeService]
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
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.failed(new NotFoundException("Error")))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }

    "return Ok with tax code incomes" when {
      "a list of tax code incomes is returned by income service" in {
        val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(1100),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.successful(taxCodeIncomes))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe OK

        val expectedJson = Json.obj(
          "data" -> Json.arr(
            Json.obj(
              "componentType" -> "EmploymentIncome",
              "employmentId" -> 1,
              "amount" -> 1100,
              "description" -> "EmploymentIncome",
              "taxCode" -> "1150L",
              "name" -> "Employer1",
              "basisOperation" -> "Week1Month1BasisOperation",
              "status" -> "Live",
              "inYearAdjustmentIntoCY" -> 0,
              "totalInYearAdjustment" -> 0,
              "inYearAdjustmentIntoCYPlusOne" -> 0),
            Json.obj(
              "componentType" -> "EmploymentIncome",
              "employmentId" -> 2,
              "amount" -> 0,
              "description" -> "EmploymentIncome",
              "taxCode" -> "1100L",
              "name" -> "Employer2",
              "basisOperation" -> "OtherBasisOperation",
              "status" -> "PotentiallyCeased",
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

  "liveMatchedTaxCodeIncomesForYear" must {

    val taxCodeIncomes = Seq(TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1100),
      "PensionIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(3), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

    val employment = Employment("company name", Some("888"), new LocalDate(TaxYear().next.year, 5, 26),
      None, Nil, "", "", 2, Some(100), hasPayrolledBenefit = false, receivingOccupationalPension = true)
    val employments = Seq(employment, employment.copy(sequenceNumber = 1))

    "return list of live and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome")(FakeRequest())

      status(result) mustBe OK

      val expectedIncomeSource = Json.toJson(IncomeSource(TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
        employment
      ))

      val expectedJson = Json.arr(
        "data" -> Json.arr(expectedIncomeSource),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return list of live and matched pension TaxCodeIncomes for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome")(FakeRequest())

      status(result) mustBe OK

      val expectedJson = Json.obj(
        "data" -> Json.arr(
          Json.obj(
            "componentType" -> "PensionIncome",
            "employmentId" -> 1,
            "amount" -> 1100,
            "description" -> "PensionIncome",
            "taxCode" -> "1150L",
            "name" -> "Employer1",
            "basisOperation" -> "Week1Month1BasisOperation",
            "status" -> "Live",
            "inYearAdjustmentIntoCY" -> 0,
            "totalInYearAdjustment" -> 0,
            "inYearAdjustmentIntoCYPlusOne" -> 0)
        )
        ,
        "links" -> Json.arr()
      )
      contentAsJson(result) mustBe expectedJson
    }

    "return NotFound when there are no matching live employments" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 99))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are no matching live pensions" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 99))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are no TaxCodeIncome records for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment)))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are no employment records for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[Employment]))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when given an invalid or non-existent income type" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment)))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "BananaIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome")(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new BadRequestException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.liveMatchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome")(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }

  "ceasedMatchingIncomeSourcesForYear" must {

    val taxCodeIncomes = Seq(TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1100),
      "PensionIncome", "1150L", "Employer1", Week1Month1BasisOperation, PotentiallyCeased, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(3), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

    val employment = Employment("company name", Some("888"), new LocalDate(TaxYear().next.year, 5, 26),
      None, Nil, "", "", 3, Some(100), hasPayrolledBenefit = false, receivingOccupationalPension = true)
    val employments = Seq(employment)

    "return list of ceased employments and unmatched TaxCodeIncomes for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe OK

      val expectedJson = Json.obj(
        "data" -> Json.arr(
          Json.obj(
            "componentType" -> "PensionIncome",
            "employmentId" -> 1,
            "amount" -> 1100,
            "description" -> "PensionIncome",
            "taxCode" -> "1150L",
            "name" -> "Employer1",
            "basisOperation" -> "Week1Month1BasisOperation",
            "status" -> "PotentiallyCeased",
            "inYearAdjustmentIntoCY" -> 0,
            "totalInYearAdjustment" -> 0,
            "inYearAdjustmentIntoCYPlusOne" -> 0),
          Json.obj(
            "componentType" -> "EmploymentIncome",
            "employmentId" -> 2,
            "amount" -> 0,
            "description" -> "EmploymentIncome",
            "taxCode" -> "1100L",
            "name" -> "Employer2",
            "basisOperation" -> "OtherBasisOperation",
            "status" -> "Ceased",
            "inYearAdjustmentIntoCY" -> 321.12,
            "totalInYearAdjustment" -> 0,
            "inYearAdjustmentIntoCYPlusOne" -> 0)
        ),
        "links" -> Json.arr()
      )
      contentAsJson(result) mustBe expectedJson
    }

    "return NotFound when there are no ceased records and they don't match" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(taxCodeIncomes(2))))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are ceased records but they all match" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(taxCodeIncomes(1))))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 2))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are no ceased records and they match" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(taxCodeIncomes(2))))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 3))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotFound when there are no TaxCodeIncomes for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new BadRequestException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.ceasedMatchingIncomeSourcesForYear(nino, TaxYear().next)(FakeRequest())

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
        val mockIncomeService = mock[IncomeService]
        val income = uk.gov.hmrc.tai.model.domain.income.Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(None, Seq(
          OtherNonTaxCodeIncome(Profit, None, 100, "Profit")
        )))

        when(mockIncomeService.incomes(any(), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(income))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.income(nino, TaxYear())(FakeRequest())

        status(result) mustBe OK

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

    "return Ok" when {
      "a valid update amount is provided" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any())).thenReturn(Future.successful(IncomeUpdateSuccess))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(), any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeRequest)

        status(result) mustBe OK
      }
    }

    "return a bad request" when {
      "an invalid update amount is provided" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any())).thenReturn(Future.successful(InvalidAmount("")))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(), any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return internal server error" when {

      "income update exception has been thrown" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any())).thenReturn(Future.successful(IncomeUpdateFailed("Failed")))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(), any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR

      }

      "any exception has been thrown" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(), any(), any(), any())(any())).thenReturn(Future.failed(new RuntimeException("Error")))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(), any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR

      }
    }
  }
}