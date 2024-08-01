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

package uk.gov.hmrc.tai.util

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.{Format, JsString, JsValue, Writes}

import java.time.format.DateTimeFormatter
import java.time.LocalDate

object DateTimeHelper {

  def convertToLocalDate(pattern: String, date: String): LocalDate = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    LocalDate.parse(date, dateTimeFormatter)
  }

  implicit val dateTimeOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

  val formatLocalDateDDMMYYYY: Format[LocalDate] = Format(
    localDateReads("dd/MM/yyyy"),
    new Writes[LocalDate] {
      private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

      override def writes(date: LocalDate): JsValue =
        JsString(date.format(dateFormat))
    }
  )
}
