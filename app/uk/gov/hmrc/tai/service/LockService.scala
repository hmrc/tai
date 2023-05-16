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
import com.google.inject.{ImplementedBy, Inject}
import uk.gov.hmrc.mongo.lock.MongoLockRepository

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.MongoConfig

case object OopsCannotAcquireLock extends Exception

@ImplementedBy(classOf[LockServiceImpl])
trait LockService {
  def requestId(implicit hc: HeaderCarrier): String

  def takeLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Boolean]

  def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Unit]
}

class LockServiceImpl @Inject() (lockRepo: MongoLockRepository, appConfig: MongoConfig)(implicit ec: ExecutionContext) extends LockService {

  def requestId(implicit hc: HeaderCarrier): String = hc.sessionId.fold(java.util.UUID.randomUUID.toString)(_.value)

  def takeLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Boolean] = {
    EitherT.right[L](lockRepo
        .takeLock(
          lockId = requestId,
          owner = owner,
          ttl = Duration(20, SECONDS) // this need to be longer than the timeout from http_verbs
        ))
  }

  def releaseLock[L](owner: String)(implicit hc: HeaderCarrier): EitherT[Future, L, Unit] =
    EitherT.right[L](lockRepo
      .releaseLock(
        lockId = requestId,
        owner = owner
      ))
}

