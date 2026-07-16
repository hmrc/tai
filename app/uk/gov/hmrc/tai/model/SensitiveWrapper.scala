/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model

import play.api.libs.json.*
import uk.gov.hmrc.crypto.json.JsonEncryption.{sensitiveDecrypter, sensitiveEncrypter}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, Sensitive}

case class SensitiveWrapper[T](override val decryptedValue: T) extends Sensitive[T]

object SensitiveWrapper {

  implicit def reads[T](implicit
    reads: Reads[T],
    crypto: Encrypter with Decrypter
  ): Reads[SensitiveWrapper[T]] = sensitiveDecrypter(SensitiveWrapper[T])

  implicit def writes[T](implicit
    writes: Writes[T],
    crypto: Encrypter with Decrypter
  ): Writes[SensitiveWrapper[T]] = sensitiveEncrypter

}
