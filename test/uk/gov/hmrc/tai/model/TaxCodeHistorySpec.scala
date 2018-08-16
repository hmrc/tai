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
      val payrollNumber1 = randomInt().toString
      val payrollNumber2 = randomInt().toString
      val emplmentId1 = randomInt()
      val emplmentId2 = randomInt()

      val taxCodeHistory = TaxCodeHistory(nino, Seq(
        TaxCodeRecord("tax code", "Employee 1", true, now, NonAnnualCode, payrollNumber1, emplmentId1, "PRIMARY"),
        TaxCodeRecord("tax code", "Employee 1", true, now, NonAnnualCode, payrollNumber2, emplmentId2, "PRIMARY")
      ))

      val validJson = Json.obj(
        "nino" -> nino,
        "taxCodeRecord" -> Seq(
          Json.obj("taxCode" -> "tax code","employerName" -> "Employee 1", "operatedTaxCode" -> true, "dateOfCalculation" -> now, "codeType" -> DailyCoding, "payrollNumber" -> payrollNumber1, "employmentId" -> emplmentId1, "employmentType" -> "PRIMARY"),
          Json.obj("taxCode" -> "tax code","employerName" -> "Employee 1", "operatedTaxCode" -> true, "dateOfCalculation" -> now, "codeType" -> DailyCoding, "payrollNumber" -> payrollNumber2, "employmentId" -> emplmentId2, "employmentType" -> "PRIMARY")
        )
      )

      validJson.as[TaxCodeHistory] mustEqual taxCodeHistory

    }
  }

  private def randomNino: String = new Generator(new Random).nextNino.toString().slice(0, -1)

  private def randomInt(maxDigits: Int = 5): Int = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10,maxDigits).toInt)
  }
}
