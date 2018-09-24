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

package uk.gov.hmrc.tai.util.factory

import org.joda.time.LocalDate
import play.api.libs.json.{JsString, JsValue, Json}
import uk.gov.hmrc.tai.model.TaxCodeRecord
import uk.gov.hmrc.tai.util.{RandomInt, TaxCodeHistoryConstants}
import uk.gov.hmrc.time.TaxYearResolver

object TaxCodeRecordFactory extends TaxCodeHistoryConstants {

  def create(taxCode: String = "1185L",
                    taxCodeId: Int = RandomInt(),
                    basisOfOperation: String = Cumulative,
                    employerName: String = "Employer 1",
                    operatedTaxCode: Boolean = true,
                    dateOfCalculation: LocalDate = TaxYearResolver.startOfCurrentTaxYear.plusMonths(2),
                    payrollNumber: String = RandomInt().toString,
                    pensionIndicator: Boolean = false,
                    employmentType: String = Primary): TaxCodeRecord = {

    val payrollNum = if (payrollNumber == "") None else Some(payrollNumber)

    TaxCodeRecord(taxCode = taxCode,
      taxCodeId = taxCodeId,
      basisOfOperation = basisOfOperation,
      employerName = employerName,
      operatedTaxCode = operatedTaxCode,
      dateOfCalculation = dateOfCalculation,
      payrollNumber = payrollNum,
      pensionIndicator = pensionIndicator,
      employmentType = employmentType)
  }

  def createJson(taxCode: String = "1185L",
                         taxCodeId: Int = RandomInt(),
                         basisOfOperation: String = Cumulative,
                         employerName: String = "Employer 1",
                         operatedTaxCode: Boolean = true,
                         p2Issued: Boolean = true,
                         dateOfCalculation: String = "2017-06-23",
                         payrollNumber: Option[String] = None,
                         pensionIndicator: Boolean = false,
                         employmentType: String = "SECONDARY"): JsValue = {

    val withOutPayroll = Json.obj("taxCode" -> taxCode,
      "taxCodeId" -> taxCodeId,
      "basisOfOperation" -> basisOfOperation,
      "employerName" -> employerName,
      "operatedTaxCode" -> operatedTaxCode,
      "p2Issued" -> p2Issued,
      "dateOfCalculation" -> dateOfCalculation,
      "pensionIndicator" -> pensionIndicator,
      "employmentType" -> employmentType)


    if (payrollNumber.isDefined) {
      withOutPayroll + ("payrollNumber" -> JsString(payrollNumber.get))
    } else {
      withOutPayroll
    }
  }
}
