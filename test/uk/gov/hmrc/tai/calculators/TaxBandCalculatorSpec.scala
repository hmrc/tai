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

package uk.gov.hmrc.tai.calculators

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.TaxBand

class TaxBandCalculatorSpec extends PlaySpec {

  "Tax Band Calculator" should {

    "return a list of tax bands" when {
      "given an empty old tax band list" in {
        sut.recreateTaxBands(None, Nil, Nil, 0) mustBe Nil
      }

      "given starting point as 0" in {
        val newTaxBands = sut.recreateTaxBands(Some(10000), oldTaxBands = taxBandInput, Nil, 0)

        newTaxBands.size mustBe 3
        newTaxBands(0).get.income mustBe Some(10000)
        newTaxBands(0).get.tax mustBe Some(2000)

        newTaxBands(1).get.income mustBe Some(0)
        newTaxBands(1).get.tax mustBe Some(0)

        newTaxBands(2).get.income mustBe Some(0)
        newTaxBands(2).get.tax mustBe Some(0)
      }

      "given starting point is 25000 and is between 2 initial tax bands" in {
        val newTaxBands = sut.recreateTaxBands(Some(10000), oldTaxBands = taxBandInput, Nil, 25000)

        newTaxBands.size mustBe 3
        newTaxBands(0).get.income mustBe Some(5000)
        newTaxBands(0).get.tax mustBe Some(1000)

        newTaxBands(1).get.income mustBe Some(5000)
        newTaxBands(1).get.tax mustBe Some(2000)

        newTaxBands(2).get.income mustBe Some(0)
        newTaxBands(2).get.tax mustBe Some(0)
      }

      "given starting point is 145000 that lies in the second band limits" in {
        val newTaxBands = sut.recreateTaxBands(Some(10000), oldTaxBands = taxBandInput, Nil, 145000)

        newTaxBands.size mustBe 3
        newTaxBands(0).get.income mustBe Some(0)
        newTaxBands(0).get.tax mustBe Some(0)

        newTaxBands(1).get.income mustBe Some(5000)
        newTaxBands(1).get.tax mustBe Some(2000)

        newTaxBands(2).get.income mustBe Some(5000)
        newTaxBands(2).get.tax mustBe Some(2250)
      }

      "given starting point is 5000 that lies in the band limits of all tax bands" in {
        val newTaxBands = sut.recreateTaxBands(Some(200000), oldTaxBands = taxBandInput, Nil, 5000)

        newTaxBands.size mustBe 3
        newTaxBands(0).get.income mustBe Some(25000)
        newTaxBands(0).get.tax mustBe Some(5000)

        newTaxBands(1).get.income mustBe Some(120000)
        newTaxBands(1).get.tax mustBe Some(48000)

        newTaxBands(2).get.income mustBe Some(55000)
        newTaxBands(2).get.tax mustBe Some(24750)
      }

      "given starting point is 200 and new result income and tax is none" in {
        val newTaxBands = sut.recreateTaxBands(Some(10), oldTaxBands = List(TaxBand(upperBand = Some(200))), Nil, 50)

        newTaxBands(0).get.income mustBe None
        newTaxBands(0).get.tax mustBe None
      }

    }

    "return income" when {
      "given an empty tax band" in {
        val taxBand = TaxBand()
        sut.getIncomeForThisBand(taxBand, 0, 0) mustBe 0
      }

      "given upper band is not None" in {
        val taxBand = TaxBand(upperBand = Some(100))
        sut.getIncomeForThisBand(taxBand, 0, 0) mustBe 0
      }

      "given starting point is greater than upper band" in {
        val taxBand = TaxBand(upperBand = Some(100))
        sut.getIncomeForThisBand(taxBand, 0, 200) mustBe 0
      }

      "given starting point and upper band as None" in {
        val taxBand = TaxBand(lowerBand = Some(200))
        sut.getIncomeForThisBand(taxBand, 0, 50) mustBe 0
      }

      "given starting point is not between lower and upper band" in {
        val taxBand = TaxBand(upperBand = Some(200), lowerBand = Some(100))
        sut.getIncomeForThisBand(taxBand, 10, 50) mustBe 10
      }
    }

    "return calculated income and tax" when {
      "given an empty tax band" in {
        val taxBand = TaxBand()
        val expectedResult: (Option[BigDecimal], Option[BigDecimal]) = (Some(0), Some(0))

        sut.recreateTaxBand(taxBand, 0, 0) mustBe expectedResult
      }

      "given lower band is none" in {
        val taxBand = TaxBand(upperBand = Some(200))
        val expectedResult: (Option[BigDecimal], Option[BigDecimal]) = (None, None)
        sut.recreateTaxBand(taxBand, 10, 50) mustBe expectedResult
      }

      "given lower band is defined" in {
        val taxBand = TaxBand(upperBand = Some(20000), lowerBand = Some(10000), rate = Some(60))
        val expectedResult: (Option[BigDecimal], Option[BigDecimal]) = (Some(1000), Some(600))
        sut.recreateTaxBand(taxBand, 1000, 5000) mustBe expectedResult
      }
    }

    "Return a list of sorted tax bands" when {
      "Given no old tax bands" in {
        sut.recreateTaxBandsNewTaxableIncome(Some(25000), None) mustBe None
      }

      "Given no new taxable income" in {
        sut.recreateTaxBandsNewTaxableIncome(None, Some(taxBandInput)) mustBe Some(taxBandInput)
      }

      "Given both old tax bands and new taxable income" in {
        val newTaxBands = sut.recreateTaxBandsNewTaxableIncome(Some(25000), Some(taxBandInput))

        newTaxBands.get.size mustBe 3
        newTaxBands.get(0).income mustBe Some(25000)
        newTaxBands.get(0).tax mustBe Some(5000)

        newTaxBands.get(1).income mustBe Some(0)
        newTaxBands.get(1).tax mustBe Some(0)

        newTaxBands.get(2).income mustBe Some(0)
        newTaxBands.get(2).tax mustBe Some(0)
      }
    }
  }

  def sut = TaxBandCalculator

  val taxBandInput = List(
    TaxBand(
      income = Some(BigDecimal(30000)),
      tax = Some(BigDecimal(6000)),
      lowerBand = Some(BigDecimal(0)),
      upperBand = Some(BigDecimal(30000)),
      rate = Some(BigDecimal(20))),
    TaxBand(
      income = Some(BigDecimal(30000)),
      tax = Some(BigDecimal(12000)),
      lowerBand = Some(BigDecimal(30000)),
      upperBand = Some(BigDecimal(150000)),
      rate = Some(BigDecimal(40))
    ),
    TaxBand(
      income = None,
      tax = None,
      lowerBand = Some(BigDecimal(150000)),
      upperBand = None,
      rate = Some(BigDecimal(45)))
  )
}
