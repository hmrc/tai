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

  private val categories =
    Seq("nonSavings", "untaxedInterest", "bankInterest", "ukDividends", "foreignInterest", "foreignDividends")

  private val listNames = Seq("summaryIABDDetailsList", "summaryIABDEstimatedPayDetailsList")

  private val subPaths = Seq("totalIncomeDetails", "allowanceReliefDeductionsDetails")

  val iabdsFromTotalLiabilityReads: Reads[Seq[NpsIabdSummary]] =
    Reads { json =>
      val extracted = categories.flatMap { category =>
        listNames.flatMap { listName =>
          (json \ "totalLiabilityDetails" \ category)
            .asOpt[JsObject]
            .toSeq
            .flatMap { categoryJson =>
              extractIabdsFromCategory(categoryJson, listName)
            }
        }
      }
      JsSuccess(extracted)
    }

  def totalLiabilityIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] =
    extractIabds(json, subPath, categories)

  private def extractIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] =
    for {
      category <- categories
      listName <- listNames
      array <- (json \ "totalLiabilityDetails" \ category \ subPath \ listName)
                 .asOpt[Seq[JsValue]]
                 .toSeq
      element <- array
      iabd    <- parseIabdSummary(element)
    } yield iabd

  private def extractIabdsFromCategory(categoryJson: JsObject, listName: String): Seq[NpsIabdSummary] =
    subPaths.flatMap { subPath =>
      (categoryJson \ subPath \ listName)
        .asOpt[Seq[JsValue]]
        .getOrElse(Seq.empty)
        .flatMap(parseIabdSummary)
    }

  private def parseIabdSummary(json: JsValue): Option[NpsIabdSummary] =
    (json \ "type").validate[(String, Int)](readsTypeTuple).asOpt.map { case (description, componentType) =>
      val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
      val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      NpsIabdSummary(componentType, employmentId, amount, description)
    }

  def filteredIabdsFromTotalLiabilityReads(predicate: Int => Boolean): Reads[Seq[NpsIabdSummary]] =
    Reads { json =>
      val extracted = categories.flatMap { category =>
        listNames.flatMap { listName =>
          (json \ "totalLiabilityDetails" \ category)
            .asOpt[JsObject]
            .toSeq
            .flatMap { categoryJson =>
              extractFilteredIabdsFromCategory(categoryJson, listName, predicate)
            }
        }
      }
      JsSuccess(extracted)
    }

  private def extractFilteredIabdsFromCategory(
    categoryJson: JsObject,
    listName: String,
    predicate: Int => Boolean
  ): Seq[NpsIabdSummary] =
    subPaths.flatMap { subPath =>
      (categoryJson \ subPath \ listName)
        .asOpt[Seq[JsValue]]
        .getOrElse(Seq.empty)
        .flatMap { json =>
          (json \ "type").validate[(String, Int)](readsTypeTuple).asOpt.collect {
            case (description, componentType) if predicate(componentType) =>
              val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
              val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
              NpsIabdSummary(componentType, employmentId, amount, description)
          }
        }
    }
}
