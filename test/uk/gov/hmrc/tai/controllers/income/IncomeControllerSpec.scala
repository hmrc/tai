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
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsNull, JsValue, Json}
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
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateResponse, IncomeUpdateSuccess, InvalidAmount}
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
  val expectedJsonEmpty = Json.obj(
    "data" -> Json.arr(),
    "links" -> Json.arr()
  )


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
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Live")(FakeRequest())

      status(result) mustBe OK

      val expectedIncomeSource = Json.toJson(IncomeSource(TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
        employment
      ))

      val expectedJson = Json.obj(
        "data" -> Json.arr(expectedIncomeSource),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return list of ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0))
        )))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Ceased")(FakeRequest())

      status(result) mustBe OK

      val expectedIncomeSource = Json.toJson(IncomeSource(TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
        employment
      ))

      val expectedJson = Json.obj(
        "data" -> Json.arr(expectedIncomeSource),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return list of potentially ceased and matched Employments & TaxCodeIncomes as IncomeSource for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0))
        )))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "PotentiallyCeased")(FakeRequest())

      status(result) mustBe OK

      val expectedIncomeSource = Json.toJson(IncomeSource(TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
        employment
      ))

      val expectedJson = Json.obj(
        "data" -> Json.arr(expectedIncomeSource),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return empty JSON when no records match" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(
          TaxCodeIncome(EmploymentIncome, None, BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0))
        )))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "PotentiallyCeased")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty

    }

    "return list of live and matched pension TaxCodeIncomes for a given year" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome", "Live")(FakeRequest())

      status(result) mustBe OK

      val expectedJson =
        Json.parse(
          """
            |{
            |    "data":[
            |       {
            |          "taxCodeIncome":{
            |             "componentType":"PensionIncome",
            |             "employmentId":1,
            |             "amount":1100,
            |             "description":"PensionIncome",
            |             "taxCode":"1150L",
            |             "name":"Employer1",
            |             "basisOperation":"Week1Month1BasisOperation",
            |             "status":"Live",
            |             "inYearAdjustmentIntoCY":0,
            |             "totalInYearAdjustment":0,
            |             "inYearAdjustmentIntoCYPlusOne":0
            |          },
            |          "employment":{
            |             "name":"company name",
            |             "payrollNumber":"888",
            |             "startDate":"2019-05-26",
            |             "annualAccounts":[
            |
            |             ],
            |             "taxDistrictNumber":"",
            |             "payeNumber":"",
            |             "sequenceNumber":1,
            |             "cessationPay":100,
            |             "hasPayrolledBenefit":false,
            |             "receivingOccupationalPension":true
            |          }
            |       }
            |    ],
            |    "links":[
            |
            |    ]
            | }
          """.stripMargin)

      contentAsJson(result) mustBe expectedJson
    }

    "return empty json when there are no matching live employments" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 99))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Live")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when there are no matching live pensions" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment.copy(sequenceNumber = 99))))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome", "Live")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when there are no TaxCodeIncome records for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment)))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome", "Live")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when there are no employment records for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[Employment]))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "PensionIncome", "Live")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when given an invalid or non-existent income type" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq(employment)))

      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "BananaIncome", "Live")(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Live")(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Live")(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new BadRequestException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.matchedTaxCodeIncomesForYear(nino, TaxYear().next, "EmploymentIncome", "Live")(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }

  "nonMatchingCeasedEmployments" must {

    val taxCodeIncomes = Seq(TaxCodeIncome(PensionIncome, Some(1), BigDecimal(1100),
      "PensionIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)),
      TaxCodeIncome(EmploymentIncome, Some(3), BigDecimal(0),
        "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Live, BigDecimal(321.12), BigDecimal(0), BigDecimal(0)))

    val employment = Employment("company name", Some("888"), new LocalDate(TaxYear().next.year, 5, 26),
      None, Nil, "", "", 2, Some(100), hasPayrolledBenefit = false, receivingOccupationalPension = true)

    "return list of non matching ceased employments when some employments do have an end date" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1, endDate = Some(new LocalDate(TaxYear().next.year, 8, 10))))

      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val ty = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, ty)(FakeRequest())

      status(result) mustBe OK

      val expectedJson = Json.obj(
        "data" -> Json.arr(employment.copy(sequenceNumber = 1, endDate = Some(new LocalDate(TaxYear().next.year, 8, 10)))),
        "links" -> Json.arr()
      )

      contentAsJson(result) mustBe expectedJson
    }

    "return empty json when no employments have an end date" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(taxCodeIncomes))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val ty = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, ty)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when TaxCodeIncomes do not have an Id" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(
          TaxCodeIncome(EmploymentIncome, None, BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0))
        )))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val ty = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, ty)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty Json when there are no TaxCodeIncome records for a given nino" in {
      val employments = Seq(employment, employment.copy(sequenceNumber = 1))

      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(employments))

      val ty = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, ty)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return empty json when there are no employment records for a given nino" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.successful(Seq(
          TaxCodeIncome(EmploymentIncome, None, BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, Ceased, BigDecimal(321.12), BigDecimal(0), BigDecimal(0))
        )))

      val mockEmploymentService = mock[EmploymentService]
      when(mockEmploymentService.employments(any[Nino], any[TaxYear])(any[HeaderCarrier]))
        .thenReturn(Future.successful(Seq.empty[Employment]))

      val ty = TaxYear().next
      val sut = createSUT(incomeService = mockIncomeService, employmentService = mockEmploymentService, authentication = loggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, ty)(FakeRequest())

      status(result) mustBe OK
      contentAsJson(result) mustBe expectedJsonEmpty
    }

    "return NotAuthorized when the user is not logged in" in {
      val sut = createSUT(authentication = notLoggedInAuthenticationPredicate)
      val result = sut.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return NotFound when a NotFoundException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
        .thenReturn(Future.failed(new NotFoundException("Error")))

      val SUT = createSUT(mockIncomeService)
      val result = SUT.nonMatchingCeasedEmployments(nino, TaxYear().next)(FakeRequest())

      status(result) mustBe NOT_FOUND
    }

    "return BadRequest when a BadRequestException occurs" in {
      val mockIncomeService = mock[IncomeService]
      when(mockIncomeService.taxCodeIncomes(any(), Matchers.eq(TaxYear().next))(any()))
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

    "return a bad request" when {
      "an invalid update amount is provided" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.successful(InvalidAmount("")), mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe BAD_REQUEST
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
      }
    }

    "return internal server error" when {

      "income update exception has been thrown" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.successful(IncomeUpdateFailed("Failed")), mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
      }

      "any exception has been thrown" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.failed(new RuntimeException("Error")), mockTaxAccountService)
        val result = SUT.updateTaxCodeIncome(nino, TaxYear(), employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
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
    new IncomeController(incomeService, taxAccountService, employmentService, authentication)

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