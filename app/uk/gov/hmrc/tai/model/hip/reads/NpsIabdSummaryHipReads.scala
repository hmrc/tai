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
      JsSuccess {
        categories.flatMap { category =>
          listNames.flatMap { listName =>
            subPaths.flatMap { subPath =>
              (json \ "totalLiabilityDetails" \ category \ subPath \ listName)
                .asOpt[Seq[JsValue]]
                .getOrElse(Seq.empty)
                .flatMap(parseIabdSummary)
            }
          }
        }
      }
    }

  def totalLiabilityIabds(json: JsValue, subPath: String, categories: Seq[String]): Seq[NpsIabdSummary] =
    categories.flatMap { category =>
      listNames.flatMap { listName =>
        (json \ "totalLiabilityDetails" \ category \ subPath \ listName)
          .asOpt[Seq[JsValue]]
          .getOrElse(Seq.empty)
          .flatMap(parseIabdSummary)
      }
    }

  def filteredIabdsFromTotalLiabilityReads(predicate: Int => Boolean): Reads[Seq[NpsIabdSummary]] =
    Reads { json =>
      JsSuccess {
        categories.flatMap { category =>
          listNames.flatMap { listName =>
            subPaths.flatMap { subPath =>
              (json \ "totalLiabilityDetails" \ category \ subPath \ listName)
                .asOpt[Seq[JsValue]]
                .getOrElse(Seq.empty)
                .flatMap { js =>
                  parseIabd(js).filter(iabd => predicate(iabd.componentType))
                }
            }
          }
        }
      }
    }

  private def parseIabdSummary(json: JsValue): Option[NpsIabdSummary] =
    (json \ "type").validate[(String, Int)](readsTypeTuple).asOpt.map { case (desc, compType) =>
      val employmentId = (json \ "employmentSequenceNumber").asOpt[Int]
      val amount = (json \ "amount").asOpt[BigDecimal].getOrElse(BigDecimal(0))
      NpsIabdSummary(compType, employmentId, amount, desc)
    }

  private def parseIabd(json: JsValue): Option[NpsIabdSummary] =
    parseIabdSummary(json)
}
