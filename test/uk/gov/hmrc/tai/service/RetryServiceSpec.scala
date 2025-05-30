/*
 * Copyright 2024 HM Revenue & Customs
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
import org.mockito.Mockito.{reset, when}
import uk.gov.hmrc.tai.config.RtiConfig
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

class RetryServiceSpec extends BaseSpec {
  private val mockRtiConfig: RtiConfig = mock[RtiConfig]
  private val sut: RetryService = new RetryService(mockRtiConfig)
  private val runTimeException = new RuntimeException("Failure")
  private class TestClass {
    var attemptNo: Int = 1
    def run(): String =
      if (attemptNo == 1) {
        attemptNo = 2
        throw new IndexOutOfBoundsException("Failure")
      } else if (attemptNo == 2) {
        attemptNo = 3
        throw runTimeException
      } else {
        "success"
      }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRtiConfig)
    when(mockRtiConfig.hodRetryMaximum).thenReturn(2)
    when(mockRtiConfig.hodRetryDelayInMillis).thenReturn(10)

    ()
  }
  private val excludeSecondAttempt: PartialFunction[Throwable, Boolean] = { case _: IndexOutOfBoundsException => true }
  "retry" must {
    "return success when succeeds on 3rd attempt and no constraints" in {
      val testInstance = new TestClass()
      whenReady(sut.withRetry[String]()(IO(testInstance.run()))) { result =>
        result mustBe "success"
        testInstance.attemptNo mustBe 3
      }
    }

    "return failure when would succeed on 3rd attempt but only retries once" in {
      when(mockRtiConfig.hodRetryMaximum).thenReturn(1)
      val testInstance = new TestClass()
      Try(Await.result(sut.withRetry[String]()(IO(testInstance.run())), Duration.Inf)) mustBe Failure(
        runTimeException
      )
    }

    "return failure when constraint to exclude exception on 2nd attempt" in {
      val testInstance = new TestClass()
      Try(
        Await.result(sut.withRetry[String](excludeSecondAttempt)(IO(testInstance.run())), Duration.Inf)
      ) mustBe Failure(
        runTimeException
      )
    }

  }
}
