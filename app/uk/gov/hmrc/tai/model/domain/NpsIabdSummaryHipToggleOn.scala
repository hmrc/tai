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

object NpsIabdSummaryHipToggleOn {

  // TODO: DDCNL-9376 Duplicate reads
  val iabdsFromTotalLiabilityReads: Reads[Seq[NpsIabdSummary]] = (json: JsValue) => {
    val categories =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val totalIncomeList = totalLiabilityIabdsOff(json, "totalIncomeDetails", categories)
    val allowReliefDeductsList = totalLiabilityIabdsOff(json, "allowanceReliefDeductionsDetails", categories)
    JsSuccess(totalIncomeList ++ allowReliefDeductsList)
  }

  // TODO: DDCNL-9376 Duplicate reads
  private def totalLiabilityIabdsOff(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] = {
    val iabdJsArray = categories.flatMap { category =>
      (json \ "totalLiabilityDetails" \ category \ subPath \ "summaryIABDEstimatedPayDetailsList").asOpt[JsArray]
    }
    iabdJsArray.flatMap(_.value) collect {
      case json if (json \ "type").asOpt[(String, Int)](readsTypeTuple).isDefined =>
        val fullType = (json \ "type").as[String]
        val (description, componentType) = parseTypeOrException(fullType)
        val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
        val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        NpsIabdSummary(componentType, employmentId, amount, description)
    }
  }
}
