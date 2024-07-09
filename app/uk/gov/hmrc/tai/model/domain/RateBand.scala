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

import play.api.libs.json.{JsArray, JsValue}

case class RateBand(income: BigDecimal, rate: BigDecimal)

object RateBand {
  def incomeAndRateBands(json: JsValue): Seq[RateBand] = {
    val bands = (json \ "totalLiability" \ "nonSavings" \ "taxBands").asOpt[JsArray]
    val details = bands.map(_.value.collect {
      case js if (js \ "income").asOpt[BigDecimal].isDefined =>
        RateBand((js \ "income").as[BigDecimal], (js \ "rate").as[BigDecimal])
    })

    details match {
      case Some(rateBands) => rateBands.toSeq.sortBy(-_.rate)
      case None            => Seq.empty[RateBand]
    }
  }
}
