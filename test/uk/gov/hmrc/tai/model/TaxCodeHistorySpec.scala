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
import play.api.libs.json.{JsResultException, Json}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

import scala.util.Random

class TaxCodeHistorySpec extends PlaySpec with TaxCodeHistoryConstants {

  "TaxCodeHistory reads" should {
    "return a TaxCodeHistory given valid Json" in {

      val now = LocalDate.now()
      val nino = randomNino
      val payrollNumber1 = randomInt().toString
      val payrollNumber2 = randomInt().toString


      val taxCodeHistory = TaxCodeHistory(nino, Seq(
        TaxCodeRecord("tax code", Cumulative, "Employee 1", operatedTaxCode = true, now, Some(payrollNumber1), pensionIndicator = false, "PRIMARY"),
        TaxCodeRecord("tax code", Cumulative, "Employee 1", operatedTaxCode = true, now, Some(payrollNumber2), pensionIndicator = false, "PRIMARY")
      ))

      val validJson = Json.obj(
        "nino" -> nino,
        "taxCodeRecord" -> Seq(
          Json.obj("taxCode" -> "tax code",
            "basisOfOperation" -> Cumulative,
            "employerName" -> "Employee 1",
            "operatedTaxCode" -> true,
            "dateOfCalculation" -> now,
            "payrollNumber" -> payrollNumber1,
            "pensionIndicator" -> false,
            "employmentType" -> "PRIMARY"),
          Json.obj("taxCode" -> "tax code",
            "basisOfOperation" -> Cumulative,
            "employerName" -> "Employee 1",
            "operatedTaxCode" -> true,
            "dateOfCalculation" -> now,
            "payrollNumber" -> payrollNumber2,
            "pensionIndicator" -> false,
            "employmentType" -> "PRIMARY")
        )
      )

      validJson.as[TaxCodeHistory] mustEqual taxCodeHistory

    }

    "throw an error when there are no tax code records" in {

      val nino = randomNino

      val invalidJson = Json.obj(
        "nino" -> nino,
        "taxCodeRecord" -> Seq.empty[TaxCodeRecord]
      )

      a[JsResultException] should be thrownBy invalidJson.as[TaxCodeHistory]

    }
  }

  private def randomNino: String = new Generator(new Random).nextNino.toString().slice(0, -1)

  private def randomInt(maxDigits: Int = 5): Int = {
    import scala.math.pow
    val random = new Random
    random.nextInt(pow(10,maxDigits).toInt)
  }
}
