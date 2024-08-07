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
import org.mockito.MockitoSugar.when
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsObject, JsValue, Json, OFormat}
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}

class HodResponseSpec extends PlaySpec {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedStringValue: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedStringValue)

  private def encryptedFormat: OFormat[HodResponse] = HodResponse.encryptedFormat(mockEncrypterDecrypter)

  private val bodyJson: JsObject = Json.obj(
    "testa" -> "valuea",
    "testb" -> "valueb"
  )

  "encryptedFormat" must {
    "write json encrypted" in {
      val hodResponse = HodResponse(body = bodyJson, etag = Some(3))

      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(hodResponse)(encryptedFormat)

      result mustBe Json.obj(
        "body" -> encryptedStringValue,
        "etag" -> 3
      )
    }

    "read json encrypted" in {

      val xx = Json.obj(
        "body" -> encryptedStringValue,
        "etag" -> 3
      )

      val vv = s""""${Json.stringify(bodyJson)}""""
      println("\n>>>>" + vv)

      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(vv))

//      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(bodyJson)))

      val result = xx.as[HodResponse](encryptedFormat)
      result mustBe HodResponse(body = Json.obj(), etag = Some(3))

    }
  }
}
