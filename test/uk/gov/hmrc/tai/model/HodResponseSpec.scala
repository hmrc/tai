/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.tai.service.EncryptionService

class HodResponseSpec extends PlaySpec with BeforeAndAfterEach {

  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedValueAsString)

  private val unencryptedBodyJson: JsArray = Json.arr(
    Json.obj("testa" -> "valuea"),
    Json.obj("testb" -> "valueb")
  )

  private val validJson = Json.obj(
    "body" -> unencryptedBodyJson,
    "etag" -> 3
  )

  private val hodResponse = HodResponse(body = unencryptedBodyJson, etag = Some(3))

  private val encryptionService = new EncryptionService()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  private val formatWithEncryption: Format[HodResponse] =
    encryptionService
      .sensitiveFormatJsObject[HodResponse](HodResponse.format, HodResponse.format, mockEncrypterDecrypter)

  "formatWithEncryption" must {
    "write encrypted, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(hodResponse)(formatWithEncryption)

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read encrypted, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(validJson)))

      val result = JsString(encryptedValueAsString).as[HodResponse](formatWithEncryption)

      result mustBe hodResponse

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read unencrypted JsObject, not calling decrypt at all" in {
      val result = validJson.as[HodResponse](formatWithEncryption)

      result mustBe hodResponse

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())

    }
  }

}
