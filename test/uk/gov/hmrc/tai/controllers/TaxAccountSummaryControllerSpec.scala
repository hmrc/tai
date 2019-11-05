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

package uk.gov.hmrc.tai.controllers

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, _}
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.TaxAccountSummary
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TaxAccountSummaryService
import uk.gov.hmrc.tai.util.NpsExceptions

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Random

class TaxAccountSummaryControllerSpec
    extends PlaySpec with MockitoSugar with NpsExceptions with MockAuthenticationPredicate {

  "taxAccountSummaryForYear" must {
    "return the tax summary for the given year" when {
      "tax year is CY+1" in {
        val mockTaxAccountSummaryService = mock[TaxAccountSummaryService]
        when(mockTaxAccountSummaryService.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear().next))(any()))
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
        when(mockTaxAccountSummaryService.taxAccountSummary(Matchers.eq(nino), Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.failed(new LockedException("Account is locked")))

        val sut = createSUT(mockTaxAccountSummaryService)
        val result = sut.taxAccountSummaryForYear(nino, TaxYear())(FakeRequest())
        val ex = the[LockedException] thrownBy Await.result(result, 5.seconds)
        ex.message mustBe "Account is locked"
      }
    }
  }

  val taxAccountSummary = TaxAccountSummary(1111, 0, 12.34, 0, 0, 0, 0)
  val taxAccountSummaryForYearCY1 = TaxAccountSummary(2222, 1, 56.78, 100.00, 43.22, 200, 100)
  private def createSUT(
    taxAccountSummaryService: TaxAccountSummaryService,
    authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new TaxAccountSummaryController(taxAccountSummaryService, authentication)

}
