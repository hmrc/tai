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

import cats.data.EitherT
import com.google.inject.Inject
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.MongoConfig

import javax.inject.Singleton
import scala.util.control.NonFatal

case object OopsCannotAcquireLock extends Exception

trait LockService {
  def sessionId(implicit hc: HeaderCarrier): String

  def takeLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Boolean]

  def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): Future[Unit]
}

@Singleton
class LockServiceImpl @Inject() (lockRepo: MongoLockRepository, appConfig: MongoConfig)(implicit ec: ExecutionContext) extends LockService with Logging {

  def sessionId(implicit hc: HeaderCarrier): String = hc.sessionId.fold{
    val ex = new RuntimeException("Session id is missing from HeaderCarrier")
    logger.error(ex.getMessage, ex)
    java.util.UUID.randomUUID.toString
  }(_.value)

  def takeLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Boolean] = {
    EitherT.right[L](lockRepo
        .takeLock(
          lockId = sessionId,
          owner = owner,
          ttl = Duration(appConfig.mongoLockTTL, SECONDS) // this need to be longer than the timeout from http_verbs
        ).recover {
      case NonFatal(ex) =>
        logger.error(ex.getMessage, ex)
        // This lock is used to lock the cache while it is populated by the HOD response
        // if it is failing we still allow the caller to go through so the user gets a better experience
        // Duplicates calls to HOD will be present in such a case
        true
    })
  }

  def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): Future[Unit] =
    lockRepo
      .releaseLock(
        lockId = sessionId,
        owner = owner
      )
}

