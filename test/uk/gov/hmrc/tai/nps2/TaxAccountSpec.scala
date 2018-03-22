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

package uk.gov.hmrc.tai.nps2

import data.NpsData
import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.nps2.TaxObject.Type._
import uk.gov.hmrc.tai.model.nps2.{NpsEmployment, NpsFormatter, TaxAccount, TaxBand}
import uk.gov.hmrc.tai.model.enums.BasisOperation


class TaxAccountSpec extends PlaySpec with NpsFormatter {

  "TaxAccount format" should {
    val sut = createSut

    "successfully convert json from nps into TaxDetail." in {
      sut.taxObjects.keySet mustBe Set(NonSavings, UkDividends)

      val nonsavingsObject = sut.taxObjects.values.toList.head
      nonsavingsObject.totalTax mustBe Some(60050)
      nonsavingsObject.totalIncome mustBe Some(165000)
      nonsavingsObject.totalTaxableIncome mustBe Some(165000)
    }

    "successfully write TaxAccount object into json for FE." in {
      val npsJson = NpsData.getFETaxAccountJson

      Json.toJson[TaxAccount](sut) mustBe npsJson
    }
  }

  "TaxAccount format for multiple allowance , benefit and deduction" should {
    "successfully read json" in {
      val json = NpsData.getNpsTaxAccountMultipleAllowancesJson
      val taxAccountObject = json.as[TaxAccount]

      taxAccountObject.taxObjects.keySet mustBe Set(NonSavings, UkDividends)

      val dividends = taxAccountObject.taxObjects.values.toList.last
      dividends.taxBands.filter(_.bandType.contains("pa")) mustBe List(TaxBand(bandType = Some("pa"), income = 1500, tax = 0, rate = 0))

      val nonSavings = taxAccountObject.taxObjects.values.toList.head
      nonSavings.taxBands.filter(_.bandType.contains("pa")) mustBe List(TaxBand(bandType = Some("pa"), income = 10000, tax = 0, rate = 0))
    }
  }

  "TaxAccount" should {
    "update income" when {
      "passed employments with sequenceNo matches with employmentId" in {
        val sut = createSut
        val employment = NpsEmployment(employerName = None,
          sequenceNumber = 2,
          isPrimary = true,
          worksNumber = Some("ABCD"),
          districtNumber = 1,
          cessationPay = Some(0),
          start = LocalDate.now())

        val taxAccount = sut.withEmployments(Seq(employment))
        taxAccount.incomes.head.worksNumber mustBe Some("ABCD")
        taxAccount.incomes.head.employmentRecord mustBe Some(employment)
      }
    }

    "not update income" when {
      "passed employments with sequenceNo doesn't matches with employmentId" in {
        val sut = createSut
        val employment = NpsEmployment(employerName = None,
          sequenceNumber = 3,
          isPrimary = true,
          worksNumber = Some("ABCD"),
          districtNumber = 1,
          cessationPay = Some(0),
          start = LocalDate.now())

        val taxAccount = sut.withEmployments(Seq(employment))
        taxAccount.incomes.head.worksNumber mustBe None
        taxAccount.incomes.head.employmentRecord mustBe None
      }
    }

    "return same tax-code" when {
      "basis operation is None" in {
        val sut = createSut
        val taxCode = sut.getOperatedTaxCode("NT", None)
        taxCode mustBe "NT"
      }

      "basis operation is Week1Month1 and taxCode is NT" in {
        val sut = createSut
        val taxCode = sut.getOperatedTaxCode("NT", Some(BasisOperation.Week1Month1))
        taxCode mustBe "NT"
      }

      "basis operation is other than Week1Month1" in {
        val sut = createSut
        val taxCode = sut.getOperatedTaxCode("NT", Some(BasisOperation.Week1Month1NotOperated))
        taxCode mustBe "NT"
      }
    }

    "append X in tax-code" when {
      "basis operation is Week1Month1 and taxCode is not NT" in {
        val sut = createSut
        val taxCode = sut.getOperatedTaxCode("1150", Some(BasisOperation.Week1Month1))
        taxCode mustBe "1150 X"
      }
    }
  }

  private def createSut = NpsData.getNpsTaxAccountJson.as[TaxAccount]
}
