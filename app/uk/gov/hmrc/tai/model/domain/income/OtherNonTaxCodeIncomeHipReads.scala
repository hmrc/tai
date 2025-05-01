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

import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._

object OtherNonTaxCodeIncomeHipReads {
  implicit val format: Format[OtherNonTaxCodeIncome] = Json.format[OtherNonTaxCodeIncome]

  val otherNonTaxCodeIncomeReads: Reads[Seq[OtherNonTaxCodeIncome]] = (json: JsValue) => {
    val extractedIabds: Seq[NpsIabdSummary] =
      json.as[Seq[NpsIabdSummary]](NpsIabdSummary.iabdsFromTotalLiabilityReads)
    JsSuccess(nonTaxCodeIncomes(extractedIabds))
  }

  private def nonTaxCodeIncomes(iabds: Seq[NpsIabdSummary]): Seq[OtherNonTaxCodeIncome] =
    iabds collect {
      case iabd if nonTaxCodeIncomesMap.isDefinedAt(iabd.componentType) =>
        OtherNonTaxCodeIncome(
          nonTaxCodeIncomesMap(iabd.componentType),
          iabd.employmentId,
          iabd.amount,
          iabd.description
        )
    }

  private val nonTaxCodeIncomesMap: Map[Int, NonTaxCodeIncomeComponentType] = Map(
    19  -> NonCodedIncome,
    20  -> Commission,
    21  -> OtherIncomeEarned,
    22  -> OtherIncomeNotEarned,
    23  -> PartTimeEarnings,
    24  -> Tips,
    25  -> OtherEarnings,
    26  -> CasualEarnings,
    62  -> ForeignDividendIncome,
    63  -> ForeignPropertyIncome,
    64  -> ForeignInterestAndOtherSavings,
    65  -> ForeignPensionsAndOtherIncome,
    66  -> StatePension,
    67  -> OccupationalPension,
    68  -> PublicServicesPension,
    69  -> ForcesPension,
    70  -> PersonalPensionAnnuity,
    72  -> Profit,
    75  -> BankOrBuildingSocietyInterest,
    76  -> UkDividend,
    77  -> UnitTrust,
    78  -> StockDividend,
    79  -> NationalSavings,
    80  -> SavingsBond,
    81  -> PurchasedLifeAnnuities,
    82  -> UntaxedInterestIncome,
    83  -> IncapacityBenefit,
    84  -> JobSeekersAllowance,
    123 -> EmploymentAndSupportAllowance
  )

}
