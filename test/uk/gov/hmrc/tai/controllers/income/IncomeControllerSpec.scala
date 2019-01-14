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
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateResponse, IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{IncomeService, TaxAccountService}
import scala.concurrent.Future
import scala.util.Random

class IncomeControllerSpec extends PlaySpec
    with MockitoSugar
    with TaxCodeIncomeSourceAPIFormatters
    with MockAuthenticationPredicate{

  val employmentId = 1
  val mockTaxAccountService = generateMockAccountServiceWithAnyResponse
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

    "return Ok and invalidate the cache" when {
      "a valid update amount is provided" in {
        val wantedNumberOfInvocations = 1
        val mockTaxAccountService = generateMockAccountServiceWithAnyResponse
        val SUT = setup(Future.successful(IncomeUpdateSuccess), mockTaxAccountService)

        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe OK
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
      }
    }

    "return a bad request and do not invalidate the cache" when {
      "an invalid update amount is provided" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.successful(InvalidAmount("")), mockTaxAccountService)
        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe BAD_REQUEST
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
      }
    }

    "return internal server error and do not invalidate the cache" when {

      "income update exception has been thrown" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.successful(IncomeUpdateFailed("Failed")), mockTaxAccountService)
        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
      }

      "any exception has been thrown" in {
        val wantedNumberOfInvocations = 0
        val SUT = setup(Future.failed(new RuntimeException("Error")), mockTaxAccountService)
        val result = SUT.updateTaxCodeIncome(nino, TaxYear(),employmentId)(fakeTaxCodeIncomeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        verify(mockTaxAccountService, times(wantedNumberOfInvocations)).invalidateTaiCacheData()(any())
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

  private def fakeTaxCodeIncomeRequest: FakeRequest[JsValue] = {
    val updateTaxCodeIncomeRequest = UpdateTaxCodeIncomeRequest(1234)
    FakeRequest("POST", "/", FakeHeaders(), Json.toJson(updateTaxCodeIncomeRequest))
      .withHeaders(("content-type", "application/json"))
  }

  private def generateMockAccountServiceWithAnyResponse: TaxAccountService = {
    val mockTaxAccountService = mock[TaxAccountService]
    when(mockTaxAccountService.version(any(),any())(any())).thenReturn(Future.successful(Some(1)))
    mockTaxAccountService
  }

  private def setup(response: Future[IncomeUpdateResponse], mockTaxAccountService: TaxAccountService): IncomeController = {
    val mockIncomeService: IncomeService = {
      val mockIncomeService: IncomeService = mock[IncomeService]
      when(mockIncomeService.updateTaxCodeIncome(any(),any(),any(),any())(any())).thenReturn(response)
      mockIncomeService
    }
    createSUT(mockIncomeService, mockTaxAccountService)
  }
}