/*
 * Copyright 2020 HM Revenue & Customs
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

sealed trait PaymentFrequency {
  val value: String
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
