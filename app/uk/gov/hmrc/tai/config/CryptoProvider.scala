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

package uk.gov.hmrc.tai.config

import play.api.Configuration
import uk.gov.hmrc.crypto.{Decrypter, Encrypter, SymmetricCryptoFactory}

import javax.inject.{Inject, Provider, Singleton}

@Singleton
class CryptoProvider @Inject() (
  configuration: Configuration,
  fakeEncrypterDecrypter: FakeEncrypterDecrypter
) extends Provider[Encrypter with Decrypter] {

  override def get(): Encrypter with Decrypter = {
    val mongoEncryptionEnabled = configuration
      .getOptional[Boolean]("mongo.encryption.enabled")
      .getOrElse(true)
    if (mongoEncryptionEnabled) {
      SymmetricCryptoFactory.aesGcmCryptoFromConfig(baseConfigKey = "mongo.encryption", configuration.underlying)
    } else {
      fakeEncrypterDecrypter
    }
  }
}
