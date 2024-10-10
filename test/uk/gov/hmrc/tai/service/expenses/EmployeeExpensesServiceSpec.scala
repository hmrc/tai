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

package uk.gov.hmrc.tai.service.expenses

import org.mockito.ArgumentMatchers.any
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadRequestException, HttpResponse}
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.controllers.auth.AuthenticatedRequest
import uk.gov.hmrc.tai.model.UpdateIabdEmployeeExpense
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class EmployeeExpensesServiceSpec extends BaseSpec {

  private val mockIabdConnector = mock[IabdConnector]

  private val service = new EmployeeExpensesService(iabdConnector = mockIabdConnector)

  private val updateIabdEmployeeExpense = UpdateIabdEmployeeExpense(100, None)
  private val iabd = 56

  private val validNpsIabd: List[NpsIabdRoot] = List(
    NpsIabdRoot(
      nino = nino.withoutSuffix,
      `type` = iabd,
      grossAmount = Some(100.00)
    )
  )

  private val taxYear = 2017

  implicit val authenticatedRequest: AuthenticatedRequest[AnyContentAsEmpty.type] =
    AuthenticatedRequest(FakeRequest(), nino)

  "updateEmployeeExpensesData" must {

    "return 200" when {
      "success response from des connector" in {
        when(mockIabdConnector.updateExpensesData(any(), any(), any(), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(200, responseBody)))

        service
          .updateEmployeeExpensesData(nino, TaxYear(), 1, updateIabdEmployeeExpense, iabd)
          .futureValue
          .status mustBe 200
      }
    }

    "return 500" when {
      "failure response from des connector" in {
        when(mockIabdConnector.updateExpensesData(any(), any(), any(), any(), any(), any())(any(), any()))
          .thenReturn(Future.successful(HttpResponse(500, responseBody)))

        service
          .updateEmployeeExpensesData(nino, TaxYear(), 1, updateIabdEmployeeExpense, iabd)
          .futureValue
          .status mustBe 500
      }
    }
  }

  "getEmployeeExpensesData" must {

    "return a list of NpsIabds" when {

      "success response from des connector when desEnabled is true" in {
        val mockIabdConnector = mock[IabdConnector]
        when(mockIabdConnector.getIabdsForType(any(), any(), any())(any()))
          .thenReturn(Future.successful(validNpsIabd))

        val service = new EmployeeExpensesService(iabdConnector = mockIabdConnector)

        val result = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe validNpsIabd

        verify(mockIabdConnector, times(1))
          .getIabdsForType(nino, taxYear, iabd)
      }

      "return exception" when {
        "failed response from des connector" in {
          when(mockIabdConnector.getIabdsForType(any(), any(), any())(any()))
            .thenReturn(Future.failed(new BadRequestException("")))

          val result = service.getEmployeeExpenses(nino, taxYear, iabd)

          intercept[Exception] {
            result.futureValue
          }
        }
      }
    }
  }

}
