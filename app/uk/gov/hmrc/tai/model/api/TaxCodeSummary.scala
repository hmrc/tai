/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.api

import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tai.model.TaxCodeRecord
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.time.TaxYearResolver

case class TaxCodeSummary(taxCode: String,
                          basisOfOperation: String,
                          startDate: LocalDate,
                          endDate: LocalDate,
                          employerName: String,
                          payrollNumber: Option[String],
                          pensionIndicator: Boolean,
                          primary: Boolean)

object TaxCodeSummary {

  implicit val format: OFormat[TaxCodeSummary] = Json.format[TaxCodeSummary]

  def apply(taxCodeRecord: TaxCodeRecord, date:LocalDate): TaxCodeSummary = {

    val taxYear = TaxYear(TaxYearResolver.taxYearFor(date))
    val startDate =
      if (taxCodeRecord.dateOfCalculation.isBefore(taxYear.start)) {
        taxYear.start
      }
      else {
        taxCodeRecord.dateOfCalculation
      }

    TaxCodeSummary(
      taxCodeRecord.taxCode, taxCodeRecord.basisOfOperation, startDate , date,
      taxCodeRecord.employerName, taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary
    )
  }

}
