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

package uk.gov.hmrc.tai.model.rti

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tai.model.tai.TaxYear

/*
 * The top-level successful response record from the Real Time Information
 * (RTI) Head of Duty (HoD)
 *
 * @param nino national insurance number of the individual, together with the
 *   [[taxYear]] this uniquely identifies a record
 */
case class RtiData(
  nino: String,
  taxYear: TaxYear,
  requestId: String,
  employments: List[RtiEmployment] = Nil
)

object RtiData {
  implicit val format: OFormat[RtiData] = Json.format[RtiData]

  def unapply(rti: RtiData): Option[(String, TaxYear, String, List[RtiEmployment])] =
    Some((rti.nino, rti.taxYear, rti.requestId, rti.employments))
}
