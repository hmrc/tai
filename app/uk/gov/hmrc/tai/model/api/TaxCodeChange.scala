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

package uk.gov.hmrc.tai.model.api

import java.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering

case class TaxCodeChange(current: Seq[TaxCodeSummary], previous: Seq[TaxCodeSummary]) {
  def latestTaxCodeChangeDate: LocalDate = current.map(_.startDate).min

  def primaryCurrentTaxCode: Option[String] = primaryTaxCode(current)
  def secondaryCurrentTaxCodes: Seq[String] = secondaryTaxCode(current)
  def primaryPreviousTaxCode: Option[String] = primaryTaxCode(previous)
  def secondaryPreviousTaxCodes: Seq[String] = secondaryTaxCode(previous)

  def primaryCurrentPayrollNumber: Option[String] = primaryPayrollNumber(current)
  def secondaryCurrentPayrollNumbers: Seq[String] = secondaryPayrollNumbers(current)
  def primaryPreviousPayrollNumber: Option[String] = primaryPayrollNumber(previous)
  def secondaryPreviousPayrollNumbers: Seq[String] = secondaryPayrollNumbers(previous)

  def primaryPreviousRecord: Option[TaxCodeSummary] = primaryRecord(previous)

  private def primaryPayrollNumber(records: Seq[TaxCodeSummary]): Option[String] =
    primaryRecord(records) match {
      case Some(record) => record.payrollNumber
      case None         => None
    }
  private def secondaryPayrollNumbers(records: Seq[TaxCodeSummary]) = secondaryRecords(records).flatMap(_.payrollNumber)
  private def primaryTaxCode(records: Seq[TaxCodeSummary]): Option[String] =
    primaryRecord(records) match {
      case Some(record) => Some(record.taxCode)
      case None         => None
    }
  private def secondaryTaxCode(records: Seq[TaxCodeSummary]) = secondaryRecords(records).map(_.taxCode)

  private def primaryRecord(records: Seq[TaxCodeSummary]): Option[TaxCodeSummary] =
    records.find(_.primary)

  private def secondaryRecords(records: Seq[TaxCodeSummary]) = records.filterNot(_.primary)
}

object TaxCodeChange {
  implicit val format: OFormat[TaxCodeChange] = Json.format[TaxCodeChange]
}
