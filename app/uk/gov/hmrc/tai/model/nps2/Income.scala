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

package uk.gov.hmrc.tai.model.nps2

import org.joda.time.LocalDate
import org.slf4j._
import uk.gov.hmrc.tai.model.nps2.Income.{IncomeType, Status}
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation

case class Income(
  employmentId: Option[Int],
  isPrimary: Boolean,
  incomeType: IncomeType.Value,
  status: Status,
  taxDistrict: Option[Int],
  payeRef: String,
  name: String,
  worksNumber: Option[String],
  taxCode: String,
  potentialUnderpayment: BigDecimal,
  employmentRecord: Option[NpsEmployment],
  basisOperation: Option[BasisOperation] = None
)

object Income {
  implicit val log: Logger = LoggerFactory.getLogger(this.getClass)

  sealed trait Status {
    def code: Int
  }
  case object Live extends Status { val code = 1 }
  case object PotentiallyCeased extends Status { val code = 2 }
  case class Ceased(on: LocalDate) extends Status { val code = 3 }
  object Ceased extends Ceased(LocalDate.now())

  object Status {
    def apply(code: Option[Int], ceased: Option[LocalDate]): Status =
      (code, ceased) match {
        case (Some(Live.code), None)              => Live
        case (Some(Ceased.code), Some(date))      => Ceased(date)
        case (Some(PotentiallyCeased.code), None) => PotentiallyCeased
        case (x, Some(date)) => {
          log.warn(s"Suspect Income status: CODE:$x, ending $date, setting to ceased")
          Ceased(date)
        }
        case (x, None) => {
          log.warn(s"Suspect Income status: CODE:$x, no ending date, setting to live")
          Live
        }
      }
  }

  object IncomeType extends Enumeration {
    val Employment, JobSeekersAllowance, Pension, OtherIncome = Value
  }
}
