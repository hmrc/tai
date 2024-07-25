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
import play.api.libs.json.{JsString, Json}
import uk.gov.hmrc.tai.model.domain.formatters.EmploymentHodFormatters

class PaymentFrequencySpec extends PlaySpec {

  private val paymentFrequencyFormat = EmploymentHodFormatters.paymentFrequencyFormat

  "Payment Frequency" must {
    "create a valid object" when {
      "user received a valid payment frequency" in {
        JsString("W1").as[PaymentFrequency](paymentFrequencyFormat) mustBe Weekly
        JsString("W2").as[PaymentFrequency](paymentFrequencyFormat) mustBe FortNightly
        JsString("W4").as[PaymentFrequency](paymentFrequencyFormat) mustBe FourWeekly
        JsString("M1").as[PaymentFrequency](paymentFrequencyFormat) mustBe Monthly
        JsString("M3").as[PaymentFrequency](paymentFrequencyFormat) mustBe Quarterly
        JsString("M6").as[PaymentFrequency](paymentFrequencyFormat) mustBe BiAnnually
        JsString("MA").as[PaymentFrequency](paymentFrequencyFormat) mustBe Annually
        JsString("IO").as[PaymentFrequency](paymentFrequencyFormat) mustBe OneOff
        JsString("IR").as[PaymentFrequency](paymentFrequencyFormat) mustBe Irregular
      }
    }

    "throw an illegal exception" in {
      val ex = the[IllegalArgumentException] thrownBy JsString("NA").as[PaymentFrequency](paymentFrequencyFormat)
      ex.getMessage mustBe "Invalid payment frequency"
    }

    "create a valid json" when {
      "user received a valid payment frequency" in {
        Json.toJson(Weekly)(paymentFrequencyFormat.writes(_)) mustBe JsString("Weekly")
        Json.toJson(FortNightly)(paymentFrequencyFormat.writes(_)) mustBe JsString("FortNightly")
        Json.toJson(FourWeekly)(paymentFrequencyFormat.writes(_)) mustBe JsString("FourWeekly")
        Json.toJson(Monthly)(paymentFrequencyFormat.writes(_)) mustBe JsString("Monthly")
        Json.toJson(Quarterly)(paymentFrequencyFormat.writes(_)) mustBe JsString("Quarterly")
        Json.toJson(BiAnnually)(paymentFrequencyFormat.writes(_)) mustBe JsString("BiAnnually")
        Json.toJson(Annually)(paymentFrequencyFormat.writes(_)) mustBe JsString("Annually")
        Json.toJson(OneOff)(paymentFrequencyFormat.writes(_)) mustBe JsString("OneOff")
        Json.toJson(Irregular)(paymentFrequencyFormat.writes(_)) mustBe JsString("Irregular")
      }
    }
  }

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
          Json.toJson(kv._2)(paymentFrequencyFormat.writes(_)) mustBe JsString(kv._1)
        }
      }
    }
  }
}
