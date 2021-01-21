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

package uk.gov.hmrc.tai.model.nps

import uk.gov.hmrc.tai.model.helpers.IncomeHelper
import uk.gov.hmrc.tai.model.{Tax, TaxCodeIncomeSummary}
import play.api.libs.json._

case class NpsEmployment(
  sequenceNumber: Int,
  startDate: NpsDate,
  endDate: Option[NpsDate],
  taxDistrictNumber: String,
  payeNumber: String,
  employerName: Option[String],
  employmentType: Int,
  employmentStatus: Option[Int] = None,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startingTaxCode: Option[String] = None,
  receivingJobseekersAllowance: Option[Boolean] = None,
  receivingOccupationalPension: Option[Boolean] = None,
  otherIncomeSourceIndicator: Option[Boolean] = None,
  payrolledTaxYear: Option[Boolean] = None,
  payrolledTaxYear1: Option[Boolean] = None,
  cessationPayThisEmployment: Option[BigDecimal] = None) {

  def toNpsIncomeSource(estimatedPay: BigDecimal): NpsIncomeSource = {
    val payAndTax = NpsTax(totalIncome = Some(new NpsComponent(amount = Some(estimatedPay))))

    NpsIncomeSource(
      name = employerName,
      taxCode = None,
      employmentId = Some(sequenceNumber),
      employmentStatus = employmentStatus,
      employmentType = Some(employmentType),
      payAndTax = Some(payAndTax),
      pensionIndicator = receivingOccupationalPension,
      otherIncomeSourceIndicator = otherIncomeSourceIndicator,
      jsaIndicator = receivingJobseekersAllowance
    )

  }

  def toTaxCodeIncomeSummary(estimatedPay: BigDecimal): TaxCodeIncomeSummary = {
    val payAndTax = Tax(totalIncome = Some(estimatedPay))
    TaxCodeIncomeSummary(
      name = employerName.getOrElse(""),
      taxCode = startingTaxCode.getOrElse(""),
      employmentId = Some(sequenceNumber),
      tax = payAndTax,
      isLive = IncomeHelper.isLive(employmentStatus)
    )
  }
}

object NpsEmployment {
  implicit val formats = Json.format[NpsEmployment]
}
