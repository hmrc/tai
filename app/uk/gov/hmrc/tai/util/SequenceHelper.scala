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

import play.api.libs.json.{JsPath, JsResultException, JsonValidationError}

object SequenceHelper {

  def checkForDuplicates[T, K](items: Seq[T], uniqueKey: T => K, keyDescription: K => String): Unit = {
    val duplicates = items
      .groupBy(uniqueKey)
      .collect { case (key, itemSeq) if itemSeq.size > 1 => key }
      .toSeq

    if (duplicates.nonEmpty) {
      val duplicateMessages = duplicates.distinct
        .map(keyDescription)
        .mkString("; ")

      throw JsResultException(
        Seq(
          JsPath -> Seq(JsonValidationError(s"Duplicate entries found for $duplicateMessages"))
        )
      )
    }
  }
}
