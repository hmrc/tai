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

package uk.gov.hmrc.tai.service

import com.google.inject.Inject
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.CacheService.formatNew

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}

class CacheService @Inject() (sessionCacheRepository: TaiSessionCacheRepository)(implicit ec: ExecutionContext) {
  def cacheEither[A](key: String)(
    block: => Future[Either[UpstreamErrorResponse, A]]
  )(implicit hc: HeaderCarrier, fmt: Format[A]): Future[Either[UpstreamErrorResponse, A]] = {
    implicit val eitherFormat: Format[Either[UpstreamErrorResponse, A]] = formatNew[A]
    sessionCacheRepository
      .getFromSession(DataKey[Either[UpstreamErrorResponse, A]](key))
      .flatMap { // TODO: If is left and out of date then act as if none
        case None =>
          block.flatMap { result =>
            sessionCacheRepository.putSession(DataKey[Either[UpstreamErrorResponse, A]](key), result).map(_ => result)
          }
        case Some(retrievedItem) => Future.successful(retrievedItem)
      }
  }
}

object CacheService {
  private def formatNew[A](implicit fmt: Format[A]): Format[Either[UpstreamErrorResponse, A]] = {
    val reads: Reads[Either[UpstreamErrorResponse, A]] = Reads { json =>
      JsSuccess(
        ((json \ "left").asOpt[JsObject], (json \ "right").asOpt[JsArray]) match {
          case (_, Some(right)) =>
            val x = right.as[A]
            Right[UpstreamErrorResponse, A](x)
          case (Some(left), _) =>
            val statusCode = (left \ "statusCode").as[Int]
            val reportAs = (left \ "reportAs").as[Int]
            val message = (left \ "reportAs").as[String]
            // val dateTime = (left \ "dateTime").as[LocalDateTime]
            Left[UpstreamErrorResponse, A](UpstreamErrorResponse(message, statusCode, reportAs))
          case _ => throw new RuntimeException("bla")
        }
      )

    }
    val writes: Writes[Either[UpstreamErrorResponse, A]] = Writes {
      case Left(e) =>
        Json.obj(
          "left" -> Json.toJson(
            "statusCode" -> e.statusCode,
            "reportAs"   -> e.reportAs,
            "message"    -> e.message,
            "dateTime"   -> Json.toJson(LocalDateTime.now)
          )
        )
      case Right(s) =>
        Json.obj(
          "right" -> Json.toJson(s)
        )
    }
    Format.apply(reads, writes)
  }
}
