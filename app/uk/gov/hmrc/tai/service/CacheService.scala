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
import uk.gov.hmrc.tai.model.UpstreamErrorResponseFormat.format
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository

import scala.concurrent.{ExecutionContext, Future}

class CacheService @Inject() (sessionCacheRepository: TaiSessionCacheRepository, appConfig: CacheConfig)(implicit
  ec: ExecutionContext
) extends Logging {

  def cacheEither[A](key: String)(
    block: => Future[Either[UpstreamErrorResponse, A]]
  )(implicit hc: HeaderCarrier, fmt: Format[A]): IO[Either[UpstreamErrorResponse, A]] = {
    implicit val eitherFormat: Format[Either[UpstreamErrorResponse, A]] = format[A]
    val blockResult = IO.fromFuture(IO(block))
    lazy val cacheErrorInSecondsTTL = appConfig.cacheErrorInSecondsTTL

    def resultAndCache: IO[Either[UpstreamErrorResponse, A]] =
      blockResult.flatMap { result =>
        if (result.isRight || cacheErrorInSecondsTTL > 0) {
          IO.fromFuture(
            IO(
              sessionCacheRepository
                .putSession(
                  DataKey[Either[UpstreamErrorResponse, A]](key),
                  result
                )
                .map(_ => result)
            )
          )
        } else {
          IO(result)
        }
      }

    val retrievalResult: IO[Option[Either[UpstreamErrorResponse, A]]] =
      IO.fromFuture(
        IO(sessionCacheRepository.getEitherFromSession(DataKey[Either[UpstreamErrorResponse, A]](key)))
      ).recoverWith { case e =>
        logger.warn("An error occurred when retrieving from cache. Will re-execute block to get data.", e)
        IO(None)
      }
    retrievalResult.flatMap {
      case None                                                                                => resultAndCache
      case Some(retrievedItem @ Left(UpstreamErrorResponse(message, statusCode, reportAs, _))) => IO(retrievedItem)
      case Some(Right(r))                                                                      => IO(Right(r))
    }

  }
}
