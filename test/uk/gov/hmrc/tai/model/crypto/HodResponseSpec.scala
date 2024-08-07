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

package uk.gov.hmrc.tai.model.crypto

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsValue, Json, OFormat}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}

class HodResponseSpec extends PlaySpec with BeforeAndAfterEach {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedStringValue: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedStringValue)

  private def encryptedFormat: OFormat[HodResponse] = HodResponse.encryptedFormat(mockEncrypterDecrypter)

  private val unencryptedBodyJson: JsObject = Json.obj(
    "testa" -> "valuea",
    "testb" -> "valueb"
  )

  private val jsonWithEncryptedValue = Json.obj(
    "body" -> encryptedStringValue,
    "etag" -> 3
  )

  private val hodResponse: HodResponse = HodResponse(body = unencryptedBodyJson, etag = Some(3))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  "encryptedFormat" must {
    "write json, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(hodResponse)(encryptedFormat)

      result mustBe jsonWithEncryptedValue

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read JsObject with encrypted JsString, calling decrypt when decrypts successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(unencryptedBodyJson)))

      val result: HodResponse = jsonWithEncryptedValue.as[HodResponse](encryptedFormat)

      result mustBe hodResponse

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsObject with unencrypted JsObject, NOT calling decrypt" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenThrow(new SecurityException("Unable to decrypt value"))

      val jsonWithUnencryptedValue = Json.obj(
        "body" -> unencryptedBodyJson,
        "etag" -> 3
      )

      val result: HodResponse = jsonWithUnencryptedValue.as[HodResponse](encryptedFormat)

      result mustBe hodResponse

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())
    }
  }
}
