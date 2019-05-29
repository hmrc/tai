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

package uk.gov.hmrc.tai.controllers.expenses

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.IabdUpdateExpensesRequest
import uk.gov.hmrc.tai.service.expenses.FlatRateExpensesService

import scala.concurrent.Future
import scala.util.Random

class FlatRateExpensesControllerSpec extends PlaySpec
  with MockitoSugar
  with MockAuthenticationPredicate {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private val mockFlatRateExpensesService = mock[FlatRateExpensesService]

  private def controller(authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new FlatRateExpensesController(authentication, flatRateExpensesService = mockFlatRateExpensesService, cc)

  private val nino = new Generator(new Random).nextNino
  private val iabdUpdateExpensesRequest = IabdUpdateExpensesRequest(1, grossAmount = 100)

  private val taxYear = 2017

  private val validNpsIabd = List(
    NpsIabdRoot(
      nino = nino.withoutSuffix,
      `type` = 56,
      grossAmount = Some(100)
    )
  )

  private val validJson = Json.toJson(validNpsIabd)

  "updateFlatRateExpensesData" must {

    "return NO CONTENT" when {
      "a valid update amount is provided and a OK response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        val result = controller().updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a NO CONTENT response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(204)))

        val result = controller().updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        status(result) mustBe NO_CONTENT
      }

      "a valid update amount is provided and a ACCEPTED response is returned" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(202)))

        val result = controller().updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        status(result) mustBe NO_CONTENT
      }
    }

    "return BAD REQUEST" when {
      "invalid update amount is provided" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(""))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        val result = controller().updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), JsNull)
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        val result = controller(notLoggedInAuthenticationPredicate).updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        whenReady(result.failed) {
          e => e mustBe a[MissingBearerToken]
        }
      }
    }

    "return INTERNAL SERVER ERROR" when {
      "flat rate expenses update exception has been thrown" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdUpdateExpensesRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesData(any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(500)))

        val result = controller().updateFlatRateExpensesData(nino, TaxYear())(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "getFlatRateExpensesData" must {

    "return OK and valid json" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockFlatRateExpensesService.getFlatRateExpenses(any(), any()))
        .thenReturn(Future.successful(validNpsIabd))

      val result = controller().getFlatRateExpensesData(nino, taxYear)(fakeRequest)

      status(result) mustBe OK

      contentAsJson(result) mustBe validJson
    }

    "return BadRequest when bad request exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockFlatRateExpensesService.getFlatRateExpenses(any(), any()))
        .thenReturn(Future.failed(new BadRequestException("")))

      val result = controller().getFlatRateExpensesData(nino, taxYear)(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return NotFound when not found exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockFlatRateExpensesService.getFlatRateExpenses(any(), any()))
        .thenReturn(Future.failed(new NotFoundException("")))

      val result = controller().getFlatRateExpensesData(nino, taxYear)(fakeRequest)

      status(result) mustBe NOT_FOUND
    }

    "return an Exception when an exception thrown" in {
      val fakeRequest = FakeRequest("GET", "/", FakeHeaders(), any())

      when(mockFlatRateExpensesService.getFlatRateExpenses(any(), any()))
        .thenReturn(Future.failed(new Exception("")))

      val result = controller().getFlatRateExpensesData(nino, taxYear)(fakeRequest)

      intercept[Exception] {
        result mustBe an[Exception]
      }
    }
  }
}
