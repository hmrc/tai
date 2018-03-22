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

package uk.gov.hmrc.tai.calculators

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.TaxBand

class PotentialUnderpaymentCalculatorSpec extends PlaySpec {

  "calculatePotentialUnderpayment" should {

    "return no potentialUnderpayment" when {

      "totalTax is None" in {
        val result = SUT.calculatePotentialUnderpayment(totalTax = None, taxBands = taxBandList)
        result mustBe None
      }

      "taxBands is None" in {
        val result = SUT.calculatePotentialUnderpayment(totalTax = Some(23), taxBands = None)
        result mustBe None
      }

      "when totalTaxFromTaxBands is less than totalTaxAmount" in {
        val result = SUT.calculatePotentialUnderpayment(totalTax = Some(10000), taxBands = taxBandList)
        result mustBe None
      }

      "when totalTaxFromTaxBands is equal to totalTaxAmount" in {
        val result = SUT.calculatePotentialUnderpayment(totalTax = Some(3), taxBands = taxBandList)
        result mustBe None
      }
    }

    "return a potentialUnderpayment value" when {
      "when totalTaxFromTaxBands is greater than totalTaxAmount" in {
        val result = SUT.calculatePotentialUnderpayment(totalTax = Some(2), taxBands = taxBandList)
        result mustBe Some(1)
      }

    }
  }

  private val SUT = PotentialUnderpaymentCalculator
  private val taxBand = TaxBand(tax = Some(1))
  private val noneTaxBand = TaxBand(None)
  private val taxBandList = Some(List(taxBand, taxBand, taxBand, noneTaxBand))

}
