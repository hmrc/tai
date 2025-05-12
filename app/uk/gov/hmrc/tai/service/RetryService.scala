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
import cats.effect.unsafe.implicits.global
import com.google.inject.Inject
import uk.gov.hmrc.tai.config.RtiConfig

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
class RetryService @Inject() (appConfig: RtiConfig) {
  import RetryService.*

  private val retryAllExceptions: PartialFunction[Throwable, Boolean] = { case _ => true }

  def withRetry[A](
    exceptionsToRetry: PartialFunction[Throwable, Boolean] = retryAllExceptions
  )(block: => IO[A]): Future[A] =
    block
      .simpleRetry(appConfig.hodRetryMaximum, appConfig.hodRetryDelayInMillis.millis, exceptionsToRetry)
      .unsafeToFuture()
}

object RetryService {
  private implicit class Retryable[A](io: IO[A]) {
    def simpleRetry(
      noOfRetries: Int,
      sleep: FiniteDuration,
      exceptionsToRetry: PartialFunction[Throwable, Boolean]
    ): IO[A] = {
      def retryLoop(times: Int): IO[A] =
        io.map(identity).handleErrorWith { ex =>
          if (exceptionsToRetry.applyOrElse(ex, _ => false) && times != 0) {
            IO.sleep(sleep) >> retryLoop(times - 1)
          } else {
            IO.raiseError(ex)
          }
        }
      retryLoop(noOfRetries)
    }
  }
}
