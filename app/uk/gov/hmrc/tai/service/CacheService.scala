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

import cats.effect.IO
import com.google.inject.Inject
import play.api.Logging
import play.api.libs.json.*
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.tai.config.CacheConfig
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.CacheService.{UpstreamErrorResponseWrapper, formatEitherWithWrapper}

import java.time.{Duration, LocalDateTime}
import scala.concurrent.{ExecutionContext, Future}

class CacheService @Inject() (sessionCacheRepository: TaiSessionCacheRepository, appConfig: CacheConfig)(implicit
  ec: ExecutionContext
) extends Logging {
  private def isCachedErrorOutOfDate(dt: LocalDateTime): Boolean =
    Duration.between(dt, LocalDateTime.now).getSeconds > appConfig.cacheErrorInSecondsTTL

  def cacheEither[A](key: String)(
    block: => Future[Either[UpstreamErrorResponse, A]]
  )(implicit hc: HeaderCarrier, fmt: Format[A]): IO[Either[UpstreamErrorResponse, A]] = {
    implicit val eitherFormat: Format[Either[UpstreamErrorResponseWrapper, A]] = formatEitherWithWrapper[A]
    def resultAndCache: IO[Either[UpstreamErrorResponse, A]] =
      IO.fromFuture(IO(block)).flatMap { result =>
        val wrappedBlockResponse = result match {
          case Left(e)  => Left[UpstreamErrorResponseWrapper, A](UpstreamErrorResponseWrapper(LocalDateTime.now, e))
          case Right(r) => Right[UpstreamErrorResponseWrapper, A](r)
        }
        IO.fromFuture(
          IO(
            sessionCacheRepository
              .putSession(DataKey[Either[UpstreamErrorResponseWrapper, A]](key), wrappedBlockResponse)
              .map(_ => result)
          )
        )
      }

    IO.fromFuture(IO(sessionCacheRepository.getFromSession(DataKey[Either[UpstreamErrorResponseWrapper, A]](key))))
      .flatMap {
        case None                                                                                      => resultAndCache
        case Some(Left(UpstreamErrorResponseWrapper(dt, retrievedItem))) if isCachedErrorOutOfDate(dt) => resultAndCache
        case Some(Left(UpstreamErrorResponseWrapper(_, retrievedItem))) => IO(Left(retrievedItem))
        case Some(Right(r))                                             => IO(Right(r))
      }
      .recoverWith { case e =>
        logger.warn("An error occurred when retrieving from cache. Will re-execute block to get data.", e)
        resultAndCache
      }
  }
}

object CacheService {
  private case class UpstreamErrorResponseWrapper(dateTime: LocalDateTime, upstreamErrorResponse: UpstreamErrorResponse)

  private def formatEitherWithWrapper[A](implicit fmt: Format[A]): Format[Either[UpstreamErrorResponseWrapper, A]] = {
    val reads: Reads[Either[UpstreamErrorResponseWrapper, A]] = Reads { json =>
      ((json \ "left").asOpt[JsObject], (json \ "right").asOpt[JsArray]) match {
        case (_, Some(right)) =>
          JsSuccess(Right[UpstreamErrorResponseWrapper, A](right.as[A]))
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
        case _ => JsError("Neither left nor right found in cache")
      }
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
