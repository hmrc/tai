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

package uk.gov.hmrc.tai.util

import play.api.libs.json._

import scala.util.{Failure, Success, Try}

object JsonHelper {
  private def combineReads[A](
    firstReads: Reads[A],
    secondReads: Reads[A]
  ): Reads[A] = {
    def trySecondReads(
      secondReads: Reads[A],
      jsValue: JsValue,
      firstReadsOutcome: Either[JsResultException, JsError]
    ): JsResult[A] =
      (Try(secondReads.reads(jsValue)), firstReadsOutcome) match {
        case (Success(value @ JsSuccess(_, _)), _)                      => value
        case (Success(JsError(_)), Right(firstReadsErrors))             => firstReadsErrors
        case (Success(JsError(_)), Left(firstReadsException))           => throw firstReadsException
        case (Failure(_: JsResultException), Right(firstReadsErrors))   => firstReadsErrors
        case (Failure(_: JsResultException), Left(firstReadsException)) => throw firstReadsException
        case (Failure(exception), _)                                    => throw exception
      }
    Reads { jsValue =>
      Try(firstReads.reads(jsValue)) match {
        case Success(value @ JsSuccess(_, _))       => value
        case Success(firstReadsErrors @ JsError(_)) => trySecondReads(secondReads, jsValue, Right(firstReadsErrors))
        case Failure(e: JsResultException)          => trySecondReads(secondReads, jsValue, Left(e))
        case Failure(exception)                     => throw exception
      }
    }
  }
  
  /*
    Same as orElse but if the second reads fails then return the first reads' errors instead of the second.
    Otherwise we wouldn't know what had failed.
   */
  implicit class OrElseTry[A](reads: Reads[A]) {
    def orElseTry(bReads: Reads[A]): Reads[A] =
      combineReads(reads, bReads)
  }
}
