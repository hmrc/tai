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

package uk.gov.hmrc.tai.model

import play.api.libs.json._

/**
  * grossAmount:1000  THIS IS MANDATORY - MUST BE A POSITIVE WHOLE NUMBER NO GREATER THAN 999999*
  *
  * @param grossAmount
  */
case class UpdateIabdEmployeeExpense(grossAmount: Int, source: Option[Int]) {
  require(grossAmount >= 0, "grossAmount cannot be less than 0")
  require(grossAmount <= 999999, "grossAmount cannot be greater than 999999")
}

object UpdateIabdEmployeeExpense {
  implicit val format: Format[UpdateIabdEmployeeExpense] = Json.format[UpdateIabdEmployeeExpense]
}
