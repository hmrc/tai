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

package uk.gov.hmrc.tai.factory

import java.time.LocalDate
import play.api.libs.json.{JsNull, JsObject, Json}
import uk.gov.hmrc.tai.model.TaxCodeRecord
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

object TaxCodeRecordFactory extends TaxCodeHistoryConstants {

  def createPrimaryEmployment(
    taxYear: TaxYear = TaxYear(),
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    payrollNumber: Option[String] = Some("123")): TaxCodeRecord =
    createEmployment(
      taxYear,
      taxCodeId,
      taxCode,
      basisOfOperation,
      employerName,
      operatedTaxCode = true,
      dateOfCalculation,
      payrollNumber,
      Primary)

  def createPrimaryEmploymentJson(
    taxYear: TaxYear = TaxYear(),
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    payrollNumber: String = "123"): JsObject =
    Json.obj(
      "taxYear"           -> taxYear.year,
      "taxCodeId"         -> taxCodeId,
      "taxCode"           -> taxCode,
      "basisOfOperation"  -> basisOfOperation,
      "employerName"      -> employerName,
      "operatedTaxCode"   -> true,
      "dateOfCalculation" -> dateOfCalculation.toString,
      "payrollNumber"     -> payrollNumber,
      "pensionIndicator"  -> false,
      "employmentType"    -> Primary
    )

  def createSecondaryEmployment(
    taxYear: TaxYear = TaxYear(),
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    payrollNumber: Option[String] = Some("123")): TaxCodeRecord =
    createEmployment(
      taxYear,
      taxCodeId,
      taxCode,
      basisOfOperation,
      employerName,
      true,
      dateOfCalculation,
      payrollNumber,
      Secondary)

  def createSecondaryEmploymentJson(
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    payrollNumber: String = "123"): JsObject =
    Json.obj(
      "taxYear"           -> TaxYear().year,
      "taxCodeId"         -> taxCodeId,
      "taxCode"           -> taxCode,
      "basisOfOperation"  -> basisOfOperation,
      "employerName"      -> employerName,
      "operatedTaxCode"   -> true,
      "dateOfCalculation" -> dateOfCalculation.toString,
      "payrollNumber"     -> payrollNumber,
      "pensionIndicator"  -> false,
      "employmentType"    -> Secondary
    )

  def createNoPayrollNumberJson(
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    employmentType: String = Primary): JsObject =
    Json.obj(
      "taxYear"           -> TaxYear().year,
      "taxCodeId"         -> taxCodeId,
      "taxCode"           -> taxCode,
      "basisOfOperation"  -> basisOfOperation,
      "employerName"      -> employerName,
      "operatedTaxCode"   -> true,
      "dateOfCalculation" -> dateOfCalculation.toString,
      "payrollNumber"     -> JsNull,
      "pensionIndicator"  -> false,
      "employmentType"    -> employmentType
    )

  def createNonOperatedEmployment(
    taxCodeId: Int = 1,
    taxCode: String = "1185L",
    basisOfOperation: String = Cumulative,
    employerName: String = "Employer 1",
    dateOfCalculation: LocalDate = LocalDate.now,
    payrollNumber: Option[String] = Some("123")): TaxCodeRecord =
    TaxCodeRecord(
      TaxYear(),
      taxCodeId,
      taxCode,
      basisOfOperation,
      employerName,
      operatedTaxCode = false,
      dateOfCalculation,
      payrollNumber,
      pensionIndicator = false,
      Primary)

  private def createEmployment(
    taxYear: TaxYear,
    taxCodeId: Int,
    taxCode: String,
    basisOfOperation: String,
    employerName: String,
    operatedTaxCode: Boolean,
    dateOfCalculation: LocalDate,
    payrollNumber: Option[String],
    employmentType: String): TaxCodeRecord =
    TaxCodeRecord(
      taxYear,
      taxCodeId,
      taxCode,
      basisOfOperation,
      employerName,
      operatedTaxCode,
      dateOfCalculation,
      payrollNumber,
      pensionIndicator = false,
      employmentType)
}
