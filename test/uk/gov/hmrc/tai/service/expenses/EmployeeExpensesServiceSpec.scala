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

import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.libs.json.*
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{BadRequestException, HttpResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.*
import uk.gov.hmrc.tai.controllers.auth.AuthenticatedRequest
import uk.gov.hmrc.tai.model.UpdateIabdEmployeeExpense
import uk.gov.hmrc.tai.model.admin.HipGetIabdsExpensesToggle
import uk.gov.hmrc.tai.model.domain.IabdDetails
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class EmployeeExpensesServiceSpec extends BaseSpec {

  private val mockIabdConnector = mock[IabdConnector]
  private val mockFeatureFlagService = mock[FeatureFlagService]

  private val service =
    new EmployeeExpensesService(iabdConnector = mockIabdConnector, featureFlagService = mockFeatureFlagService)

  private val updateIabdEmployeeExpense = UpdateIabdEmployeeExpense(100, None)
  private val iabd = 56

  private val validNpsIabd: List[IabdDetails] = List(
    IabdDetails(
      nino = Some(nino.withoutSuffix),
      `type` = Some(iabd),
      grossAmount = Some(100.00)
    )
  )

  private val validNpsIabdJson =
    s"""
       |{
       |  "iabdDetails": [
       |    {
       |      "nationalInsuranceNumber": "$nino",
       |      "iabdSequenceNumber": 201700002,
       |      "taxYear": $taxYear,
       |      "type": "New Estimated Pay ($iabd)",
       |      "grossAmount": 100.00
       |    }
       |  ]
       |}
       |""".stripMargin

  private val taxYear = 2017

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockIabdConnector, mockFeatureFlagService)
  }

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

  "getEmployeeExpensesData for DES" must {

    "return a list of NpsIabds" when {

      "success response from des connector when desEnabled is true" in {
        val mockIabdConnector = mock[IabdConnector]
        when(mockIabdConnector.getIabdsForType(any(), any(), any())(any()))
          .thenReturn(Future.successful(validNpsIabd))
        when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
          Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
        )

        val service =
          new EmployeeExpensesService(iabdConnector = mockIabdConnector, featureFlagService = mockFeatureFlagService)

        val result = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe validNpsIabd

        verify(mockIabdConnector, times(1))
          .getIabdsForType(nino, taxYear, iabd)
      }

      "return exception" when {
        "failed response from des connector" in {
          when(mockIabdConnector.getIabdsForType(any(), any(), any())(any()))
            .thenReturn(Future.failed(new BadRequestException("")))
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = false))
          )

          val result = service.getEmployeeExpenses(nino, taxYear, iabd)

          intercept[Exception] {
            result.futureValue
          }
        }
      }
    }
  }

  "getEmployeeExpensesData for HIP" must {

    "return a list of NpsIabds" when {

      "success response from hip connector when HipGetIabdsExpensesToggle is true" in {
        val mockIabdConnector = mock[IabdConnector]
        when(mockIabdConnector.iabds(any(), any(), any())(any()))
          .thenReturn(Future.successful(Json.parse(validNpsIabdJson)))
        when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
          Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
        )

        val service =
          new EmployeeExpensesService(iabdConnector = mockIabdConnector, featureFlagService = mockFeatureFlagService)

        val result: Future[List[IabdDetails]] = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe validNpsIabd

        verify(mockIabdConnector, times(1))
          .iabds(nino, TaxYear(taxYear), Some(s"Flat-Rate-Job-Expenses-(0$iabd)"))
      }

      "success response from hip connector when iabdDetails is missing" in {
        val mockIabdConnector = mock[IabdConnector]
        when(mockIabdConnector.iabds(any(), any(), any())(any()))
          .thenReturn(Future.successful(Json.parse("{}")))
        when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
          Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
        )

        val service =
          new EmployeeExpensesService(iabdConnector = mockIabdConnector, featureFlagService = mockFeatureFlagService)

        val result: Future[List[IabdDetails]] = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe List.empty

        verify(mockIabdConnector, times(1))
          .iabds(nino, TaxYear(taxYear), Some(s"Flat-Rate-Job-Expenses-(0$iabd)"))
      }

      "success response from hip connector when iabdDetails is empty" in {
        val mockIabdConnector = mock[IabdConnector]
        when(mockIabdConnector.iabds(any(), any(), any())(any()))
          .thenReturn(Future.successful(Json.parse("""{"iabdDetails": []}""")))
        when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
          Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
        )

        val service =
          new EmployeeExpensesService(iabdConnector = mockIabdConnector, featureFlagService = mockFeatureFlagService)

        val result: Future[List[IabdDetails]] = service.getEmployeeExpenses(nino, taxYear, iabd)

        result.futureValue mustBe List.empty

        verify(mockIabdConnector, times(1))
          .iabds(nino, TaxYear(taxYear), Some(s"Flat-Rate-Job-Expenses-(0$iabd)"))
      }

      "return exception" when {
        "failed response from des connector" in {
          when(mockIabdConnector.iabds(any(), any(), any())(any()))
            .thenReturn(Future.failed(new BadRequestException("")))
          when(mockFeatureFlagService.get(eqTo[FeatureFlagName](HipGetIabdsExpensesToggle))).thenReturn(
            Future.successful(FeatureFlag(HipGetIabdsExpensesToggle, isEnabled = true))
          )

          val result = service.getEmployeeExpenses(nino, taxYear, iabd)

          intercept[Exception] {
            result.futureValue
          }
        }
      }
    }
  }

}
