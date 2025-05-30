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

import play.api.libs.json.*
import uk.gov.hmrc.http.UpstreamErrorResponse

object UpstreamErrorResponseFormat {

  val leftNode: String = "failure"
  val rightNode: String = "success"

  private def parseLeft[A](left: JsObject): JsResult[Either[UpstreamErrorResponse, A]] = {
    val statusCode = (left \ "statusCode").as[Int]
    val reportAs = (left \ "reportAs").as[Int]
    val message = (left \ "message").as[String]
    JsSuccess(
      Left[UpstreamErrorResponse, A](
        UpstreamErrorResponse(message, statusCode, reportAs)
      )
    )
  }

  def format[A](implicit fmt: Format[A]): Format[Either[UpstreamErrorResponse, A]] = {
    val reads: Reads[Either[UpstreamErrorResponse, A]] = Reads { json =>
      def asLeftNode: Option[JsObject] = (json \ leftNode).asOpt[JsObject]
      def asRightNode: Option[A] = (json \ rightNode).asOpt[A]
      asRightNode
        .map(rn => JsSuccess(Right[UpstreamErrorResponse, A](rn)))
        .orElse(asLeftNode.map(ln => parseLeft(ln)))
        .orElse(json.asOpt[A].map(rn => JsSuccess(Right(rn))))
        .getOrElse(JsError(s"Neither $leftNode nor $rightNode found in cache"))
    }
    val writes: Writes[Either[UpstreamErrorResponse, A]] = Writes {
      case Left(UpstreamErrorResponse(message, statusCode, reportAs, _)) =>
        Json.obj(
          leftNode -> Json.obj(
            "statusCode" -> statusCode,
            "reportAs"   -> reportAs,
            "message"    -> message
          )
        )
      case Right(s) => Json.obj(rightNode -> Json.toJson(s))
    }
    Format(reads, writes)
  }
}
