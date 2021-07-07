/*
 * Copyright 2021 HM Revenue & Customs
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
import org.mockito.Mockito.{when, _}
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.http.{BadRequestException, HttpResponse}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.model.UpdateIabdEmployeeExpense
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class EmployeeExpensesServiceSpec extends BaseSpec with ScalaFutures {

  private val mockDesConnector = mock[DesConnector]
  private val mockNpsConnector = mock[NpsConnector]
  private val mockIabdConnector = mock[IabdConnector]
  private val mockFeaturesToggle = mock[FeatureTogglesConfig]

  private val service = new EmployeeExpensesService(
    desConnector = mockDesConnector,
    npsConnector = mockNpsConnector,
    iabdConnector = mockIabdConnector,
    featureTogglesConfig = mockFeaturesToggle)

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

  "updateEmployeeExpensesData" must {

    "return 200" when {
      "success response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(200)))

        when(mockFeaturesToggle.desUpdateEnabled).thenReturn(true)

        Await
          .result(service.updateEmployeeExpensesData(nino, TaxYear(), 1, updateIabdEmployeeExpense, iabd), 5 seconds)
          .status mustBe 200
      }
    }

    "return 500" when {
      "failure response from des connector" in {
        when(mockDesConnector.updateExpensesDataToDes(any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(Future.successful(HttpResponse(500)))

        when(mockFeaturesToggle.desUpdateEnabled).thenReturn(true)

        Await
          .result(service.updateEmployeeExpensesData(nino, TaxYear(), 1, updateIabdEmployeeExpense, iabd), 5 seconds)
          .status mustBe 500
      }
    }
  }

  "getEmployeeExpensesData" must {

    "return a list of NpsIabds" when {

      "success response from des connector when desEnabled is true" in {
        val mockDesConnector = mock[DesConnector]
        when(mockDesConnector.getIabdsForTypeFromDes(any(), any(), any())(any()))
          .thenReturn(Future.successful(validNpsIabd))

        val mockFeaturesToggle = mock[FeatureTogglesConfig]
        when(mockFeaturesToggle.desEnabled).thenReturn(true)

        val mockNpsConnector = mock[NpsConnector]
        val mockIabdConnector = mock[IabdConnector]

        val service = new EmployeeExpensesService(
          desConnector = mockDesConnector,
          npsConnector = mockNpsConnector,
          iabdConnector = mockIabdConnector,
          featureTogglesConfig = mockFeaturesToggle)

        val result = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe validNpsIabd

        verify(mockNpsConnector, times(0))
          .getIabdsForType(nino, taxYear, iabd)

        verify(mockDesConnector, times(1))
          .getIabdsForTypeFromDes(nino, taxYear, iabd)
      }

      "return exception" when {
        "failed response from des connector" in {
          when(mockFeaturesToggle.desEnabled).thenReturn(true)
          when(mockDesConnector.getIabdsForTypeFromDes(any(), any(), any())(any()))
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
