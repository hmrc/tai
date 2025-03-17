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

package uk.gov.hmrc.tai.model.domain.income

import play.api.libs.json.{Json, OFormat, Writes}
import uk.gov.hmrc.tai.model.domain.Employment
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeSquidReads.*

import java.time.LocalDate

case class IncomeSource(taxCodeIncome: TaxCodeIncome, employment: Employment)

object IncomeSource {
  /*
    The Hip toggle only affects the Reads. We only require the Writes
    here therefore we can safely import TaxCodeIncomeSquidReads.
   */
  implicit val incomeSourceFormat: Writes[IncomeSource] =
    Json.format[IncomeSource]

}

case class TaxCodeIncomeSummary(
  name: String,
  taxCode: String,
  amount: BigDecimal,
  employmentId: Option[Int] = None,
  iabdUpdateSource: Option[IabdUpdateSource] = None,
  updateNotificationDate: Option[LocalDate] = None,
  updateActionDate: Option[LocalDate] = None
)

object TaxCodeIncomeSummary {
  implicit val format: OFormat[TaxCodeIncomeSummary] = Json.format[TaxCodeIncomeSummary]
}

case class IabdIncome(
  employmentId: Option[Int],
  iabdUpdateSource: Option[IabdUpdateSource],
  updateNotificationDate: Option[LocalDate],
  updateActionDate: Option[LocalDate],
  grossAmount: BigDecimal
)

case class IncomeSourceFromSummary(taxCodeIncomeSummary: TaxCodeIncomeSummary, employment: Employment)

object IncomeSourceFromSummary {
  implicit val format: OFormat[IncomeSourceFromSummary] = Json.format[IncomeSourceFromSummary]
}
