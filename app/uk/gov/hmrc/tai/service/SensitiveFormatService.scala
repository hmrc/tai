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
import uk.gov.hmrc.tai.config.MongoConfig

import scala.util.{Failure, Success, Try}

class SensitiveFormatService @Inject() (encrypterDecrypter: Encrypter with Decrypter, mongoConfig: MongoConfig) {
  import SensitiveFormatService._

  private def writeJsValueWithEncryption(jsValue: JsValue): JsValue =
    if (mongoConfig.mongoEncryptionEnabled) {
      JsString(encrypterDecrypter.encrypt(PlainText(Json.stringify(jsValue))).value)
    } else {
      jsValue
    }

  private def sensitiveReadsJsValue[A <: JsValue: Format]: Reads[SensitiveJsValue] = {
    case jsString @ JsString(s) =>
      if (mongoConfig.mongoEncryptionEnabled) {
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
      } else {
        JsSuccess(SensitiveJsValue(jsString))
      }

    case js: JsValue => JsSuccess(SensitiveJsValue(js))
  }

  private def sensitiveReadsJsObject[A](reads: Reads[A]): Reads[A] =
    sensitiveReadsJsValue[JsObject].map { sensitiveJsValue =>
      reads.reads(sensitiveJsValue.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }

  private def sensitiveReadsJsArray[A](reads: Reads[A]): Reads[A] =
    sensitiveReadsJsValue[JsArray].map { sensitiveJsValue =>
      reads.reads(sensitiveJsValue.decryptedValue) match {
        case JsSuccess(value, _) => value
        case JsError(e)          => throw JsResultException(e)
      }
    }

  private def sensitiveWritesJsValue[A](writes: Writes[A]): Writes[A] = { o: A =>
    writeJsValueWithEncryption(writes.writes(o))
  }

  def sensitiveFormatJsValue[A <: JsValue: Format]: Format[SensitiveJsValue] =
    Format(sensitiveReadsJsValue, sjo => writeJsValueWithEncryption(sjo.decryptedValue))

  def sensitiveFormatFromReadsWrites[A](implicit
    reads: Reads[A],
    writes: Writes[A]
  ): Format[A] =
    Format[A](
      sensitiveReadsJsObject[A](reads),
      sensitiveWritesJsValue[A](writes)
    )

  def sensitiveFormatFromReadsWritesJsArray[A](implicit
    reads: Reads[A],
    writes: Writes[A]
  ): Format[A] =
    Format[A](
      sensitiveReadsJsArray[A](reads),
      sensitiveWritesJsValue[A](writes)
    )
}

object SensitiveFormatService {
  case class SensitiveJsValue(override val decryptedValue: JsValue) extends Sensitive[JsValue]
}
