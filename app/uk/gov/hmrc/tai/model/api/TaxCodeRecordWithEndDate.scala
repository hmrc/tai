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
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering

case class TaxCodeRecordWithEndDate(taxCode: String,
                                    basisOfOperation: String,
                                    startDate: LocalDate,
                                    endDate: LocalDate,
                                    employerName: String,
                                    payrollNumber: Option[String],
                                    pensionIndicator: Boolean,
                                    primary: Boolean)

object TaxCodeRecordWithEndDate {
  implicit val format: OFormat[TaxCodeRecordWithEndDate] = Json.format[TaxCodeRecordWithEndDate]
}

case class TaxCodeChange(current: Seq[TaxCodeRecordWithEndDate], previous: Seq[TaxCodeRecordWithEndDate]) {
  def latestTaxCodeChangeDate: LocalDate = current.map(_.startDate).min

  def primaryCurrentTaxCode: Option[String] = primaryTaxCode(current)
  def secondaryCurrentTaxCodes: Seq[String] = secondaryTaxCode(current)
  def primaryPreviousTaxCode: Option[String] = primaryTaxCode(previous)
  def secondaryPreviousTaxCodes: Seq[String] = secondaryTaxCode(previous)

  def primaryCurrentPayrollNumber: Option[String] = primaryPayrollNumber(current)
  def secondaryCurrentPayrollNumbers: Seq[String] = secondaryPayrollNumbers(current)
  def primaryPreviousPayrollNumber: Option[String] = primaryPayrollNumber(previous)
  def secondaryPreviousPayrollNumbers: Seq[String] = secondaryPayrollNumbers(previous)

  private def primaryPayrollNumber(records: Seq[TaxCodeRecordWithEndDate]): Option[String] = {
    primaryRecord(records) match {
      case Some(record) => record.payrollNumber
      case None => None
    }
  }
  private def secondaryPayrollNumbers(records: Seq[TaxCodeRecordWithEndDate]) = secondaryRecords(records).flatMap(_.payrollNumber)
  private def primaryTaxCode(records: Seq[TaxCodeRecordWithEndDate]): Option[String] = {
    primaryRecord(records) match {
      case Some(record) => Some(record.taxCode)
      case None => None
    }
  }
  private def secondaryTaxCode(records: Seq[TaxCodeRecordWithEndDate]) = secondaryRecords(records).map(_.taxCode)

  private def primaryRecord(records: Seq[TaxCodeRecordWithEndDate]): Option[TaxCodeRecordWithEndDate] =
    records.find(_.primary)

  private def secondaryRecords(records: Seq[TaxCodeRecordWithEndDate]) = records.filterNot(_.primary)
}

object TaxCodeChange {
  implicit val format: OFormat[TaxCodeChange] = Json.format[TaxCodeChange]
}