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

package uk.gov.hmrc.tai.service

import com.google.inject.Inject
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Crypted, Decrypter, Encrypter, PlainText, Sensitive}

import scala.util.{Failure, Success, Try}

class EncryptionService @Inject() (encrypterDecrypter: Encrypter with Decrypter) {
  import EncryptionService._

  private def writesSensitiveJsValue: Writes[SensitiveJsValue] = { sjo: SensitiveJsValue =>
    JsString(encrypterDecrypter.encrypt(PlainText(Json.stringify(sjo.decryptedValue))).value)
  }

  private def readsSensitiveJsValue[A <: JsValue: Format]: Reads[SensitiveJsValue] = {
    case JsString(s) =>
      Try(encrypterDecrypter.decrypt(Crypted(s))) match {
        case Success(plainText) =>
          JsSuccess(SensitiveJsValue(Json.parse(plainText.value).as[A]))

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

  def sensitiveFormatJsValue[A <: JsValue: Format]: Format[SensitiveJsValue] =
    Format(readsSensitiveJsValue, writesSensitiveJsValue)

  private def sensitiveReadsJsObject[A](reads: Reads[A]): Reads[A] =
    readsSensitiveJsValue[JsObject].map { sensitiveJsValue =>
      reads.reads(sensitiveJsValue.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }

  private def sensitiveReadsJsArray[A](reads: Reads[A]): Reads[A] =
    readsSensitiveJsValue[JsArray].map { sensitiveJsValue =>
      reads.reads(sensitiveJsValue.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }

  private def sensitiveWritesJsValue[A](writes: Writes[A]): Writes[A] = { o: A =>
    val jsValue: JsValue = writes.writes(o)
    JsString(encrypterDecrypter.encrypt(PlainText(Json.stringify(jsValue))).value)
  }

  def sensitiveFormatJsObject[A](implicit
    reads: Reads[A],
    writes: Writes[A]
  ): Format[A] =
    Format[A](
      sensitiveReadsJsObject[A](reads),
      sensitiveWritesJsValue[A](writes)
    )

  def sensitiveFormatJsArray[A](implicit
    reads: Reads[A],
    writes: Writes[A]
  ): Format[A] =
    Format[A](
      sensitiveReadsJsArray[A](reads),
      sensitiveWritesJsValue[A](writes)
    )
}

object EncryptionService {
  case class SensitiveJsValue(override val decryptedValue: JsValue) extends Sensitive[JsValue]
}
