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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json.{Format, JsObject, Json, OFormat, Writes}
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.LocalDate
import scala.util.matching.Regex

case class Employment(
  name: String,
  employmentStatus: TaxCodeIncomeStatus,
  payrollNumber: Option[String],
  startDate: LocalDate,
  endDate: Option[LocalDate],
  annualAccounts: Seq[AnnualAccount],
  taxDistrictNumber: String,
  payeNumber: String,
  sequenceNumber: Int,
  cessationPay: Option[BigDecimal],
  hasPayrolledBenefit: Boolean,
  @deprecated("Use employmentType instead")
  receivingOccupationalPension: Boolean,
  employmentType: TaxCodeIncomeComponentType,
  isRtiServerFailure: Boolean = false
) {

  def tempUnavailableStubExistsForYear(year: TaxYear): Boolean =
    annualAccounts.exists(annualAccount =>
      annualAccount.realTimeStatus == TemporarilyUnavailable && annualAccount.taxYear == year
    )

  def hasAnnualAccountsForYear(year: TaxYear): Boolean = annualAccountsForYear(year).nonEmpty

  def annualAccountsForYear(year: TaxYear): Seq[AnnualAccount] = annualAccounts.filter(_.taxYear == year)

}

object Employment {
  // TODO: Once the new TES employment APIs (see DDCNL-9806) are fully live and the old APIs no longer
  //  used we can get rid of the field isRtiServerFailure and the two writes below can revert to one.
  implicit val employmentWrites: Writes[Employment] = { e =>
    employmentWritesWithRTIStatus.writes(e).as[JsObject] - "isRtiServerFailure"
  }
  val employmentWritesWithRTIStatus: Writes[Employment] = Json.writes[Employment]

  private val numericWithLeadingZeros: Regex = """^([0]+)([1-9][0-9]*)""".r
  def numberChecked(stringVal: String): String =
    stringVal match {
      case numericWithLeadingZeros(_, numeric) => numeric
      case _                                   => stringVal
    }
}

case class AddEmployment(
  employerName: String,
  startDate: LocalDate,
  payrollNumber: String,
  telephoneContactAllowed: String,
  telephoneNumber: Option[String]
)

object AddEmployment {
  implicit val formats: OFormat[AddEmployment] = Json.format[AddEmployment]
}

case class EndEmployment(endDate: LocalDate, telephoneContactAllowed: String, telephoneNumber: Option[String])

object EndEmployment {
  implicit val formats: OFormat[EndEmployment] = Json.format[EndEmployment]
}

case class IncorrectEmployment(whatYouToldUs: String, telephoneContactAllowed: String, telephoneNumber: Option[String])

object IncorrectEmployment {
  implicit val formats: Format[IncorrectEmployment] = Json.format[IncorrectEmployment]
}
