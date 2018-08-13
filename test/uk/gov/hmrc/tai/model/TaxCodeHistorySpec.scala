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
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.util.TaxCodeRecordConstants

import scala.util.Random

class TaxCodeHistorySpec extends PlaySpec with TaxCodeRecordConstants {

  "TaxCodeHistory reads" should {
    "return a TaxCodeHistory given valid Json" in {

      val now = LocalDate.now()
      val nino = randomNino
      val taxCodeHistory = TaxCodeHistory(nino, Seq(
        TaxCodeRecord("tax code", "Employee 1", true, now, NonAnnualCode),
        TaxCodeRecord("tax code", "Employee 1", true, now, NonAnnualCode)
      ))

      val validJson = Json.obj(
        "nino" -> nino,
        "taxCodeRecord" -> Seq(
          Json.obj("taxCode" -> "tax code","employerName" -> "Employee 1", "operatedTaxCode" -> true, "dateOfCalculation" -> now, "codeType" -> DailyCoding),
          Json.obj("taxCode" -> "tax code","employerName" -> "Employee 1", "operatedTaxCode" -> true, "dateOfCalculation" -> now, "codeType" -> DailyCoding)
        )
      )

      validJson.as[TaxCodeHistory] mustEqual taxCodeHistory

    }
  }

  private def randomNino: String = new Generator(new Random).nextNino.toString().slice(0, -1)

}
