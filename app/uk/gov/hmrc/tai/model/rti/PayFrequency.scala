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

package uk.gov.hmrc.tai.model.rti

import play.api.libs.json.{JsError, JsString, JsSuccess, Reads, Writes}

object PayFrequency extends Enumeration {
  type PayFrequency = Value

  val Weekly: Value = Value("W1")
  val Fortnightly: Value = Value("W2")
  val FourWeekly: Value = Value("W4")
  val Monthly: Value = Value("M1")
  val Quarterly: Value = Value("M3")
  val BiAnnually: Value = Value("M6")
  val Annually: Value = Value("MA")
  val OneOff: Value = Value("IO")
  val Irregular: Value = Value("IR")

  implicit val reads: Reads[PayFrequency] = Reads { json =>
    json.validate[String].flatMap { str =>
      values
        .find(_.toString == str)
        .map(JsSuccess(_))
        .getOrElse(JsError(s"Invalid PayFrequency: $str"))
    }
  }

  implicit val writes: Writes[PayFrequency] = Writes(pf => JsString(pf.toString))
}
