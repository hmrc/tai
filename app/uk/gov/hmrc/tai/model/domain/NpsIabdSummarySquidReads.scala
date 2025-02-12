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

object NpsIabdSummarySquidReads {
  val iabdsFromTotalLiabilityReads: Reads[Seq[NpsIabdSummary]] = (json: JsValue) => {
    val categories =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")
    val totalIncomeList = totalLiabilityIabds(json, "totalIncome", categories)
    val allowReliefDeductsList = totalLiabilityIabds(json, "allowReliefDeducts", categories)
    JsSuccess(totalIncomeList ++ allowReliefDeductsList)
  }

  def totalLiabilityIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] = {
    val iabdJsArray = categories.flatMap { category =>
      (json \ "totalLiability" \ category \ subPath \ "iabdSummaries").asOpt[JsArray]
    }

    iabdJsArray.flatMap(_.value) collect {
      case jsonValue if (jsonValue \ "type").asOpt[Int].isDefined =>
        val componentType = (jsonValue \ "type").as[Int]
        val employmentId = (jsonValue \ "employmentId").asOpt[Int]
        val amount = (jsonValue \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
        val description = (jsonValue \ "npsDescription").asOpt[String].getOrElse("")
        NpsIabdSummary(componentType, employmentId, amount, description)
    }
  }
}
