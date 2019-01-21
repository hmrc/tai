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

package uk.gov.hmrc.tai.model.templates

import org.joda.time.LocalDate
import uk.gov.hmrc.tai.model.domain.{BankAccount, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear

case class CloseBankAccount(
                           personDetails: Person,
                           taxYear: TaxYear,
                           bankAccount: BankAccount,
                           endDate: LocalDate,
                           interestEarnedThisTaxYear: Option[BigDecimal]) {

  private val dateFormat = "d MMMM yyyy"

  val displayableTaxYearRange: String =
    s"${taxYear.start.toString(dateFormat)} to ${taxYear.end.toString(dateFormat)}"

  val bankAccountClosedInCurrentTaxYear: Boolean = TaxYear().withinTaxYear(endDate)
}