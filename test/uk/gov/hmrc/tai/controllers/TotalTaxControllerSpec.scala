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
import uk.gov.hmrc.http.{BadRequestException, LockedException}
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.domain.calculation.{IncomeCategory, TaxBand, TotalTax, UkDividendsIncomeCategory}
import uk.gov.hmrc.tai.model.domain.taxAdjustments.*
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TotalTaxService
import uk.gov.hmrc.tai.util.{BaseSpec, NpsExceptions}

import scala.concurrent.Future

class TotalTaxControllerSpec extends BaseSpec with NpsExceptions {

  val reliefsGivingBackTax: Option[TaxAdjustment] = Some(
    TaxAdjustment(100, Seq(TaxAdjustmentComponent(EnterpriseInvestmentSchemeRelief, 100)))
  )
  val otherTaxDue: Option[TaxAdjustment] = Some(TaxAdjustment(100, Seq(TaxAdjustmentComponent(ExcessGiftAidTax, 100))))
  val alreadyTaxedAtSource: Option[TaxAdjustment] = Some(
    TaxAdjustment(100, Seq(TaxAdjustmentComponent(TaxOnBankBSInterest, 100)))
  )
  val taxReliefComponents: Option[TaxAdjustment] = Some(
    TaxAdjustment(100, Seq(TaxAdjustmentComponent(PersonalPensionPaymentRelief, 100)))
  )

  val totalTax: TotalTax = TotalTax(
    BigDecimal(1000),
    Seq(
      IncomeCategory(
        UkDividendsIncomeCategory,
        10,
        20,
        30,
        Seq(
          TaxBand(bandType = "", code = "", income = 0, tax = 0, lowerBand = None, upperBand = None, rate = 0),
          TaxBand(
            bandType = "B",
            code = "BR",
            income = 10000,
            tax = 500,
            lowerBand = Some(5000),
            upperBand = Some(20000),
            rate = 10
          )
        )
      )
    ),
    reliefsGivingBackTax,
    otherTaxDue,
    alreadyTaxedAtSource,
    None,
    taxReliefComponents
  )

  "totalTax" must {
    "return the total tax details for the given year" in {
      val mockTotalTaxService = mock[TotalTaxService]
      when(mockTotalTaxService.totalTax(meq(nino), meq(TaxYear()))(any()))
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
              "totalTax"           -> 10,
              "totalTaxableIncome" -> 20,
              "totalIncome"        -> 30,
              "taxBands" -> Json.arr(
                Json.obj(
                  "bandType" -> "",
                  "code"     -> "",
                  "income"   -> 0,
                  "tax"      -> 0,
                  "rate"     -> 0
                ),
                Json.obj(
                  "bandType"  -> "B",
                  "code"      -> "BR",
                  "income"    -> 10000,
                  "tax"       -> 500,
                  "lowerBand" -> 5000,
                  "upperBand" -> 20000,
                  "rate"      -> 10
                )
              )
            )
          ),
          "reliefsGivingBackTax" -> Json.obj(
            "amount" -> 100,
            "taxAdjustmentComponents" -> Json.arr(
              Json.obj(
                "taxAdjustmentType"   -> "EnterpriseInvestmentSchemeRelief",
                "taxAdjustmentAmount" -> 100
              )
            )
          ),
          "otherTaxDue" -> Json.obj(
            "amount" -> 100,
            "taxAdjustmentComponents" -> Json.arr(
              Json.obj(
                "taxAdjustmentType"   -> "ExcessGiftAidTax",
                "taxAdjustmentAmount" -> 100
              )
            )
          ),
          "alreadyTaxedAtSource" -> Json.obj(
            "amount" -> 100,
            "taxAdjustmentComponents" -> Json.arr(
              Json.obj(
                "taxAdjustmentType"   -> "TaxOnBankBSInterest",
                "taxAdjustmentAmount" -> 100
              )
            )
          ),
          "taxReliefComponent" -> Json.obj(
            "amount" -> 100,
            "taxAdjustmentComponents" -> Json.arr(
              Json.obj(
                "taxAdjustmentType"   -> "PersonalPensionPaymentRelief",
                "taxAdjustmentAmount" -> 100
              )
            )
          )
        ),
        "links" -> Json.arr()
      )
      contentAsJson(result) mustBe expectedJson
    }

    "return the bad request exception" when {
      "hod throws coding calculation error for cy+1" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.failed(new BadRequestException(CodingCalculationCYPlusOne)))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        status(result) mustBe BAD_REQUEST
      }

      "hod throws bad request for cy" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.failed(new BadRequestException("Cannot perform a Coding Calculation for CY")))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest())
        status(result) mustBe BAD_REQUEST
      }
    }

    "return Locked exception" when {
      "hod throws locked exception" in {
        val mockTotalTaxService = mock[TotalTaxService]
        when(mockTotalTaxService.totalTax(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.failed(new LockedException("Account is locked")))

        val sut = createSUT(mockTotalTaxService)
        val result = sut.totalTax(nino, TaxYear())(FakeRequest()).failed.futureValue

        result mustBe a[LockedException]

        result.getMessage mustBe "Account is locked"
      }
    }
  }

  private def createSUT(
    totalTaxService: TotalTaxService,
    authentication: AuthJourney = loggedInAuthenticationAuthJourney
  ) =
    new TotalTaxController(totalTaxService, authentication, cc)

}
