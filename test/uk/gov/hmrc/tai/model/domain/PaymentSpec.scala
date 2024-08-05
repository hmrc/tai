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
import play.api.libs.json.{JsResultException, JsValue, Json}
import uk.gov.hmrc.tai.model.domain.Payment.paymentHodReads

import java.io.File
import java.time.LocalDate
import scala.io.BufferedSource

class PaymentSpec extends PlaySpec {
  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentHodFormattersTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString(""))
    jsVal
  }

  private val samplePayment = Payment(
    date = LocalDate.of(2017, 5, 26),
    amountYearToDate = 2000,
    taxAmountYearToDate = 1200,
    nationalInsuranceAmountYearToDate = 300,
    amount = 200,
    taxAmount = 100,
    nationalInsuranceAmount = 150,
    payFrequency = Irregular,
    duplicate = None
  )

  "paymentHodReads" must {

    "read nps json and convert it to payment object" in {
      val parsedJson: Payment = getJson("rtiInYearFragment").as[Payment](paymentHodReads)
      parsedJson mustBe samplePayment
    }

    "throw an error" when {
      "a field key is wrong" in {

        val incorrectJson = Json.obj(
          "aaa"                               -> "2017-05-26",
          "amountYearToDate"                  -> 2000,
          "taxAmountYearToDate"               -> 1200,
          "nationalInsuranceAmountYearToDate" -> 1500,
          "amount"                            -> 200,
          "taxAmount"                         -> 100,
          "nationalInsuranceAmount"           -> 150
        )

        an[JsResultException] mustBe thrownBy(incorrectJson.as[Payment](paymentHodReads))
      }

      "a key is wrong" in {
        val incorrectJson =
          Json.obj(
            "date"                              -> "aaa",
            "amountYearToDate"                  -> 2000,
            "taxAmountYearToDate"               -> 1200,
            "nationalInsuranceAmountYearToDate" -> 1500,
            "amount"                            -> 200,
            "taxAmount"                         -> 100,
            "nationalInsuranceAmount"           -> 150
          )

        an[JsResultException] mustBe thrownBy(incorrectJson.as[Payment](paymentHodReads))
      }
    }
  }

}
