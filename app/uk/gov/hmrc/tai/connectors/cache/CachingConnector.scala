/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors.cache

import cats.data.EitherT
import com.google.inject.Inject
import play.api.libs.json.Format
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository

import scala.concurrent.{ExecutionContext, Future}

class CachingConnector @Inject() (sessionCacheRepository: TaiSessionCacheRepository)(implicit ec: ExecutionContext) {

  def invalidateAll[A](nino: Nino)(f: => Future[A]): Future[A] =
    sessionCacheRepository.deleteAllFromSession(nino).flatMap { _ =>
      f
    }

  def cache[A: Format](key: String, nino: Nino)(f: => Future[A]): Future[A] = {

    def fetchAndCache: Future[A] =
      for {
        result <- f
        _      <- sessionCacheRepository.putSession[A](DataKey[A](key), result, nino)
      } yield result

    def readAndUpdate: Future[A] =
      sessionCacheRepository
        .getFromSession[A](DataKey[A](key), nino)
        .flatMap {
          case None        => fetchAndCache
          case Some(value) => Future.successful(value)
        }

    readAndUpdate
  }

  def cacheEitherT[L, A: Format](key: String, nino: Nino)(f: => EitherT[Future, L, A]): EitherT[Future, L, A] = {

    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _ <- EitherT[Future, L, (String, String)](
               sessionCacheRepository
                 .putSession[A](DataKey[A](key), result, nino)
                 .map(Right(_))
             )
      } yield result

    def readAndUpdate: EitherT[Future, L, A] =
      EitherT(
        sessionCacheRepository
          .getFromSession[A](DataKey[A](key), nino)
          .flatMap {
            case None =>
              fetchAndCache.value
            case Some(value) =>
              Future.successful(Right(value))
          }
      )

    readAndUpdate
  }
}
