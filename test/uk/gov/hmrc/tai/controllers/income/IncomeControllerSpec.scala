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

package uk.gov.hmrc.tai.controllers.income

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{IncomeService, TaxAccountService}

import scala.concurrent.Future
import scala.util.Random

class IncomeControllerSpec extends PlaySpec
    with MockitoSugar
    with TaxCodeIncomeSourceAPIFormatters
    with MockAuthenticationPredicate{

  "untaxedInterest" must {
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
    "return Not Found" when {
      "Nil is returned by income service" in {
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(),Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.successful(Seq.empty[TaxCodeIncome]))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }

      "a Not Found Exception occurs" in {
        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(),Matchers.eq(TaxYear().next))(any()))
          .thenReturn(Future.failed(new NotFoundException("Error")))

        val SUT = createSUT(mockIncomeService)
        val result = SUT.taxCodeIncomesForYear(nino, TaxYear().next)(FakeRequest())

        status(result) mustBe NOT_FOUND
      }
    }


    "return Ok with tax code incomes" when {
      "a list of tax code incomes is returned by income service" in {
        val taxCodeIncomes = Seq(TaxCodeIncome(EmploymentIncome, Some(1), BigDecimal(1100),
          "EmploymentIncome", "1150L", "Employer1", Week1Month1BasisOperation, Live, BigDecimal(0),BigDecimal(0),BigDecimal(0)),
          TaxCodeIncome(EmploymentIncome, Some(2), BigDecimal(0),
            "EmploymentIncome", "1100L", "Employer2", OtherBasisOperation, PotentiallyCeased, BigDecimal(321.12),BigDecimal(0),BigDecimal(0)))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.taxCodeIncomes(any(),Matchers.eq(TaxYear().next))(any()))
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

  "incomes" must {
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
                "incomeComponentType" -> "Profit" ,
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

    "return Ok" when {
      "a valid update amount is provided" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(),any(),any(),any())(any())).thenReturn(Future.successful(IncomeUpdateSuccess))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(),any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeRequest)

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
        when(mockIncomeService.updateTaxCodeIncome(any(),any(),any(),any())(any())).thenReturn(Future.successful(InvalidAmount("")))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(),any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return internal server error" when {
      "any exception has been thrown" in {

        val employmentId = 1

        val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)

        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
          .withHeaders(("content-type", "application/json"))

        val mockIncomeService = mock[IncomeService]
        when(mockIncomeService.updateTaxCodeIncome(any(),any(),any(),any())(any())).thenReturn(Future.failed(new RuntimeException("Error")))

        val mockTaxAccountService = mock[TaxAccountService]
        when(mockTaxAccountService.version(any(),any())(any())).thenReturn(Future.successful(Some(1)))

        val SUT = createSUT(mockIncomeService, mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR

      }
    }
  }

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  private val nino = new Generator(new Random).nextNino

  private val untaxedInterest = UntaxedInterest(UntaxedInterestIncome, None, 123, "Untaxed Interest", Seq.empty[BankAccount])

  private def createSUT(incomeService: IncomeService = mock[IncomeService],
                        taxAccountService: TaxAccountService = mock[TaxAccountService],
                        authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
                        new IncomeController(incomeService, taxAccountService, authentication)
}