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

package uk.gov.hmrc.tai.repositories

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.domain.calculation._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.{TaxAccountRepository, TotalTaxRepository}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TotalTaxRepositorySpec extends BaseSpec {

  "incomeCategories" must {
    "return the income categories that is being read from the taxAccountRepository" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(mockTaxAccountRepository.taxAccount(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(json))
      val sut = createSUT(mockTaxAccountRepository)
      val result = sut.incomeCategories(nino, TaxYear()).futureValue
      result must contain theSameElementsAs Seq(
        IncomeCategory(
          UkDividendsIncomeCategory,
          0,
          0,
          0,
          Seq(
            TaxBand(
              bandType = "B",
              code = "BR",
              income = 10000,
              tax = 500,
              lowerBand = Some(5000),
              upperBand = Some(20000),
              rate = 10)
          )
        ),
        IncomeCategory(ForeignDividendsIncomeCategory, 1000.23, 1000.24, 1000.25, Nil)
      )
    }
  }

  "taxFreeAllowance" must {
    "return the tax free allowance amount" in {
      val mockTaxAccountRepository = mock[TaxAccountRepository]
      when(mockTaxAccountRepository.taxAccount(meq(nino), meq(TaxYear()))(any()))
        .thenReturn(Future.successful(json))
      val sut = createSUT(mockTaxAccountRepository)
      val result = sut.taxFreeAllowance(nino, TaxYear()).futureValue

      result mustBe 100
    }
  }

  val json = Json.obj(
    "totalLiability" -> Json.obj(
      "ukDividends" -> Json.obj(
        "allowReliefDeducts" -> Json.obj(
          "amount" -> 100
        ),
        "totalIncome" -> Json.obj(),
        "taxBands" -> Json.arr(
          Json.obj(
            "bandType"  -> "B",
            "taxCode"      -> "BR",
            "income"    -> 10000,
            "tax"       -> 500,
            "lowerBand" -> 5000,
            "upperBand" -> 20000,
            "rate"      -> 10
          )
        )
      ),
      "foreignDividends" -> Json.obj(
        "totalTax"           -> 1000.23,
        "totalTaxableIncome" -> 1000.24,
        "totalIncome" -> Json.obj(
          "amount" -> 1000.25
        )
      )
    ))

  private def createSUT(taxAccountRepository: TaxAccountRepository) =
    new TotalTaxRepository(taxAccountRepository)

}
