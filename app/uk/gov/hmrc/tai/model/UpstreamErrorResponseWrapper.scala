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

import java.time.LocalDateTime

case class UpstreamErrorResponseWrapper(dateTime: LocalDateTime, upstreamErrorResponse: UpstreamErrorResponse)

object UpstreamErrorResponseWrapper {

  private val leftNode: String = "failure"
  private val rightNode: String = "success"

  def formatEitherWithWrapper[A](implicit fmt: Format[A]): Format[Either[UpstreamErrorResponseWrapper, A]] = {
    val reads: Reads[Either[UpstreamErrorResponseWrapper, A]] = Reads { json =>
      ((json \ leftNode).asOpt[JsObject], (json \ rightNode).asOpt[JsArray]) match {
        case (_, Some(right)) => JsSuccess(Right[UpstreamErrorResponseWrapper, A](right.as[A]))
        case (Some(left), _) =>
          val statusCode = (left \ "statusCode").as[Int]
          val reportAs = (left \ "reportAs").as[Int]
          val message = (left \ "message").as[String]
          val dateTime = (left \ "dateTime").as[LocalDateTime]
          JsSuccess(
            Left[UpstreamErrorResponseWrapper, A](
              UpstreamErrorResponseWrapper(dateTime, UpstreamErrorResponse(message, statusCode, reportAs))
            )
          )
        case _ => JsError(s"Neither $leftNode nor $rightNode found in cache")
      }
    }
    val writes: Writes[Either[UpstreamErrorResponseWrapper, A]] = Writes {
      case Left(UpstreamErrorResponseWrapper(dt, e)) =>
        Json.obj(
          leftNode -> Json.obj(
            "statusCode" -> e.statusCode,
            "reportAs"   -> e.reportAs,
            "message"    -> e.message,
            "dateTime"   -> Json.toJson(dt)
          )
        )
      case Right(s) => Json.obj(rightNode -> Json.toJson(s))
    }
    Format(reads, writes)
  }
}
