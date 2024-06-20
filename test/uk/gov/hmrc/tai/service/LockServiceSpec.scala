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

import org.mongodb.scala.model.Filters
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.lock.{Lock, MongoLockRepository}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
class LockServiceSpec extends BaseSpec with DefaultPlayMongoRepositorySupport[Lock] {

  implicit override lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(additionalConfiguration)
      .configure(
        "mongo.lock.expiryInMicroseconds" -> 2000,
        "auditing.enabled"                -> false
      )
      .build()

  lazy val sut: LockServiceImpl = app.injector.instanceOf[LockServiceImpl]
  lazy val repository: MongoLockRepository = app.injector.instanceOf[MongoLockRepository]

  "TakeLock" should {
    "returns true" when {
      "no lock is present" in {
        val result = sut.takeLock("lockId")
        result.value.futureValue mustBe Right(true)

        find(Filters.equal("_id", sessionIdValue)).map { result: Seq[Lock] =>
          result.size mustBe 1
          val expiry = result.head.expiryTime
          val start = result.head.timeCreated
          ChronoUnit.SECONDS.between(start, expiry) mustBe 2
        }
      }

      "previous lock was released" in {
        val timestamp = Instant.now()
        insert(Lock("some session id", "lockId", timestamp, timestamp.plusSeconds(2)))

        val result = for {
          _      <- sut.releaseLock[Boolean]("lockId")
          result <- sut.takeLock[Boolean]("lockId").value
        } yield result
        result.futureValue mustBe Right(true)

        find(Filters.equal("_id", sessionIdValue)).map { result: Seq[Lock] =>
          result.size mustBe 1
          val expiry = result.head.expiryTime
          val start = result.head.timeCreated
          ChronoUnit.SECONDS.between(start, expiry) mustBe 2
        }
      }
    }

    "returns false" when {
      "a lock is present" in {
        val timestamp = Instant.now()
        Await.result(
          deleteAll().flatMap { _ =>
            insert(Lock("some session id", "lockId", timestamp, timestamp.plusSeconds(2)))
          },
          5 seconds
        )
        val result = sut.takeLock("lockId")
        result.value.futureValue mustBe Right(false)
      }
    }
  }

  "releaseLock" should {
    "released the lock" in {
      val timestamp = Instant.now()
      insert(Lock("some session id", "lockId", timestamp, timestamp.plusSeconds(2)))

      sut.releaseLock[Boolean]("lockId").futureValue

      find(Filters.equal("_id", sessionIdValue)).map { result: Seq[Lock] =>
        result.isEmpty mustBe true
      }
    }
  }

  "sessionId" should {
    "returns the session id" in {
      sut.sessionId mustBe "some session id"
    }
  }
}
