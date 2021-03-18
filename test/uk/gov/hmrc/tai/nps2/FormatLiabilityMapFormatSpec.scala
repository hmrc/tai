/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.nps2.{NpsFormatter, TaxDetail, TaxObject}

class FormatLiabilityMapFormatSpec extends PlaySpec with NpsFormatter {
  "Total Liability " should {
    "return Tax-Detail object with pa tax bands" when {
      "non-savings has been passed" in {
        val result = createJson("nonSavings")
        result.size mustBe 1

        val nonSavingTaxDetail = result.get(TaxObject.Type.NonSavings)
        nonSavingTaxDetail must not be None

        val paBandIncome = nonSavingTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe Some(11000)
      }

      "uk-dividends has been passed" in {
        val result = createJson("ukDividends")
        result.size mustBe 1

        val ukDividendsTaxDetail = result.get(TaxObject.Type.UkDividends)
        ukDividendsTaxDetail must not be None

        val paBandIncome = ukDividendsTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe Some(11000)
      }

      "Bank Interest has been passed" in {
        val result = createJson("bankInterest")
        result.size mustBe 1

        val bankInterestTaxDetail = result.get(TaxObject.Type.BankInterest)
        bankInterestTaxDetail must not be None

        val paBandIncome = bankInterestTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe Some(11000)
      }

      "Foreign Interest has been passed" in {
        val result = createJson("foreignInterest")
        result.size mustBe 1

        val foreignInterestTaxDetail = result.get(TaxObject.Type.ForeignInterest)
        foreignInterestTaxDetail must not be None

        val paBandIncome = foreignInterestTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe Some(11000)
      }

      "Foreign Dividends has been passed" in {
        val result = createJson("foreignDividends")
        result.size mustBe 1

        val foreignDividendsTaxDetail = result.get(TaxObject.Type.ForeignDividends)
        foreignDividendsTaxDetail must not be None

        val paBandIncome = foreignDividendsTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe Some(11000)
      }

      "non-savings has been passed with tax-bands" in {
        val npsJson =
          """
              {
                 "nonSavings": {
                     "taxBands": [
                       {
                         "bandType": "B",
                        "income": 19595,
                         "isBasicRate": true,
                         "lowerBand": 0,
                         "rate": 20,
                         "tax": 3919,
                         "taxCode": "BR",
                         "upperBand": 32000
                       }
                     ]
                 }
               }"""

        val result = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]
        result.size mustBe 1

        val nonSavingTaxDetail = result.get(TaxObject.Type.NonSavings)
        nonSavingTaxDetail must not be None

        val bands = nonSavingTaxDetail.map(_.taxBands)
        bands.size mustBe 1

        val paBandIncome = bands.get.find(_.bandType.contains("pa")).map(_.income)
        paBandIncome mustBe None
      }

    }

    "not return TaxDetail object" when {
      "null totalLiabilitySections has been passed" in {
        val npsJson =
          """
              {
                 "nonSavings": null,
                 "untaxedInterest": null,
                 "bankInterest": null,
                 "ukDividends": null,
                 "foreignInterest": null,
                 "foreignDividends": null
               }"""

        val result = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]
        result.size mustBe 0
      }
    }

    "not return pa bands" when {
      "untaxed interest has been passed without tax bands" in {
        val result = createJson("untaxedInterest")
        result.size mustBe 0
      }

      "untaxed interest has been passed with tax bands" in {
        val npsJson =
          """
              {
                 "untaxedInterest": {
                   "allowReliefDeducts": {
                     "amount": 11000,
                     "iabdSummaries": [
                       {
                         "amount": 11000
                       }
                     ]
                   },
                    "taxBands": [
                      {
                        "bandType": "B",
                       "income": 19595,
                        "isBasicRate": true,
                        "lowerBand": 0,
                        "rate": 20,
                        "tax": 3919,
                        "taxCode": "BR",
                        "upperBand": 32000
                      }
                    ]
                 }
               }"""

        val result = Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]
        result.size mustBe 1

        val untaxedInterestTaxDetail = result.get(TaxObject.Type.UntaxedInterest)
        untaxedInterestTaxDetail must not be None

        val paBandIncome = untaxedInterestTaxDetail.flatMap(_.taxBands.find(_.bandType.contains("pa")).map(_.income))
        paBandIncome mustBe None
      }
    }

  }

  private def createJson(liabilityType: String): Map[TaxObject.Type.Value, TaxDetail] = {
    val npsJson =
      s"""
              {
                 "$liabilityType": {
                   "allowReliefDeducts": {
                     "amount": 11000,
                     "iabdSummaries": [
                       {
                         "amount": 11000
                       }
                     ]
                   }
                 }
               }"""

    Json.parse(npsJson).as[Map[TaxObject.Type.Value, TaxDetail]]

  }

}
