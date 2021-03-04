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

import play.api.libs.json.{JsObject, JsResult, JsSuccess, JsValue, Reads, Writes}

case class Change[A, B](currentYear: A, currentYearPlusOne: B)
object Change {

  implicit def changeReads[A, B](implicit aReads: Reads[A], bReads: Reads[B]): Reads[Change[A, B]] =
    new Reads[Change[A, B]] {
      override def reads(json: JsValue): JsResult[Change[A, B]] =
        json match {
          case JsObject(js) =>
            val a = aReads.reads((json \ "currentYear").as[JsValue]).get
            val b = bReads.reads((json \ "currentYearPlusOne").as[JsValue]).get
            JsSuccess(Change(a, b))
          case e =>
            throw new IllegalArgumentException(
              s"Expected a JsObject, found $e"
            )
        }

    }

  implicit def changeWrite[A, B](implicit aWrites: Writes[A], bWrites: Writes[B]) = new Writes[Change[A, B]] {
    def writes(change: Change[A, B]) = {
      val currentYearJs = aWrites.writes(change.currentYear)
      val currentYearPlusOneJs = bWrites.writes(change.currentYearPlusOne)
      JsObject(Seq("currentYear" -> currentYearJs, "currentYearPlusOne" -> currentYearPlusOneJs))
    }
  }
}
