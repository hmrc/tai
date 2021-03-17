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

package uk.gov.hmrc.tai.factory

import play.api.libs.json.{JsObject, JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

object TaxCodeHistoryFactory extends TaxCodeHistoryConstants {

  def createTaxCodeHistory(nino: Nino): TaxCodeHistory = {
    val taxCodeRecord1 = TaxCodeRecordFactory.createPrimaryEmployment()
    val taxCodeRecord2 = TaxCodeRecordFactory.createSecondaryEmployment(
      taxCode = "1100L",
      employerName = "Employer 2",
      payrollNumber = Some("456")
    )

    TaxCodeHistory(nino.toString, Seq(taxCodeRecord1, taxCodeRecord2))
  }

  def createTaxCodeHistoryJson(nino: Nino): JsObject =
    createTaxCodeHistoryJson(
      nino,
      Seq(
        TaxCodeRecordFactory.createPrimaryEmploymentJson(),
        TaxCodeRecordFactory.createSecondaryEmploymentJson(
          taxCode = "1100L",
          employerName = "Employer 2",
          payrollNumber = "456"
        )
      )
    )

  def createTaxCodeHistoryJson(nino: Nino, records: Seq[JsValue]): JsObject =
    Json.obj(
      "nino"          -> nino.toString,
      "taxCodeRecord" -> records
    )
}
