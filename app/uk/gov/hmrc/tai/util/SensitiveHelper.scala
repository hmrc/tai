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
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText, Sensitive}

import scala.util.{Failure, Success, Try}

object SensitiveHelper {
  case class SensitiveJsValue(override val decryptedValue: JsValue) extends Sensitive[JsValue]

  implicit def writesSensitiveJsValue(implicit crypto: Encrypter): Writes[SensitiveJsValue] = { sjo: SensitiveJsValue =>
    JsString(
      log(crypto.encrypt(PlainText(Json.stringify(sjo.decryptedValue))).value, s"encrypting ${sjo.decryptedValue}")
    )
  }

  private def log[A](a: A, s: String): A = {
    println(s"\n***LOGGING:$s")
    a
  }

  implicit def readsSensitiveJsValue[A <: JsValue: Format](implicit crypto: Decrypter): Reads[SensitiveJsValue] = {
    case JsString(s) =>
      Try(log(crypto.decrypt(Crypted(s)), s"decrypting $s")) match {
        case Success(plainText) =>
          println("\nSUCCESSFULLY DECRYPTED")
          JsSuccess(SensitiveJsValue(Json.parse(plainText.value).as[A]))
        case Failure(_: SecurityException) =>
          println("\nNOT DECRYPTED")
          JsSuccess(SensitiveJsValue(JsString(s).as[A]))
        case Failure(exception) => throw exception
      }
    case js: JsValue =>
      println("\nNot a JsString")
      JsSuccess(SensitiveJsValue(js))
  }

  implicit def formatSensitiveJsValue[A <: JsValue: Format](implicit
    crypto: Encrypter with Decrypter
  ): Format[SensitiveJsValue] =
    Format(readsSensitiveJsValue, writesSensitiveJsValue)
}
