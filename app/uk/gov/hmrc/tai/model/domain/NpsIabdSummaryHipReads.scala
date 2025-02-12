/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json.{JsArray, JsSuccess, JsValue, Reads}
import uk.gov.hmrc.tai.util.JsonHelper.{parseTypeOrException, readsTypeTuple}
import uk.gov.hmrc.tai.util.SequenceHelper

object NpsIabdSummaryHipReads {

  val iabdsFromTotalLiabilityReads: Reads[Seq[NpsIabdSummary]] = (json: JsValue) => {
    val categories =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val totalIncomeList = totalLiabilityIabds(json, "totalIncomeDetails", categories)
    val allowReliefDeductsList = totalLiabilityIabds(json, "allowanceReliefDeductionsDetails", categories)
    JsSuccess(totalIncomeList ++ allowReliefDeductsList)
  }

  def totalLiabilityIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] = {

    val parseSummary: PartialFunction[JsValue, NpsIabdSummary] = {
      case jsonValue if (jsonValue \ "type").asOpt[(String, Int)](readsTypeTuple).isDefined =>
        val fullType = (jsonValue \ "type").as[String]
        val (description, componentType) = parseTypeOrException(fullType)
        val employmentId = (jsonValue \ "employmentSequenceNumber").asOpt[Int]
        val amount = (jsonValue \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        NpsIabdSummary(componentType, employmentId, amount, description)
    }

    def extractItems(listName: String): Seq[NpsIabdSummary] =
      categories
        .flatMap(category => (json \ "totalLiabilityDetails" \ category \ subPath \ listName).asOpt[JsArray])
        .flatMap(_.value)
        .collect(parseSummary)

    val allItems = extractItems("summaryIABDDetailsList") ++ extractItems("summaryIABDEstimatedPayDetailsList")

    SequenceHelper.checkForDuplicates[NpsIabdSummary, (Option[Int], Int)](
      allItems,
      uniqueKey = item => (item.employmentId, item.componentType),
      keyDescription = { case (employmentId, componentType) =>
        s"employmentSequenceNumber: $employmentId and componentType: $componentType"
      }
    )

    allItems
  }
}
