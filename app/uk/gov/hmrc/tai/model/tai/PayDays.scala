/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.tai

sealed trait PayDays {
  def days: Int

  def remainingQuarter: Int

  def remainingBiAnnual: Int
}

object THREE_MONTHS extends PayDays {
  val days = 90
  val remainingQuarter = 1
  val remainingBiAnnual = 1
}

object NINE_MONTHS extends PayDays {
  val days = 273
  val remainingQuarter = 3
  val remainingBiAnnual = 0
}

object SIX_MONTHS extends PayDays {
  val days = 182
  val remainingQuarter = 2
  val remainingBiAnnual = 1
}

object RegularYear extends Enumeration {
  val NoOfMonths: RegularYear.Value = Value(12)
  val NoOfDays: RegularYear.Value = Value(365)
  val NoOfWeeks: RegularYear.Value = Value(52)
}

object NoOfMonths extends Enumeration {
  val Annually: NoOfMonths.Value = Value(12)
  val Quarterly: NoOfMonths.Value = Value(4)
  val BiAnnually: NoOfMonths.Value = Value(2)
}
