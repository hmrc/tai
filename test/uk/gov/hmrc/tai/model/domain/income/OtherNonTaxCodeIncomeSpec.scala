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

package uk.gov.hmrc.tai.model.domain.income

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsArray, JsNull, JsObject, Json}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.OtherNonTaxCodeIncome.otherNonTaxCodeIncomeHipToggleOffReads

import scala.util.Random

class OtherNonTaxCodeIncomeSpec extends PlaySpec {
  private val nino: Nino = new Generator(new Random).nextNino
  "otherNonTaxCodeIncomeReads" must {
    "return empty sequence" when {
      "there is no total liability present in tax account" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino
        )
        json.as[Seq[OtherNonTaxCodeIncome]](otherNonTaxCodeIncomeHipToggleOffReads) mustBe empty
      }

      "total liability is null in tax account" in {
        val json = Json.obj(
          "taxAccountId"   -> "id",
          "nino"           -> nino.nino,
          "totalLiability" -> JsNull
        )
        json.as[Seq[OtherNonTaxCodeIncome]](otherNonTaxCodeIncomeHipToggleOffReads) mustBe empty
      }
    }

    "return non-tax-code incomes" when {
      "non-tax-code-incomes are present" in {
        val json = taxAccountJsonWithIabds(
          npsIabdSummaries(
            1,
            Seq(19, 20, 21, 22, 23, 24, 25, 26, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 74, 75, 76, 77, 78, 79, 80,
              81, 82, 83, 84, 85, 86, 87, 88, 89, 94, 116, 123, 125),
            100
          )
        )

        json.as[Seq[OtherNonTaxCodeIncome]](otherNonTaxCodeIncomeHipToggleOffReads) mustBe Seq(
          OtherNonTaxCodeIncome(NonCodedIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Commission, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherIncomeEarned, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherIncomeNotEarned, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PartTimeEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Tips, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OtherEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(CasualEarnings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignDividendIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignPropertyIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignInterestAndOtherSavings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForeignPensionsAndOtherIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(StatePension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(OccupationalPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PublicServicesPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(ForcesPension, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PersonalPensionAnnuity, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(Profit, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(BankOrBuildingSocietyInterest, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(UkDividend, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(UnitTrust, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(StockDividend, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(NationalSavings, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(SavingsBond, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(PurchasedLifeAnnuities, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(UntaxedInterestIncome, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(IncapacityBenefit, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(JobSeekersAllowance, Some(1), 100, "desc"),
          OtherNonTaxCodeIncome(EmploymentAndSupportAllowance, Some(1), 100, "desc")
        )
      }
    }
  }

  private def npsIabdSummaries(empId: Int, types: Seq[Int], amount: Int): Seq[JsObject] =
    types.map { tp =>
      Json.obj(
        "amount"             -> amount,
        "type"               -> tp,
        "npsDescription"     -> "desc",
        "employmentId"       -> empId,
        "estimatesPaySource" -> 1
      )
    }

  private def taxAccountJsonWithIabds(
    incomeIabdSummaries: Seq[JsObject] = Seq.empty[JsObject],
    allowReliefIabdSummaries: Seq[JsObject] = Seq.empty[JsObject]
  ): JsObject =
    Json.obj(
      "taxAccountId" -> "id",
      "nino"         -> nino.nino,
      "totalLiability" -> Json.obj(
        "nonSavings" -> Json.obj(
          "totalIncome" -> Json.obj(
            "iabdSummaries" -> JsArray(incomeIabdSummaries)
          ),
          "allowReliefDeducts" -> Json.obj(
            "iabdSummaries" -> JsArray(allowReliefIabdSummaries)
          )
        )
      )
    )

}
