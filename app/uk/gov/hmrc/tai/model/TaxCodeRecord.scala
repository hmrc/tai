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

package uk.gov.hmrc.tai.model

import java.time.LocalDate
import play.api.libs.json._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

case class TaxCodeRecord(
  taxYear: TaxYear,
  taxCodeId: Int,
  taxCode: String,
  basisOfOperation: String,
  employerName: String,
  operatedTaxCode: Boolean,
  dateOfCalculation: LocalDate,
  payrollNumber: Option[String],
  pensionIndicator: Boolean,
  private val employmentType: String)
    extends TaxCodeHistoryConstants {

  val isPrimary: Boolean = {
    employmentType == Primary
  }
}

object TaxCodeRecord {
  implicit val format: OFormat[TaxCodeRecord] = Json.format[TaxCodeRecord]

  def mostRecent(taxCodeRecords: Seq[TaxCodeRecord]): TaxCodeRecord =
    taxCodeRecords.reduceLeft((record1, record2) =>
      if (record1.dateOfCalculation.isAfter(record2.dateOfCalculation)) record1 else record2)

}
