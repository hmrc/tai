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

package uk.gov.hmrc.tai.model

import play.api.libs.json.{JsPath, Writes}
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.CodingComponentAPIFormatters
import play.api.libs.functional.syntax._

final case class TaxFreeAmountComparison(previous: Seq[CodingComponent], next: Seq[CodingComponent])

object TaxFreeAmountComparison extends CodingComponentAPIFormatters {

  implicit val writes: Writes[TaxFreeAmountComparison] = (

    (JsPath \ "previous").write(Writes.seq[CodingComponent](codingComponentWrites)) and
    (JsPath \ "current").write(Writes.seq[CodingComponent](codingComponentWrites))

    )(unlift(TaxFreeAmountComparison.unapply))

}