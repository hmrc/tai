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

package uk.gov.hmrc.tai.service

import cats.effect.IO
import com.google.inject.Inject
import play.api.Logging
import uk.gov.hmrc.http.{HeaderCarrier, LockedException}
import uk.gov.hmrc.mongo.lock.MongoLockRepository
import uk.gov.hmrc.tai.config.MongoConfig

import javax.inject.Singleton
import scala.concurrent.duration.{Duration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class LockService @Inject() (lockRepo: MongoLockRepository, appConfig: MongoConfig)(implicit ec: ExecutionContext)
    extends Logging {

  private def recoverRelease[A](result: IO[A]): PartialFunction[Throwable, IO[A]] = { case NonFatal(ex) =>
    logger.warn("Error while releasing lock: " + ex.getMessage, ex)
    result
  }

  def withLock[A](key: String)(block: => IO[A])(implicit hc: HeaderCarrier): IO[A] =
    IO.fromFuture(IO(takeLock(key)))
      .bracket { isLockAcquired =>
        if (isLockAcquired) {
          block
        } else {
          IO.raiseError[A](new LockedException(s"Lock for $key could not be acquired"))
        }
      } { _ =>
        IO.fromFuture(IO(releaseLock(key))).recoverWith(recoverRelease(IO((): Unit)))
      }

  private def takeLock[L](owner: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    lockRepo
      .takeLock(
        lockId = sessionId,
        owner = owner,
        ttl = Duration(appConfig.mongoLockTTL, MILLISECONDS) // this need to be longer than the timeout from http_verbs
      )
      .map(_.fold(false)(_ => true))
      .recover { case NonFatal(ex) =>
        logger.error(ex.getMessage, ex)
        // This lock is used to lock the cache while it is populated by the HOD response
        // if it is failing we still allow the caller to go through so the user gets a better experience
        // Duplicates calls to HOD will be present in such a case
        true
      }

  private def sessionId(implicit hc: HeaderCarrier): String = hc.sessionId.fold {
    val ex = new RuntimeException("Session id is missing from HeaderCarrier")
    logger.error(ex.getMessage, ex)
    java.util.UUID.randomUUID.toString
  }(_.value)

  private def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): Future[Unit] =
    lockRepo
      .releaseLock(
        lockId = sessionId,
        owner = owner
      )
}
