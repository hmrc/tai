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

package uk.gov.hmrc.tai.factory

import play.api.libs.json.{JsArray, JsNull, JsObject, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.tai.TaxYear

object TaxAccountHistoryFactory {

  def basicIncomeSourcesJson(nino: Nino, year: TaxYear = TaxYear()): JsObject =
    templateJson(nino, year, Json.arr(incomeSourceJson), Json.obj())

  def basicTotalLiabilityJson(nino: Nino, year: TaxYear = TaxYear()): JsObject =
    templateJson(nino, year, Json.arr(), totalLiabilityJson)

  def combinedIncomeSourcesTotalLiabilityJson(nino: Nino, year: TaxYear = TaxYear()): JsObject =
    templateJson(nino, year, Json.arr(incomeSourceJson), totalLiabilityJson)

  private def templateJson(nino: Nino, year: TaxYear, incomeSources: JsArray, totalLiability: JsObject): JsObject =
    Json.obj(
      "taxAccountId"             -> 7,
      "date"                     -> "02/08/2018",
      "nino"                     -> nino.toString,
      "noCYEmployment"           -> false,
      "taxYear"                  -> year.toString,
      "previousTaxAccountId"     -> 6,
      "previousYearTaxAccountId" -> 1,
      "nextTaxAccountId"         -> JsNull,
      "nextYearTaxAccountId"     -> JsNull,
      "totalEstTax"              -> 16956,
      "inYearCalcResult"         -> 1,
      "inYearCalcAmount"         -> 0,
      "totalLiability"           -> totalLiability,
      "incomeSources"            -> incomeSources
    )

  private def totalLiabilityJson: JsObject =
    Json.obj(
      "nonSavings"       -> nonSavingLiabilityJson,
      "untaxedInterest"  -> JsNull,
      "bankInterest"     -> JsNull,
      "ukDividends"      -> JsNull,
      "foreignInterest"  -> JsNull,
      "foreignDividends" -> JsNull,
      "basicRateExtensions" -> Json.obj(
        "npsDescription"               -> JsNull,
        "amount"                       -> 0,
        "type"                         -> JsNull,
        "iabdSummaries"                -> JsNull,
        "sourceAmount"                 -> JsNull,
        "personalPensionPayment"       -> JsNull,
        "giftAidPayments"              -> JsNull,
        "personalPensionPaymentRelief" -> JsNull,
        "giftAidPaymentsRelief"        -> JsNull
      ),
      "reliefsGivingBackTax" -> JsNull,
      "otherTaxDue"          -> JsNull,
      "alreadyTaxedAtSource" -> JsNull,
      "addTaxRefunded"       -> JsNull,
      "lessTaxReceived"      -> JsNull,
      "totalLiability"       -> 3030
    )

  private def incomeSourceJson: JsObject =
    Json.obj(
      "employmentId"                     -> 1,
      "employmentType"                   -> 1,
      "employmentStatus"                 -> 1,
      "employmentTaxDistrictNumber"      -> 754,
      "employmentPayeRef"                -> "employmentPayeRef",
      "pensionIndicator"                 -> false,
      "otherIncomeSourceIndicator"       -> false,
      "jsaIndicator"                     -> false,
      "name"                             -> "incomeSourceName",
      "taxCode"                          -> "1035L",
      "basisOperation"                   -> 1,
      "potentialUnderpayment"            -> JsNull,
      "totalInYearAdjustment"            -> 0,
      "inYearAdjustmentIntoCY"           -> 0,
      "inYearAdjustmentIntoCYPlusOne"    -> 0,
      "inYearAdjustmentFromPreviousYear" -> 0,
      "actualPUPCodedInCYPlusOneTaxYear" -> 0,
      "allowances"                       -> Json.arr(personalAllowance8105),
      "deductions"                       -> Json.arr(carBenefitDeduction),
      "payAndTax"                        -> Json.obj()
    )

  private def carBenefitDeduction: JsObject =
    Json.obj(
      "npsDescription" -> "car benfit",
      "amount"         -> 2000,
      "type"           -> 8,
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount"              -> 2000,
          "type"                -> 31,
          "npsDescription"      -> "Car Benefit",
          "employmentId"        -> 1,
          "defaultEstimatedPay" -> JsNull,
          "estimatedPaySource"  -> JsNull
        )
      ),
      "sourceAmount" -> 2000
    )

  private def personalAllowance8105: JsObject =
    Json.obj(
      "npsDescription" -> "Personal Allowance",
      "amount"         -> 11850,
      "type"           -> 11,
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount"         -> 11850,
          "type"           -> 118,
          "npsDescription" -> "Personal Allowance (PA)",
          "employmentId"   -> JsNull
        )
      ),
      "sourceAmount" -> 11850
    )

  private def nonSavingLiabilityJson: JsObject =
    Json.obj(
      "totalIncome"        -> totalIncomeLiabilityJson,
      "allowReliefDeducts" -> allowReliefLiabilityJson,
      "totalTax"           -> 3030,
      "totalTaxableIncome" -> 15150,
      "taxBands" -> Json.arr(
        Json.obj(
          "bandType"    -> "B",
          "taxCode"     -> "BR",
          "isBasicRate" -> true,
          "income"      -> 15150,
          "tax"         -> 3030,
          "lowerBand"   -> 0,
          "upperBand"   -> 34500,
          "rate"        -> 20
        ),
        Json.obj(
          "bandType"    -> "D0",
          "taxCode"     -> "D0",
          "isBasicRate" -> false,
          "income"      -> JsNull,
          "tax"         -> JsNull,
          "lowerBand"   -> 34500,
          "upperBand"   -> 150000,
          "rate"        -> 40
        ),
        Json.obj(
          "bandType"    -> "D1",
          "taxCode"     -> "D1",
          "isBasicRate" -> false,
          "income"      -> JsNull,
          "tax"         -> JsNull,
          "lowerBand"   -> 150000,
          "upperBand"   -> 0,
          "rate"        -> 45
        )
      )
    )

  private def totalIncomeLiabilityJson: JsObject =
    Json.obj(
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount"              -> 25000,
          "type"                -> 27,
          "npsDescription"      -> "New Estimated Pay",
          "employmentId"        -> 1,
          "defaultEstimatedPay" -> JsNull,
          "estimatedPaySource"  -> 16
        ),
        Json.obj(
          "amount"         -> 2000,
          "type"           -> 31,
          "npsDescription" -> "Car Benefit",
          "employmentId"   -> 1
        )
      ),
      "sourceAmount" -> JsNull
    )

  private def allowReliefLiabilityJson: JsObject =
    Json.obj(
      "npsDescription" -> JsNull,
      "amount"         -> 11850,
      "type"           -> JsNull,
      "iabdSummaries" -> Json.arr(
        Json.obj(
          "amount"         -> 11850,
          "type"           -> 118,
          "npsDescription" -> "Personal Allowance (PA)",
          "employmentId"   -> JsNull
        )
      ),
      "sourceAmount" -> JsNull
    )
}
