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

package uk.gov.hmrc.tai.util

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

class LockedException(message: String) extends Exception(message)

object IORetryExtension {
  implicit class Retryable[A](io: IO[A]) {
    def simpleRetry(noOfRetries: Int, sleep: FiniteDuration): IO[A] = {
      def retryLoop(times: Int): IO[A] =
        io.map(identity).handleErrorWith {
          case _: LockedException if times != 0 =>
            IO.sleep(sleep) >> retryLoop(times - 1)
          case ex => IO.raiseError(ex)
        }
      retryLoop(noOfRetries)
    }
  }
}
