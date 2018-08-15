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

package uk.gov.hmrc.tai.model

import org.joda.time.LocalDate
import org.scalatest.prop.PropertyChecks
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.util.TaxCodeRecordConstants

class TaxCodeRecordSpec extends PlaySpec with TaxCodeRecordConstants with PropertyChecks{

  "TaxCodeRecord reads" should {

    "return a TaxCodeRecord for valid codes" in {
      forAll (validCodeCombos) { (npsCode: String, codeType:CodeType) =>
        validJson(npsCode).as[TaxCodeRecord] mustEqual TaxCodeRecord("testCode", "employerName", true, new LocalDate(2018, 2, 2), codeType)
      }

    }
  }

  def validJson(codeType: String) = Json.obj(
    "taxCode" -> "testCode",
    "employerName" -> "employerName",
    "operatedTaxCode" -> true,
    "dateOfCalculation" -> "2018-02-02",
    "codeType" -> codeType
  )

  def taxCodeRecord(codeType: CodeType) = TaxCodeRecord("testCode", "employerName", true, new LocalDate(2018, 2, 2), codeType)

  val validCodeCombos =
    Table(
      ("npsCode", "codeType"),
      (DailyCoding, NonAnnualCode),
      (BudgetCoding, NonAnnualCode),
      (BudgetCodingNonIssued, NonAnnualCode),
      (BudgetCodingP9X, NonAnnualCode),
      (AnnualCodeP9X, AnnualCode),
      (AnnualCoding, AnnualCode)
    )
}


