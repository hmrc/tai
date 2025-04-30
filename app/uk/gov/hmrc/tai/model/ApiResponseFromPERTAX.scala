/*
 * Copyright 2025 HM Revenue & Customs
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

import uk.gov.hmrc.tai.model.ErrorView
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.libs.json.*
import play.api.mvc.Result
import play.api.mvc.Results.Status

case class ApiResponseFromPERTAX(
  code: String,
  message: String,
  errorView: Option[ErrorView] = None,
  redirect: Option[String] = None,
  reportAs: Int
) {
  def toResult: Result = Status(reportAs)(this)
}

object ApiResponseFromPERTAX {
  implicit def writable[T](implicit writes: Writes[T]): Writeable[T] = {
    implicit val contentType: ContentTypeOf[T] = ContentTypeOf[T](Some(ContentTypes.JSON))
    Writeable(Writeable.writeableOf_JsValue.transform.compose(writes.writes))
  }

  private def removeNulls(jsObject: JsObject): JsValue =
    JsObject(jsObject.fields.collect {
      case (s, j: JsObject)            =>
        (s, removeNulls(j))
      case other if other._2 != JsNull =>
        other
    })

  implicit val writes: Writes[ApiResponseFromPERTAX] = new Writes[ApiResponseFromPERTAX] {
    override def writes(o: ApiResponseFromPERTAX): JsValue = removeNulls(
      Json.obj(
        "code"      -> JsString(o.code),
        "message"   -> JsString(o.message),
        "errorView" -> o.errorView.map(errorView => Json.toJson(errorView)),
        "redirect"  -> o.redirect.map(JsString.apply)
      )
    )
  }
}
