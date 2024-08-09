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

  private def writesSensitiveJsValue(implicit crypto: Encrypter): Writes[SensitiveJsValue] = { sjo: SensitiveJsValue =>
    JsString(crypto.encrypt(PlainText(Json.stringify(sjo.decryptedValue))).value)
  }

  private def readsSensitiveJsValue[A <: JsValue: Format](implicit crypto: Decrypter): Reads[SensitiveJsValue] = {
    case JsString(s) =>
      Try(crypto.decrypt(Crypted(s))) match {
        case Success(plainText) => JsSuccess(SensitiveJsValue(Json.parse(plainText.value).as[A]))

        /*
          Both of the below cases cater for two scenarios where the value is not encrypted:-
            either an unencrypted JsString or any other JsValue.
          This is to avoid breaking users' session in case data written before encryption introduced.
         */

        case Failure(_: SecurityException) => JsSuccess(SensitiveJsValue(JsString(s).as[A]))
        case Failure(exception)            => throw exception
      }
    case js: JsValue => JsSuccess(SensitiveJsValue(js))
  }

  def formatSensitiveJsValue[A <: JsValue: Format](implicit
    crypto: Encrypter with Decrypter
  ): Format[SensitiveJsValue] =
    Format(readsSensitiveJsValue, writesSensitiveJsValue)

  def sensitiveReads[A](reads: Reads[A])(implicit crypto: Encrypter with Decrypter): Reads[A] =
    formatSensitiveJsValue[JsObject].map { sensitiveJsValue =>
      reads.reads(sensitiveJsValue.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }

  def sensitiveWrites[A](writes: Writes[A])(implicit crypto: Encrypter): Writes[A] = { o: A =>
    val jsObject = writes.writes(o).as[JsObject]
    JsString(crypto.encrypt(PlainText(Json.stringify(jsObject))).value)
  }

}
