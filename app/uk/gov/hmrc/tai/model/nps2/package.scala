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

package uk.gov.hmrc.tai.model

import play.api.libs.json._
import uk.gov.hmrc.tai.util.DateTimeHelper.formatLocalDateDDMMYYYY

import java.time.LocalDate

package object nps2 {

  def enumerationFormat(a: Enumeration): Format[a.Value] = new Format[a.Value] {
    def reads(json: JsValue): JsResult[a.Value] = JsSuccess(a.withName(json.as[String]))

    def writes(v: a.Value): JsValue = JsString(v.toString)
  }

  def enumerationNumFormat(a: Enumeration): Format[a.Value] = new Format[a.Value] {
    def reads(json: JsValue): JsResult[a.Value] = JsSuccess(a(json.as[Int]))

    def writes(v: a.Value): JsValue = JsNumber(v.id)
  }

  implicit val formatLocalDate: Format[LocalDate] = formatLocalDateDDMMYYYY
}
