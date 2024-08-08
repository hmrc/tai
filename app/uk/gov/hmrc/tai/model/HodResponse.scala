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

package uk.gov.hmrc.tai.model

import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.tai.util.SensitiveHelper.{SensitiveJsValue, readsSensitiveJsValue}

case class HodResponse(body: JsArray, etag: Option[Int])

object HodResponse {
//  private implicit val formats: OFormat[HodResponse] = Json.format[HodResponse]

  implicit def encryptedFormat(implicit crypto: Encrypter with Decrypter): OFormat[HodResponse] = {
    val encryptedReads: Reads[HodResponse] =
      (
        (__ \ "body").read[SensitiveJsValue](readsSensitiveJsValue[JsArray]) and
          (__ \ "etag").readNullable[Int]
      )((body, etag) => HodResponse(body.decryptedValue.as[JsArray], etag))

    val encryptedWrites: OWrites[HodResponse] =
      (
        (__ \ "body").write[SensitiveJsValue] and
          (__ \ "etag").writeNullable[Int]
      )(ua => (SensitiveJsValue(ua.body), ua.etag))

    OFormat(encryptedReads, encryptedWrites)
  }
}
