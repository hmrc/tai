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

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText}
import uk.gov.hmrc.tai.util.SensitiveHelper._

class SensitiveHelperSpec extends PlaySpec with BeforeAndAfterEach {
  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"
  private val encryptedValue: Crypted = Crypted(encryptedValueAsString)
  private val unencryptedJsObject: JsObject = Json.obj(
    "testa" -> "valuea",
    "testb" -> "valueb"
  )
  private val unencryptedJsString: JsString = JsString("test")
  private val sensitiveJsObject: SensitiveJsValue = SensitiveJsValue(unencryptedJsObject)
  private val sensitiveJsString: SensitiveJsValue = SensitiveJsValue(unencryptedJsString)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  "formatSensitiveJsValue" must {
    "write JsObject, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(sensitiveJsObject)(formatSensitiveJsValue[JsObject])

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "write JsString, calling encrypt" in {
      when(mockEncrypterDecrypter.encrypt(any())).thenReturn(encryptedValue)

      val result: JsValue = Json.toJson(sensitiveJsString)(formatSensitiveJsValue[JsString])

      result mustBe JsString(encryptedValueAsString)

      verify(mockEncrypterDecrypter, times(1)).encrypt(any())
    }

    "read JsString as a JsObject, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(unencryptedJsObject)))

      val result = JsString(encryptedValueAsString).as[SensitiveJsValue](formatSensitiveJsValue[JsObject])

      result mustBe sensitiveJsObject

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsString as a JsString, calling decrypt successfully" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenReturn(PlainText(Json.stringify(JsString("test"))))

      val result = JsString(encryptedValueAsString).as[SensitiveJsValue](formatSensitiveJsValue[JsString])

      result mustBe SensitiveJsValue(JsString("test"))

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsString as a JsString, calling decrypt unsuccessfully (i.e. not encrypted) and use unencrypted jsString" in {
      when(mockEncrypterDecrypter.decrypt(any())).thenThrow(new SecurityException("Unable to decrypt value"))

      val result = JsString("abc").as[SensitiveJsValue](formatSensitiveJsValue[JsString])

      result mustBe SensitiveJsValue(JsString("abc"))

      verify(mockEncrypterDecrypter, times(1)).decrypt(any())
    }

    "read JsObject, not calling decrypt at all" in {
      val result = unencryptedJsObject.as[SensitiveJsValue](formatSensitiveJsValue[JsObject])

      result mustBe sensitiveJsObject

      verify(mockEncrypterDecrypter, times(0)).decrypt(any())
    }
  }

}
