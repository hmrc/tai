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
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier, LockedException}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.calculation.{IncomeCategory, TaxBand, TotalTax, UkDividendsIncomeCategory}
import uk.gov.hmrc.tai.model.domain.taxAdjustments._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TotalTaxService
import uk.gov.hmrc.tai.util.NpsExceptions

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Random

class TotalTaxControllerSpec extends PlaySpec
  with MockitoSugar
  with NpsExceptions
  with MockAuthenticationPredicate{

  "totalTax" must {
    "return the total tax details for the given year" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(Matchers.eq(nino),Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.successful(totalTax))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        status(result) mustBe OK

        val expectedJson = Json.obj(
          "data" -> Json.obj(
            "amount" -> 1000,
            "incomeCategories" -> Json.arr(
              Json.obj(
                "incomeCategoryType" -> "UkDividendsIncomeCategory",
                "totalTax" -> 10,
                "totalTaxableIncome" -> 20,
                "totalIncome" -> 30,
                "taxBands" -> Json.arr(
                  Json.obj(
                    "bandType" -> "",
                    "code" -> "",
                    "income" -> 0,
                    "tax" -> 0,
                    "rate" -> 0
                  ),
                  Json.obj(
                    "bandType" -> "B",
                    "code" -> "BR",
                    "income" -> 10000,
                    "tax" -> 500,
                    "lowerBand" -> 5000,
                    "upperBand" -> 20000,
                    "rate" -> 10
                  )
                )
              )
            ),
          "reliefsGivingBackTax" -> Json.obj(
            "amount" -> 100,
            "taxAdjustmentComponents" -> Json.arr(
              Json.obj(
                "taxAdjustmentType" -> "EnterpriseInvestmentSchemeRelief",
                "taxAdjustmentAmount" -> 100
              )
            )
          ),
            "otherTaxDue" -> Json.obj(
              "amount" -> 100,
              "taxAdjustmentComponents" -> Json.arr(
                Json.obj(
                  "taxAdjustmentType" -> "ExcessGiftAidTax",
                  "taxAdjustmentAmount" -> 100
                )
              )
            ),
            "alreadyTaxedAtSource" -> Json.obj(
              "amount" -> 100,
              "taxAdjustmentComponents" -> Json.arr(
                Json.obj(
                  "taxAdjustmentType" -> "TaxOnBankBSInterest",
                  "taxAdjustmentAmount" -> 100
                )
              )
            )
          ),
          "links" -> Json.arr())
        contentAsJson(result) mustBe expectedJson
      }

    "return the bad request exception" when {
      "hod throws coding calculation error for cy+1" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(Matchers.eq(nino),Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.failed(new BadRequestException(CodingCalculationCYPlusOne)))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        status(result) mustBe BAD_REQUEST
      }

      "hod throws bad request for cy" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(Matchers.eq(nino),Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.failed(new BadRequestException("Cannot perform a Coding Calculation for CY")))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        status(result) mustBe BAD_REQUEST
      }
    }

    "return Locked exception" when {
      "hod throws locked exception" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(Matchers.eq(nino),Matchers.eq(TaxYear()))(any()))
          .thenReturn(Future.failed(new LockedException("Account is locked")))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        val ex = the[LockedException] thrownBy Await.result(result, 5 seconds)
        ex.message mustBe "Account is locked"
      }
    }

    "return NOT AUTHORISED" when {
      "the user is not logged in" in {
        val sut = createSUT(mock[TotalTaxService], notLoggedInAuthenticationPredicate)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        ScalaFutures.whenReady(result.failed) { e =>
          e mustBe a[MissingBearerToken]
        }
      }
    }
  }

  val nino = new Generator(new Random).nextNino

  private implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))

  val reliefsGivingBackTax = Some(TaxAdjustment(100, Seq(TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100))))
  val otherTaxDue = Some(TaxAdjustment(100, Seq(TaxAdjustmentComponent(ExcessGiftAidTax, 100))))
  val alreadyTaxedAtSource = Some(TaxAdjustment(100, Seq(TaxAdjustmentComponent(TaxOnBankBSInterest, 100))))

  val totalTax = TotalTax(BigDecimal(1000), Seq(
    IncomeCategory(UkDividendsIncomeCategory, 10, 20, 30, Seq(
      TaxBand(bandType = "", code = "", income = 0, tax = 0, lowerBand = None, upperBand = None, rate = 0),
      TaxBand(bandType = "B", code = "BR", income = 10000, tax = 500, lowerBand = Some(5000), upperBand = Some(20000), rate = 10)))),
    reliefsGivingBackTax, otherTaxDue, alreadyTaxedAtSource)

  private def createSUT(totalTaxService: TotalTaxService, authentication: AuthenticationPredicate =
                        loggedInAuthenticationPredicate) = new TotalTaxController(totalTaxService, authentication)

}