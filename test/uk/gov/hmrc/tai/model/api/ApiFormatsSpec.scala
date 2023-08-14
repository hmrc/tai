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

package uk.gov.hmrc.tai.model.api

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsNumber, JsString, Json}
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear

class ApiFormatsSpec extends PlaySpec with ApiFormats {

  "paymentFrequencyFormat" when {

    val jsonValues = List(
      ("Weekly", Weekly),
      ("FortNightly", FortNightly),
      ("FourWeekly", FourWeekly),
      ("Monthly", Monthly),
      ("Quarterly", Quarterly),
      ("BiAnnually", BiAnnually),
      ("Annually", Annually),
      ("OneOff", OneOff),
      ("Irregular", Irregular)
    )

    "reading values" must {
      "read valid string values" in {
        jsonValues.foreach { kv =>
          JsString(kv._1).as[PaymentFrequency] mustBe kv._2
        }
      }

      "throw an IllegalArgumentException" when {
        "an invalid string is read" in {
          the[IllegalArgumentException] thrownBy {
            JsString("Foo").as[PaymentFrequency]
          } must have message "Invalid payment frequency"
        }
      }
    }

    "writing values" must {
      "write PaymentFrequencies to a JsString" in {
        jsonValues.foreach { kv =>
          Json.toJson(kv._2)(paymentFrequencyFormat.writes) mustBe JsString(kv._1)
        }
      }
    }
  }
  "formatTaxYear reads" must {
    "read a valid json taxyear" in {
      JsNumber(2018).as[TaxYear](formatTaxYear) mustBe TaxYear(2018)
    }
    "throw an exception for an invalid json taxyear" in {
      val ex = the[IllegalArgumentException] thrownBy JsString("Not Valid").as[TaxYear](formatTaxYear)
      ex.getMessage mustBe "Invalid tax year"
    }
  }
  "formatTaxYear writes" must {
    "write valid json form a TaxYear instance" in {
      Json.toJson(TaxYear(2015))(formatTaxYear) mustBe JsNumber(2015)
    }
  }
}
