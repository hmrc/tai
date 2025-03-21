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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.{any, eq as meq}
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, *}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.domain.TaxAccountSummary
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TaxAccountSummaryService
import uk.gov.hmrc.tai.util.{BaseSpec, NpsExceptions}

import scala.concurrent.Future

class TaxAccountSummaryControllerSpec extends BaseSpec with NpsExceptions {

  val taxAccountSummary: TaxAccountSummary = TaxAccountSummary(1111, 0, 12.34, 0, 0, 0, 0)
  val taxAccountSummaryForYearCY1: TaxAccountSummary = TaxAccountSummary(2222, 1, 56.78, 100.00, 43.22, 200, 100)

  "taxAccountSummaryForYear" must {
    "return the tax summary for the given year" when {
      "tax year is CY+1" in {
        val mockTaxAccountSummaryService = mock[TaxAccountSummaryService]
        when(mockTaxAccountSummaryService.taxAccountSummary(meq(nino), meq(TaxYear().next))(any(), any()))
          .thenReturn(Future.successful(taxAccountSummaryForYearCY1))

        val sut = createSUT(mockTaxAccountSummaryService)
        val result = sut.taxAccountSummaryForYear(nino, TaxYear().next)(FakeRequest())
        status(result) mustBe OK

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "totalEstimatedTax"                  -> 2222,
            "taxFreeAmount"                      -> 1,
            "totalInYearAdjustmentIntoCY"        -> 56.78,
            "totalInYearAdjustment"              -> 100.00,
            "totalInYearAdjustmentIntoCYPlusOne" -> 43.22,
            "totalEstimatedIncome"               -> 200,
            "taxFreeAllowance"                   -> 100
          ),
          "links" -> Json.arr()
        )
        contentAsJson(result) mustBe expectedJson
      }
    }

    "return Locked exception" when {
      "nps throws locked exception" in {
        val mockTaxAccountSummaryService = mock[TaxAccountSummaryService]
        when(mockTaxAccountSummaryService.taxAccountSummary(meq(nino), meq(TaxYear()))(any(), any()))
          .thenReturn(Future.failed(new LockedException("Account is locked")))

        val sut = createSUT(mockTaxAccountSummaryService)

        val result = sut.taxAccountSummaryForYear(nino, TaxYear())(FakeRequest()).failed.futureValue

        result mustBe a[LockedException]

        result.getMessage mustBe "Account is locked"
      }
    }
  }

  private def createSUT(
    taxAccountSummaryService: TaxAccountSummaryService,
    authentication: AuthJourney = loggedInAuthenticationAuthJourney
  ) =
    new TaxAccountSummaryController(taxAccountSummaryService, authentication, cc)

}
