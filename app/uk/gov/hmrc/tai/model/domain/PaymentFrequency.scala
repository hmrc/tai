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

import play.api.libs.json.{Format, JsResult, JsString, JsSuccess, JsValue}

sealed trait PaymentFrequency {
  val value: String
}

object PaymentFrequency {
  val paymentFrequencyFormat: Format[PaymentFrequency] = new Format[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] = json.as[String] match {
      case "Weekly"      => JsSuccess(Weekly)
      case "FortNightly" => JsSuccess(FortNightly)
      case "FourWeekly"  => JsSuccess(FourWeekly)
      case "Monthly"     => JsSuccess(Monthly)
      case "Quarterly"   => JsSuccess(Quarterly)
      case "BiAnnually"  => JsSuccess(BiAnnually)
      case "Annually"    => JsSuccess(Annually)
      case "OneOff"      => JsSuccess(OneOff)
      case "Irregular"   => JsSuccess(Irregular)
      case _             => throw new IllegalArgumentException("Invalid payment frequency")
    }
    override def writes(paymentFrequency: PaymentFrequency): JsValue = JsString(paymentFrequency.toString)
  }

  val paymentFrequencyFormatFromHod: Format[PaymentFrequency] = new Format[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] = json.as[String] match {
      case Weekly.value      => JsSuccess(Weekly)
      case FortNightly.value => JsSuccess(FortNightly)
      case FourWeekly.value  => JsSuccess(FourWeekly)
      case Monthly.value     => JsSuccess(Monthly)
      case Quarterly.value   => JsSuccess(Quarterly)
      case BiAnnually.value  => JsSuccess(BiAnnually)
      case Annually.value    => JsSuccess(Annually)
      case OneOff.value      => JsSuccess(OneOff)
      case Irregular.value   => JsSuccess(Irregular)
      case _                 => throw new IllegalArgumentException("Invalid payment frequency")
    }

    override def writes(paymentFrequency: PaymentFrequency): JsValue = JsString(paymentFrequency.toString)

  }
}

case object Weekly extends PaymentFrequency {
  val value: String = "W1"
}

case object FortNightly extends PaymentFrequency {
  val value: String = "W2"
}

case object FourWeekly extends PaymentFrequency {
  val value: String = "W4"
}

case object Monthly extends PaymentFrequency {
  val value: String = "M1"
}

case object Quarterly extends PaymentFrequency {
  val value: String = "M3"
}

case object BiAnnually extends PaymentFrequency {
  val value: String = "M6"
}

case object Annually extends PaymentFrequency {
  val value: String = "MA"
}

case object OneOff extends PaymentFrequency {
  val value: String = "IO"
}

case object Irregular extends PaymentFrequency {
  val value: String = "IR"
}
