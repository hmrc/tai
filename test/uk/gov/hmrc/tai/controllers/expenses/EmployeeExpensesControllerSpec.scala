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

package uk.gov.hmrc.tai.controllers.expenses

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.{BadRequestException, HttpResponse, NotFoundException}
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateExpensesRequest, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.service.expenses.EmployeeExpensesService
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class EmployeeExpensesControllerSpec extends BaseSpec {

  private val mockEmployeeExpensesService = mock[EmployeeExpensesService]

  private def controller(authentication: AuthJourney = loggedInAuthenticationAuthJourney) =
    new EmployeeExpensesController(authentication, mockEmployeeExpensesService, cc)

  private val iabd = 56
  val grossAmount = 100
  private val iabdUpdateExpensesRequest = IabdUpdateExpensesRequest(1, grossAmount)
  private val taxYear = 2017

  private val validNpsIabd = List(
    NpsIabdRoot(
      nino = nino.withoutSuffix,
      `type` = 56,
      grossAmount = Some(100)
    )
  )

  private val validJson = Json.toJson(validNpsIabd)

  "updateEmployeeExpensesData" must {

    val updateIabdEmployeeExpenseInternet =
      UpdateIabdEmployeeExpense(grossAmount, Some(EmployeeExpensesController.internet))

    "return NO CONTENT" when {
      "a valid update amount is provided and a OK response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseInternet), any())(
              any(),
              any()
            )
        )
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val result = controller().updateEmployeeExpensesData(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a NO CONTENT response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseInternet), any())(
              any(),
              any()
            )
        )
          .thenReturn(Future.successful(HttpResponse(204, "")))

        val result = controller().updateEmployeeExpensesData(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a ACCEPTED response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseInternet), any())(
              any(),
              any()
            )
        )
          .thenReturn(Future.successful(HttpResponse(202, "")))

        val result = controller().updateEmployeeExpensesData(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }
    }

    "return BAD REQUEST" when {
      "invalid update amount is provided" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(""))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseInternet), any())(
              any(),
              any()
            )
        )
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val result = controller().updateEmployeeExpensesData(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return INTERNAL SERVER ERROR" when {
      "flat rate expenses update exception has been thrown" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseInternet), any())(
              any(),
              any()
            )
        )
          .thenReturn(Future.successful(HttpResponse(500, "")))

        val result = controller().updateEmployeeExpensesData(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "updateWorkingFromHomeExpenses" must {

    val updateIabdEmployeeExpenseWFH =
      UpdateIabdEmployeeExpense(grossAmount, Some(EmployeeExpensesController.workingFromHome))

    "return NO CONTENT" when {
      "a valid update amount is provided and a OK response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseWFH), any())(any(), any())
        )
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val result = controller().updateWorkingFromHomeExpenses(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a NO CONTENT response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseWFH), any())(any(), any())
        )
          .thenReturn(Future.successful(HttpResponse(204, "")))

        val result = controller().updateWorkingFromHomeExpenses(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a ACCEPTED response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseWFH), any())(any(), any())
        )
          .thenReturn(Future.successful(HttpResponse(202, "")))

        val result = controller().updateWorkingFromHomeExpenses(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe NO_CONTENT
      }
    }

    "return BAD REQUEST" when {
      "invalid update amount is provided" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(""))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseWFH), any())(any(), any())
        )
          .thenReturn(Future.successful(HttpResponse(200, "")))

        val result = controller().updateWorkingFromHomeExpenses(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return INTERNAL SERVER ERROR" when {
      "flat rate expenses update exception has been thrown" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(
          mockEmployeeExpensesService
            .updateEmployeeExpensesData(any(), any(), any(), meq(updateIabdEmployeeExpenseWFH), any())(any(), any())
        )
          .thenReturn(Future.successful(HttpResponse(500, "")))

        val result = controller().updateWorkingFromHomeExpenses(nino, TaxYear(), iabd)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  "getEmployeeExpensesData" must {

    "return OK and valid json" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockEmployeeExpensesService.getEmployeeExpenses(any(), any(), any()))
        .thenReturn(Future.successful(validNpsIabd))

      val result = controller().getEmployeeExpensesData(nino, taxYear, iabd)(fakeRequest)

      status(result) mustBe OK

      contentAsJson(result) mustBe validJson
    }

    "return BadRequest when bad request exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockEmployeeExpensesService.getEmployeeExpenses(any(), any(), any()))
        .thenReturn(Future.failed(new BadRequestException("")))

      val result = controller().getEmployeeExpensesData(nino, taxYear, iabd)(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return NotFound when not found exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockEmployeeExpensesService.getEmployeeExpenses(any(), any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result = controller().getEmployeeExpensesData(nino, taxYear, iabd)(fakeRequest)

      status(result) mustBe NOT_FOUND
    }

    "return an Exception when an exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockEmployeeExpensesService.getEmployeeExpenses(any(), any(), any()))
        .thenReturn(Future.failed(new Exception("")))

      val result = controller().getEmployeeExpensesData(nino, taxYear, iabd)(fakeRequest)

      intercept[Exception] {
        result mustBe an[Exception]
      }
    }
  }
}
