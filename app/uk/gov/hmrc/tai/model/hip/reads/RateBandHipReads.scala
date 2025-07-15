/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.tai.model.domain.RateBand

object RateBandHipReads {

  implicit val rateBandReads: Reads[RateBand] = Json.reads[RateBand]

  def incomeAndRateBands(json: JsValue): Seq[RateBand] =
    (json \ "totalLiabilityDetails" \ "nonSavings" \ "taxBandDetails")
      .validateOpt[List[RateBand]]
      .getOrElse(None)
      .getOrElse(Nil)
      .sortBy(-_.rate)
}
