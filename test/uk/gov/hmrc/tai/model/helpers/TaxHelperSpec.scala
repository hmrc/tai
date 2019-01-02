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

package uk.gov.hmrc.tai.model.helpers

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.calculators.TaxCalculator
import uk.gov.hmrc.tai.model.Tax
import uk.gov.hmrc.tai.model.nps.{NpsComponent, NpsTax}
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.tai.model.TaxBand

class TaxHelperSpec extends PlaySpec with MockitoSugar {

  "Tax Helper" should {
    "return tax object" when {
      "given none nps tax and tax paid at source" in {
        val sut = createSUT
        when(sut.taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(any(), any(), any())).thenReturn(None)
        sut.toTax(NpsTax(), None) mustBe Tax(None, None, None, None, None, None, None, None)
      }

      "given nps tax and tax paid at source" in {
        val sut = createSUT
        val taxBands = List(TaxBand(income = Some(1000), rate = Some(0)), TaxBand(income = Some(1000), rate = Some(20)))
        val oldTax = NpsTax(Some(NpsComponent(Some(1000))), Some(NpsComponent(Some(5000))),
          Some(6000), Some(200), Some(taxBands))
        val taxPaidAtSource = Some(BigDecimal(200))

        when(sut.taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(any(), any(),
          any())).thenReturn(Some(BigDecimal(1000)))

        val resTax = Tax(Some(1000), Some(6000), Some(200), None,None,None,None,
          Some(taxBands), Some(5000), Some(1000), Some(200))
        sut.toTax(oldTax, taxPaidAtSource) mustBe resTax
      }
    }

    "return adjusted tax" when {
      "given none nps tax, tax code, allowances, deductions and potential underpayment" in {
        val sut = createSUT
        when(sut.taxCalculator.adjustTaxData(any(), any(), any(), any())).thenReturn((None, None, None, None))
        when(sut.taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(any(), any(), any())).thenReturn(None)
        sut.toAdjustedTax(NpsTax(), None, None, None, None) mustBe Tax(None, None, None, None, None, None, None, None)
      }

      "given nps tax, tax code, allowances, deductions and potential underpayment" in {
        val sut = createSUT
        val taxBands = List(TaxBand(Some(1000), Some(0), None, Some(1500), Some(0)),
          TaxBand(Some(10000), Some(200), None, Some(11500), Some(20)))
        val oldTax = NpsTax(Some(NpsComponent(Some(100000))), Some(NpsComponent(Some(500))),
          Some(6000), Some(200), Some(taxBands))
        val taxCode = Some("BR")
        val potentialUnderPayment = Some(BigDecimal(100))

        when(sut.taxCalculator.adjustTaxData(any(), any(), any(), any())).thenReturn((Some(taxBands), oldTax.totalTax,
          oldTax.totalTaxableIncome, Some(BigDecimal(100))))

        when(sut.taxCalculator.actualTaxDueAssumingBasicRateAlreadyPaid(any(), any(),
          any())).thenReturn(Some(BigDecimal(1000)))

        val resTax = Tax(Some(100000), Some(6000), Some(200), Some(100),None,None,None, Some(taxBands), Some(100), Some(1000), None)
        sut.toAdjustedTax(oldTax, taxCode, Some(List(NpsComponent(Some(1000)))),
          Some(List(NpsComponent(Some(5000)))), potentialUnderPayment) mustBe resTax
      }
    }

  }

  private def createSUT = new SUT

  class SUT extends TaxHelper {
    override val taxCalculator: TaxCalculator = mock[TaxCalculator]
  }

}
