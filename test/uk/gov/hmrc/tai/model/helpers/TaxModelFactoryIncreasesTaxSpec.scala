/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.helpers

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps.NpsIabdSummary


class TaxModelFactoryIncreasesTaxSpec extends PlaySpec with TaxModelFactoryTestData {

  "groupItemsThatIncreaseTax" must {
    "return object of components which increase the tax" when {
      "passed default arguments" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(incomes = None)

        increasesTax mustBe None
      }

      "passed only total liability" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(totalLiability = totalLiabil, incomes = None)

        increasesTax mustBe Some(IncreasesTax(
          incomes = None,
          benefitsFromEmployment = Some(TaxComponent(1000, 0, "", List(IabdSummary(29, "Dummy", 1000, Some(1), Some(1), None)))),
          total = 1000))
      }

      "passed only Income Source" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(None, income, None, None)

        increasesTax mustBe None
      }

      "passed only Income" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(incomes = incomesObject)

        increasesTax mustBe Some(IncreasesTax(Some(
          Incomes(
            TaxCodeIncomes(None, None, None, None, hasDuplicateEmploymentNames = false, 50000, 40000, 1000.0),
            NoneTaxCodeIncomes(None, None, None, None, None, None, None, None, None, None, 20000),
            30000)),
          None, 30000))
      }

      "passed only npsEmployments" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(incomes = None, npsEmployments = employments)

        increasesTax mustBe None
      }

      "passed all arguments" in {
        val increasesTax = createSUT.groupItemsThatIncreaseTax(totalLiabil, income, incomesObject, employments)

        increasesTax mustBe Some(IncreasesTax(
          incomes = Some(Incomes(
            TaxCodeIncomes(None, None, None, None, hasDuplicateEmploymentNames = false, 50000, 40000, 1000.0),
            NoneTaxCodeIncomes(None, None, None, None, None, None, None, None, None, None, 20000),
            30000)),
          benefitsFromEmployment = Some(TaxComponent(
            1000, 0, "", List(IabdSummary(29, "Dummy", 1000, Some(1), Some(1), None)
            ))),
          total = 31000))
      }
    }


  }

  private val employments = Some(List(npsEmployment))

  private val taxCodeIncomes = TaxCodeIncomes(hasDuplicateEmploymentNames = false,
    totalIncome = BigDecimal(50000),
    totalTaxableIncome = BigDecimal(40000),
    totalTax = BigDecimal(1000.0))

  private val noneTaxCodeIncomes = NoneTaxCodeIncomes(totalIncome = BigDecimal(20000))

  private val incomesObject = Some(Incomes(taxCodeIncomes, noneTaxCodeIncomes, BigDecimal(30000)))

  private val genericIabd = NpsIabdSummary(
    amount = Some(1000),
    `type` = Some(29),
    npsDescription = Some("Dummy"),
    employmentId = Some(1),
    estimatedPaySource = Some(1)
  )

  private val totalLiabil = Some(
    totalLiab.copy(nonSavings = Some(genericNpsTax.copy(allowReliefDeducts = Some(genericNpsComponent.copy(
      iabdSummaries = Some(List(genericIabd)))))))
  )

  private val income = Some(List(incomeSource))

  def createSUT = new SUT

  class SUT extends TaxModelFactory

}
