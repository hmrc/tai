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

import uk.gov.hmrc.crypto.{Crypted, PlainBytes, PlainText}
import uk.gov.hmrc.tai.util.BaseSpec

class FakeEncrypterDecrypterSpec extends BaseSpec {

  "FakeEncrypterDecrypter" must {
    "encrypt using identity" in {
      val encrypterDecrypter = new FakeEncrypterDecrypter()
      val plaintext = "testString"
      encrypterDecrypter.encrypt(PlainText(plaintext)).value mustBe plaintext
    }

    "decrypt using identity" in {
      val encrypterDecrypter = new FakeEncrypterDecrypter()
      val plaintext = "testString"
      encrypterDecrypter.decrypt(Crypted(plaintext)).value mustBe plaintext
    }

    "encrypt binary using identity" in {
      val encrypterDecrypter = new FakeEncrypterDecrypter()
      val plainText = "testString"
      encrypterDecrypter.encrypt(PlainBytes(plainText.getBytes("UTF-8"))).value mustBe plainText
    }

    "decrypt binary using identity" in {
      val encrypterDecrypter = new FakeEncrypterDecrypter()
      val plainText = "testString"
      encrypterDecrypter.decryptAsBytes(Crypted(plainText)).value mustBe plainText.getBytes("UTF-8")
    }
  }
}
