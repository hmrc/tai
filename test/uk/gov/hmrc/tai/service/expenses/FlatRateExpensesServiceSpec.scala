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

package uk.gov.hmrc.tai.service.expenses

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.DesConnector
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.response.{ExpensesUpdateFailure, ExpensesUpdateSuccess}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class FlatRateExpensesServiceSpec extends PlaySpec
  with MockitoSugar
  with MockAuthenticationPredicate {

  private implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  private val mockDesConnector = mock[DesConnector]

  private val service = new FlatRateExpensesService(desConnector = mockDesConnector)

  private val nino = new Generator(new Random).nextNino

  "updateFlatRateExpensesAmount" must {

    "return ExpensesUpdateSuccess" when {
      "success response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(),any(),any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateSuccess))

        Await.result(service.updateFlatRateExpensesAmount(nino, TaxYear(), 1, 100), 5 seconds) mustBe ExpensesUpdateSuccess
      }
    }

    "return ExpensesUpdateFailure" when {
      "failure response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(),any(),any(),any(),any(),any())(any()))
          .thenReturn(Future.successful(ExpensesUpdateFailure))

        Await.result(service.updateFlatRateExpensesAmount(nino, TaxYear(), 1, 100), 5 seconds) mustBe ExpensesUpdateFailure
      }
    }
  }

}