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

object SensitiveHelper {
  case class SensitiveJsObject(override val decryptedValue: JsObject) extends Sensitive[JsObject]

  implicit def writesSensitiveJsObject(implicit crypto: Encrypter): Writes[SensitiveJsObject] = {
    sjo: SensitiveJsObject =>
      JsString(crypto.encrypt(PlainText(Json.stringify(sjo.decryptedValue))).value)
  }

  implicit def readsSensitiveJsObject(implicit crypto: Decrypter): Reads[SensitiveJsObject] = {
    case js @ JsObject(_) => JsSuccess(SensitiveJsObject(js))
    case JsString(s) =>
      val plainText = crypto.decrypt(Crypted(s))
      JsSuccess(SensitiveJsObject(Json.parse(plainText.value).as[JsObject]))
    case jsValue => JsError(s"Unable to create a JsObject from $jsValue")
  }

  implicit def formatSensitiveJsObject(implicit crypto: Encrypter with Decrypter): Format[SensitiveJsObject] =
    Format(readsSensitiveJsObject, writesSensitiveJsObject)
}
