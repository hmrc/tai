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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Reads, Writes}

sealed trait PaymentFrequency {
  val value: String
}

object PaymentFrequency {

  implicit val reads: Reads[PaymentFrequency] = new Reads[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] =
      json match {
        case name if name == JsString(Weekly.value) => JsSuccess(Weekly)
        case name if name == JsString(FortNightly.value) => JsSuccess(FortNightly)
        case name if name == JsString(Monthly.value) => JsSuccess(Monthly)
        case name if name == JsString(Quarterly.value) => JsSuccess(Quarterly)
        case name if name == JsString(BiAnnually.value) => JsSuccess(BiAnnually)
        case name if name == JsString(Annually.value) => JsSuccess(Annually)
        case name if name == JsString(OneOff.value) => JsSuccess(OneOff)
        case name if name == JsString(Irregular.value) => JsSuccess(Irregular)
        case _                                         => JsError("Unknown PaymentFrequency")
      }
  }

  implicit val formats: Format[PaymentFrequency] =
    Format(reads, writes)

  implicit val writes: Writes[PaymentFrequency] = new Writes[PaymentFrequency] {
    override def writes(o: PaymentFrequency): JsValue = JsString(o.value)
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
