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

package uk.gov.hmrc.tai.util

import play.api.libs.json.*

object EitherFormat {

  given [L, R](using ofmtL: Format[L], ofmtR: Format[R]): Format[Either[L, R]] with
    def reads(json: JsValue): JsResult[Either[L, R]] =
      ofmtL.reads(json).map(Left(_)) orElse ofmtR.reads(json).map(Right(_))

    def writes(either: Either[L, R]): JsValue = either match
      case Left(l)  => ofmtL.writes(l)
      case Right(r) => ofmtR.writes(r)

}
