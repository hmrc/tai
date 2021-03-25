/*
 * Copyright 2021 HM Revenue & Customs
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

object PayFrequency extends Enumeration {
  val Weekly: PayFrequency.Value = Value("W1")
  val Fortnightly: PayFrequency.Value = Value("W2")

  /**
    * Not the same as [[Monthly]] (Calendar Month)
    */
  val FourWeekly: PayFrequency.Value = Value("W4")

  /**
    * A Calendar month, not the same as [[FourWeekly]]
    */
  val Monthly: PayFrequency.Value = Value("M1")
  val Quarterly: PayFrequency.Value = Value("M3")
  val BiAnnually: PayFrequency.Value = Value("M6")
  val Annually: PayFrequency.Value = Value("MA")
  val OneOff: PayFrequency.Value = Value("IO")
  val Irregular: PayFrequency.Value = Value("IR")
}
