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
import uk.gov.hmrc.tai.config.CacheConfig
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.CacheService.{UpstreamErrorResponseWrapper, formatNew}

import java.time.{Duration, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class CacheService @Inject() (sessionCacheRepository: TaiSessionCacheRepository, appConfig: CacheConfig)(implicit
  ec: ExecutionContext
) {
  private def isCachedErrorOutOfDate(dt: LocalDateTime): Boolean =
    Duration.between(dt, LocalDateTime.now).getSeconds > appConfig.cacheErrorInSecondsTTL

  def cacheEither[A](key: String)(
    block: => Future[Either[UpstreamErrorResponse, A]]
  )(implicit hc: HeaderCarrier, fmt: Format[A]): Future[Either[UpstreamErrorResponse, A]] = {
    implicit val eitherFormat: Format[Either[UpstreamErrorResponseWrapper, A]] = formatNew[A]
    def resultAndCache: Future[Either[UpstreamErrorResponse, A]] =
      block.flatMap { result =>
        val wrappedBlockResponse = result match {
          case Left(e)  => Left[UpstreamErrorResponseWrapper, A](UpstreamErrorResponseWrapper(LocalDateTime.now, e))
          case Right(r) => Right[UpstreamErrorResponseWrapper, A](r)
        }
        sessionCacheRepository
          .putSession(DataKey[Either[UpstreamErrorResponseWrapper, A]](key), wrappedBlockResponse)
          .map(_ => result)
      }

    sessionCacheRepository
      .getFromSession(DataKey[Either[UpstreamErrorResponseWrapper, A]](key))
      .flatMap {
        case None                                                                                      => resultAndCache
        case Some(Left(UpstreamErrorResponseWrapper(dt, retrievedItem))) if isCachedErrorOutOfDate(dt) => resultAndCache
        case Some(Left(UpstreamErrorResponseWrapper(_, retrievedItem))) => Future.successful(Left(retrievedItem))
        case Some(Right(r))                                             => Future.successful(Right(r))
      }
  }
}

object CacheService {
  private case class UpstreamErrorResponseWrapper(dateTime: LocalDateTime, upstreamErrorResponse: UpstreamErrorResponse)

  private def formatNew[A](implicit fmt: Format[A]): Format[Either[UpstreamErrorResponseWrapper, A]] = {
    val reads: Reads[Either[UpstreamErrorResponseWrapper, A]] = Reads { json =>
      JsSuccess(
        ((json \ "left").asOpt[JsObject], (json \ "right").asOpt[JsArray]) match {
          case (_, Some(right)) =>
            Right[UpstreamErrorResponseWrapper, A](right.as[A])
          case (Some(left), _) =>
            val statusCode = (left \ "statusCode").as[Int]
            val reportAs = (left \ "reportAs").as[Int]
            val message = (left \ "message").as[String]
            val dateTime = (left \ "dateTime").as[LocalDateTime]
            Left[UpstreamErrorResponseWrapper, A](
              UpstreamErrorResponseWrapper(dateTime, UpstreamErrorResponse(message, statusCode, reportAs))
            )
          case _ => throw new RuntimeException("Unrecognised cache value")
        }
      )

    }
    val writes: Writes[Either[UpstreamErrorResponseWrapper, A]] = Writes {
      case Left(UpstreamErrorResponseWrapper(dt, e)) =>
        Json.obj(
          "left" -> Json.obj(
            "statusCode" -> e.statusCode,
            "reportAs"   -> e.reportAs,
            "message"    -> e.message,
            "dateTime"   -> Json.toJson(dt)
          )
        )
      case Right(s) =>
        Json.obj(
          "right" -> Json.toJson(s)
        )
    }
    Format(reads, writes)
  }

}
