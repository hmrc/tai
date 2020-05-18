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

import org.joda.time.LocalDate
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.tai.model.tai.TaxYear

case class Employment(
  name: String,
  payrollNumber: Option[String],
  startDate: LocalDate,
  endDate: Option[LocalDate],
  annualAccounts: Seq[AnnualAccount],
  taxDistrictNumber: String,
  payeNumber: String,
  sequenceNumber: Int,
  cessationPay: Option[BigDecimal],
  hasPayrolledBenefit: Boolean,
  receivingOccupationalPension: Boolean) {

  lazy val key: String = employerDesignation + payrollNumber.map(pr => if (pr == "") "" else "-" + pr).getOrElse("")

  lazy val employerDesignation: String = taxDistrictNumber + "-" + payeNumber

  lazy val latestAnnualAccount: Option[AnnualAccount] = if (annualAccounts.isEmpty) None else Some(annualAccounts.max)

  def tempUnavailableStubExistsForYear(year: TaxYear): Boolean =
    annualAccounts.exists(annualAccount =>
      annualAccount.realTimeStatus == TemporarilyUnavailable && annualAccount.taxYear == year)

  def nonTempAccountForYear(year: TaxYear): Seq[AnnualAccount] =
    annualAccounts.filter(annualAccount =>
      annualAccount.taxYear == year && annualAccount.realTimeStatus != TemporarilyUnavailable)

  def hasAnnualAccountsForYear(year: TaxYear): Boolean = annualAccountsForYear(year).nonEmpty

  def annualAccountsForYear(year: TaxYear): Seq[AnnualAccount] = annualAccounts.filter(_.taxYear == year)

}

case class AddEmployment(
  employerName: String,
  startDate: LocalDate,
  payrollNumber: String,
  telephoneContactAllowed: String,
  telephoneNumber: Option[String])

object AddEmployment {
  implicit val formats = Json.format[AddEmployment]
}

case class EndEmployment(endDate: LocalDate, telephoneContactAllowed: String, telephoneNumber: Option[String])

object EndEmployment {
  implicit val formats = Json.format[EndEmployment]
}

case class IncorrectEmployment(whatYouToldUs: String, telephoneContactAllowed: String, telephoneNumber: Option[String])

object IncorrectEmployment {
  implicit val formats: Format[IncorrectEmployment] = Json.format[IncorrectEmployment]
}
