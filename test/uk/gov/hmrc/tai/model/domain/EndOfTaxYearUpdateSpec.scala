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

package uk.gov.hmrc.tai.model.domain

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.tai.model.domain.EndOfTaxYearUpdate.endOfTaxYearUpdateHodReads

import java.io.File
import java.time.LocalDate
import scala.io.BufferedSource

class EndOfTaxYearUpdateSpec extends PlaySpec {

  private val sampleEndOfTaxYearUpdateTwoAdjusts = EndOfTaxYearUpdate(
    LocalDate.of(2016, 6, 4),
    Seq(Adjustment(TaxAdjustment, -20.99), Adjustment(NationalInsuranceAdjustment, 44.2))
  )

  private val sampleEndOfTaxYearUpdate =
    EndOfTaxYearUpdate(LocalDate.of(2016, 6, 4), Seq(Adjustment(TaxAdjustment, -20.99)))
  private val sampleEndOfTaxYearUpdateMultipleAdjusts = EndOfTaxYearUpdate(
    LocalDate.of(2016, 6, 4),
    Seq(
      Adjustment(TaxAdjustment, -20.99),
      Adjustment(IncomeAdjustment, -21.99),
      Adjustment(NationalInsuranceAdjustment, 44.2)
    )
  )

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentHodFormattersTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString(""))
    jsVal
  }
  "EndOfTaxYearUpdate reads" must {

    "read rti json with a single adjustment, and convert it to TotalTaxDelta adjustment object" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentSingleAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdate
    }

    "read rti json with multip[le adjustments convert adjustment objects" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentMultipleAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdateMultipleAdjusts
    }

    "read rti json with multip[le adjustments and ignore those of zero value" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentZeroAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdateTwoAdjusts
    }
  }

}
