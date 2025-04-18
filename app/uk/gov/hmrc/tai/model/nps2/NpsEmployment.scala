/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.nps2

import java.time.LocalDate

case class NpsEmployment(
  employerName: Option[String],
  isPrimary: Boolean,
  sequenceNumber: Int,
  worksNumber: Option[String],
  districtNumber: Int,
  iabds: List[Iabd] = Nil,
  cessationPay: Option[BigDecimal],
  start: LocalDate
)

object NpsEmployment {
  def unapply(
    n: NpsEmployment
  ): Option[(Option[String], Boolean, Int, Option[String], Int, List[Iabd], Option[BigDecimal], LocalDate)] =
    Some(
      (n.employerName, n.isPrimary, n.sequenceNumber, n.worksNumber, n.districtNumber, n.iabds, n.cessationPay, n.start)
    )
}
