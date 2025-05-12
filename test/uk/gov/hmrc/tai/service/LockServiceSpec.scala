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
import cats.effect.unsafe.implicits.global
import org.mongodb.scala.model.Filters
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.lock.{Lock, MongoLockRepository}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.tai.util.{BaseSpec, LockedException}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class LockServiceSpec extends BaseSpec with DefaultPlayMongoRepositorySupport[Lock] {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(additionalConfiguration)
      .configure(
        "mongo.lock.expiryInMilliseconds" -> 2000,
        "auditing.enabled"                -> false
      )
      .build()

  lazy val sut: LockService = app.injector.instanceOf[LockService]
  val repository: MongoLockRepository = app.injector.instanceOf[MongoLockRepository]

  private def findForSessionId: Future[Seq[Lock]] = find(Filters.equal("_id", sessionIdValue))

  "withLock" must {
    "take lock when no lock is present and release it when action completed" in {
      val ioResult = sut.withLock[Seq[Lock]]("lockId") {
        IO.fromFuture(IO(findForSessionId))
      }
      val result = Await.result(ioResult.unsafeToFuture(), Duration.Inf)

      result.size mustBe 1
      val expiry = result.head.expiryTime
      val start = result.head.timeCreated
      ChronoUnit.SECONDS.between(start, expiry) mustBe 2

      Await.result(findForSessionId, Duration.Inf).size mustBe 0
    }

    "return locked exception when a lock is present" in {
      val timestamp = Instant.now()
      Await.result(
        deleteAll().flatMap { _ =>
          insert(Lock(sessionIdValue, "lockId", timestamp, timestamp.plusSeconds(2)))
        },
        Duration.Inf
      )

      val ioResult = sut.withLock[Seq[Lock]]("lockId") {
        IO.fromFuture(IO(findForSessionId))
      }
      a[LockedException] mustBe thrownBy(Await.result(ioResult.unsafeToFuture(), Duration.Inf))
    }
  }
}
