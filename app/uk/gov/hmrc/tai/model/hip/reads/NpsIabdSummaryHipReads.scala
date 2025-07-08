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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.NpsIabdSummary
import uk.gov.hmrc.tai.util.JsonHelper.readsTypeTuple

object NpsIabdSummaryHipReads {

  val iabdsFromTotalLiabilityReads: Reads[Seq[NpsIabdSummary]] = Reads { json =>
    val categories =
      Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")

    val totalIncomeList = extractIabds(json, "totalIncomeDetails", categories)
    val allowanceReliefList = extractIabds(json, "allowanceReliefDeductionsDetails", categories)

    JsSuccess(totalIncomeList ++ allowanceReliefList)
  }

  def totalLiabilityIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] =
    extractIabds(json, subPath, categories)

  private def extractIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] = {
    val listNames = Seq("summaryIABDDetailsList", "summaryIABDEstimatedPayDetailsList")

    for {
      category <- categories
      listName <- listNames
      array <- (json \ "totalLiabilityDetails" \ category \ subPath \ listName)
                 .validateOpt[JsArray]
                 .getOrElse(None)
                 .toSeq
      element <- array.value
      iabd    <- parseIabdSummary(element)
    } yield iabd
  }

  private def parseIabdSummary(json: JsValue): Option[NpsIabdSummary] =
    (json \ "type").validate[(String, Int)](readsTypeTuple).asOpt.map { case (description, componentType) =>
      val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
      val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      NpsIabdSummary(componentType, employmentId, amount, description)
    }
}
