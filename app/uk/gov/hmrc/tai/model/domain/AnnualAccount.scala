/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.tai.model.tai.TaxYear

case class AnnualAccount(
  key: String,
  taxYear: TaxYear,
  realTimeStatus: RealTimeStatus,
  payments: Seq[Payment],
  endOfTaxYearUpdates: Seq[EndOfTaxYearUpdate]) {

  lazy val totalIncomeYearToDate: BigDecimal =
    if (payments.isEmpty) 0 else payments.max.amountYearToDate

  lazy val employerDesignation: String = {
    val split = key.split("-")
    split(0) + "-" + split(1)
  }

  lazy val latestPayment: Option[Payment] = if (payments.isEmpty) None else Some(payments.max)
}

object AnnualAccount {
  implicit val annualAccountOrdering: Ordering[AnnualAccount] = Ordering.by(_.taxYear.year)

  def apply(key: String, taxYear: TaxYear, rtiStatus: RealTimeStatus): AnnualAccount =
    AnnualAccount(key, taxYear, rtiStatus, Nil, Nil)

}
