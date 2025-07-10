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

package uk.gov.hmrc.tai.model.hip.reads

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNull, JsObject, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.OtherNonTaxCodeIncome

import scala.util.Random

class OtherNonTaxCodeIncomeHipReadsSpec extends PlaySpec {
  private val nino: Nino = new Generator(new Random).nextNino
  "otherNonTaxCodeIncomeReads" must {
    "return empty sequence" when {
      "there is no total liability present in tax account" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino
        )
        json.as[Seq[OtherNonTaxCodeIncome]](OtherNonTaxCodeIncomeHipReads.otherNonTaxCodeIncomeReads) mustBe empty
      }

      "total liability is null in tax account" in {
        val json = Json.obj(
          "taxAccountId"   -> "id",
          "nino"           -> nino.nino,
          "totalLiability" -> JsNull
        )
        json.as[Seq[OtherNonTaxCodeIncome]](OtherNonTaxCodeIncomeHipReads.otherNonTaxCodeIncomeReads) mustBe empty
      }
    }

    "return non-tax-code incomes" when {
      "non-tax-code-incomes are present" in {
        val json = Json
          .parse("""{
                   |   "nationalInsuranceNumber":"AA000003",
                   |   "taxAccountIdentifier":"id",
                   |   "taxYear":2023,
                   |   "totalLiabilityDetails":{
                   |      "nonSavings":{
                   |         "totalIncomeDetails":{
                   |            "type":"",
                   |            "summaryIABDDetailsList":[
                   |               
                   |            ],
                   |            "summaryIABDEstimatedPayDetailsList":[
                   |               {
                   |                  "amount":100,
                   |                  "type":"Non-Coded Income (019)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Commission (020)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Other Income (Earned) (021)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Other Income (Not Earned) (022)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Part Time Earnings (023)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Tips (024)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Other Earnings (025)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Casual Earnings (026)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Foreign Dividend Income (062)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Foreign Property Income (063)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Foreign Interest & Other Savings (064)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Foreign Pensions & Other Income (065)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"State Pension (066)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Occupational Pension (067)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Public Services Pension (068)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Forces Pension (069)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Personal Pension Annuity (070)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Lump Sum Deferral (071)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Profit (072)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Loss Brought Forward from earlier tax year (074)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Taxed Interest (075)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"UK Dividend (076)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Unit Trust (077)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Stock Dividend (078)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"National Savings (079)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Savings Bond (080)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Purchased Life Annuities (081)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Untaxed Interest (082)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Incapacity Benefit (083)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Job Seekers Allowance (084)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Other Benefit (085)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Trusts, Settlements & Estates at Trust Rate (086)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Trusts, Settlements & Estates at Basic Rate (087)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Trusts, Settlements & Estates at Lower Rate (088)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Trusts, Settlements & Estates at Non-payable Dividend Rate (089)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Chargeable Event Gain (094)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Higher Rate Adjustment (116)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Employment and Support Allowance (123)",
                   |                  "employmentSequenceNumber":1
                   |               },
                   |               {
                   |                  "amount":100,
                   |                  "type":"Bereavement Allowance (125)",
                   |                  "employmentSequenceNumber":1
                   |               }
                   |            ]
                   |         },
                   |         "allowanceReliefDeductionsDetails":{
                   |            "type":"",
                   |            "summaryIABDDetailsList":[
                   |               
                   |            ],
                   |            "summaryIABDEstimatedPayDetailsList":[
                   |               
                   |            ]
                   |         }
                   |      }
                   |   }
                   |}""".stripMargin)
          .as[JsObject]

        json.as[Seq[OtherNonTaxCodeIncome]](OtherNonTaxCodeIncomeHipReads.otherNonTaxCodeIncomeReads) mustBe Seq(
          OtherNonTaxCodeIncome(NonCodedIncome, Some(1), 100, "Non-Coded Income"),
          OtherNonTaxCodeIncome(Commission, Some(1), 100, "Commission"),
          OtherNonTaxCodeIncome(OtherIncomeEarned, Some(1), 100, "Other Income (Earned)"),
          OtherNonTaxCodeIncome(OtherIncomeNotEarned, Some(1), 100, "Other Income (Not Earned)"),
          OtherNonTaxCodeIncome(PartTimeEarnings, Some(1), 100, "Part Time Earnings"),
          OtherNonTaxCodeIncome(Tips, Some(1), 100, "Tips"),
          OtherNonTaxCodeIncome(OtherEarnings, Some(1), 100, "Other Earnings"),
          OtherNonTaxCodeIncome(CasualEarnings, Some(1), 100, "Casual Earnings"),
          OtherNonTaxCodeIncome(ForeignDividendIncome, Some(1), 100, "Foreign Dividend Income"),
          OtherNonTaxCodeIncome(ForeignPropertyIncome, Some(1), 100, "Foreign Property Income"),
          OtherNonTaxCodeIncome(ForeignInterestAndOtherSavings, Some(1), 100, "Foreign Interest & Other Savings"),
          OtherNonTaxCodeIncome(ForeignPensionsAndOtherIncome, Some(1), 100, "Foreign Pensions & Other Income"),
          OtherNonTaxCodeIncome(StatePension, Some(1), 100, "State Pension"),
          OtherNonTaxCodeIncome(OccupationalPension, Some(1), 100, "Occupational Pension"),
          OtherNonTaxCodeIncome(PublicServicesPension, Some(1), 100, "Public Services Pension"),
          OtherNonTaxCodeIncome(ForcesPension, Some(1), 100, "Forces Pension"),
          OtherNonTaxCodeIncome(PersonalPensionAnnuity, Some(1), 100, "Personal Pension Annuity"),
          OtherNonTaxCodeIncome(Profit, Some(1), 100, "Profit"),
          OtherNonTaxCodeIncome(BankOrBuildingSocietyInterest, Some(1), 100, "Taxed Interest"),
          OtherNonTaxCodeIncome(UkDividend, Some(1), 100, "UK Dividend"),
          OtherNonTaxCodeIncome(UnitTrust, Some(1), 100, "Unit Trust"),
          OtherNonTaxCodeIncome(StockDividend, Some(1), 100, "Stock Dividend"),
          OtherNonTaxCodeIncome(NationalSavings, Some(1), 100, "National Savings"),
          OtherNonTaxCodeIncome(SavingsBond, Some(1), 100, "Savings Bond"),
          OtherNonTaxCodeIncome(PurchasedLifeAnnuities, Some(1), 100, "Purchased Life Annuities"),
          OtherNonTaxCodeIncome(UntaxedInterestIncome, Some(1), 100, "Untaxed Interest"),
          OtherNonTaxCodeIncome(IncapacityBenefit, Some(1), 100, "Incapacity Benefit"),
          OtherNonTaxCodeIncome(JobSeekersAllowance, Some(1), 100, "Job Seekers Allowance"),
          OtherNonTaxCodeIncome(EmploymentAndSupportAllowance, Some(1), 100, "Employment and Support Allowance")
        )
      }
    }
  }
}
