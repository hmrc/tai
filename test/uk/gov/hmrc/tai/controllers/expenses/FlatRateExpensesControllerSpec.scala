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

package uk.gov.hmrc.tai.controllers.expenses

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.IabdEditDataRequest
import uk.gov.hmrc.tai.model.domain.response.{ExpensesUpdateFailure, ExpensesUpdateSuccess}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.expenses.FlatRateExpensesService

import scala.concurrent.Future
import scala.util.Random

class FlatRateExpensesControllerSpec extends PlaySpec
    with MockitoSugar
    with MockAuthenticationPredicate {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private val mockFlatRateExpensesService = mock[FlatRateExpensesService]

  private def controller(authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new FlatRateExpensesController(authentication, flatRateExpensesService = mockFlatRateExpensesService)

  private val nino = new Generator(new Random).nextNino
  private val iabdEditDataRequest = IabdEditDataRequest(version = 1, newAmount = 100)

  "updateFlatRateExpensesAmount" must {

    "return OK" when {
      "a valid update amount is provided" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdEditDataRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesAmount(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateSuccess))

        val result = controller().updateFlatRateExpensesAmount(nino,TaxYear())(fakeRequest)

        status(result) mustBe NO_CONTENT
      }
    }

    "return BAD REQUEST" when {
      "invalid update amount is provided" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(""))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesAmount(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateSuccess))

        val result = controller().updateFlatRateExpensesAmount(nino,TaxYear())(fakeRequest)

        status(result) mustBe BAD_REQUEST
      }
    }

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), JsNull)
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesAmount(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateSuccess))

        val result = controller(notLoggedInAuthenticationPredicate).updateFlatRateExpensesAmount(nino,TaxYear())(fakeRequest)

        whenReady(result.failed) {
          e => e mustBe a[MissingBearerToken]
        }
      }
    }

    "return INTERNAL SERVER ERROR" when {
      "flat rate expenses update exception has been thrown" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdEditDataRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesAmount(any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateFailure))

        val result = controller().updateFlatRateExpensesAmount(nino,TaxYear())(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "any exception has been thrown" in {
        val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), Json.toJson(iabdEditDataRequest))
          .withHeaders(("content-type", "application/json"))

        when(mockFlatRateExpensesService.updateFlatRateExpensesAmount(any(),any(),any(),any())(any()))
          .thenReturn(Future.failed(new RuntimeException("Error")))

        val result = controller().updateFlatRateExpensesAmount(nino,TaxYear())(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

}
