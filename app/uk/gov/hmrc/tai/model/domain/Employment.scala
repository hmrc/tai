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

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.{Format, JsResult, JsSuccess, JsValue, Json, OFormat, Reads}
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
  receivingOccupationalPension: Boolean
) {

  lazy val latestAnnualAccount: Option[AnnualAccount] = if (annualAccounts.isEmpty) None else Some(annualAccounts.max)

  def tempUnavailableStubExistsForYear(year: TaxYear): Boolean =
    annualAccounts.exists(annualAccount =>
      annualAccount.realTimeStatus == TemporarilyUnavailable && annualAccount.taxYear == year
    )

  def hasAnnualAccountsForYear(year: TaxYear): Boolean = annualAccountsForYear(year).nonEmpty

  def annualAccountsForYear(year: TaxYear): Seq[AnnualAccount] = annualAccounts.filter(_.taxYear == year)

}

object Employment {
  implicit val employmentFormat: Format[Employment] = Json.format[Employment]

  val employmentHodReads: Reads[Employment] = new Reads[Employment] {
    private val dateReadsFromHod: Reads[LocalDate] = localDateReads("dd/MM/yyyy")

    override def reads(json: JsValue): JsResult[Employment] = {
      val name = (json \ "employerName").as[String]
      val payrollNumber = (json \ "worksNumber").asOpt[String]
      val startDate = (json \ "startDate").as[LocalDate](dateReadsFromHod)
      val endDate = (json \ "endDate").asOpt[LocalDate](dateReadsFromHod)
      val taxDistrictNumber = numberChecked((json \ "taxDistrictNumber").as[String])
      val payeNumber = (json \ "payeNumber").as[String]
      val sequenceNumber = (json \ "sequenceNumber").as[Int]
      val cessationPay = (json \ "cessationPayThisEmployment").asOpt[BigDecimal]
      val payrolledBenefit = (json \ "payrolledTaxYear").asOpt[Boolean].getOrElse(false) || (json \ "payrolledTaxYear1")
        .asOpt[Boolean]
        .getOrElse(false)
      val receivingOccupationalPension = (json \ "receivingOccupationalPension").as[Boolean]
      val status = TaxCodeIncomeStatus.employmentStatusFromNps(json)
      JsSuccess(
        Employment(
          name,
          status,
          payrollNumber,
          startDate,
          endDate,
          Nil,
          taxDistrictNumber,
          payeNumber,
          sequenceNumber,
          cessationPay,
          payrolledBenefit,
          receivingOccupationalPension
        )
      )
    }
  }

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
