/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.crypto.Sensitive.SensitiveString
import uk.gov.hmrc.crypto.json.JsonEncryption

import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant


final case class CacheItem(
    id        : String,
    data      : JsObject,
    createdAt : Instant,
    modifiedAt: Instant
)
object CacheItem {

  implicit val format: OFormat[CacheItem] = Json.format

  def encryptedFormat(implicit crypto: Encrypter with Decrypter): OFormat[CacheItem] = {

    implicit val sensitiveFormat: Format[SensitiveString] =
      JsonEncryption.sensitiveEncrypterDecrypter(SensitiveString.apply)

    val encryptedReads: Reads[CacheItem] =
      (
          (__ \ "id").read[String] and
          (__ \ "data").read[SensitiveString] and
          (__ \ "createdAt").read(MongoJavatimeFormats.instantFormat) and
          (__ \ "modifiedAt").read(MongoJavatimeFormats.instantFormat)
      )(
        (id, data, createdAt, modifiedAt) =>
          CacheItem(id, Json.parse(data.decryptedValue).as[JsObject], createdAt, modifiedAt)
      )

    val encryptedWrites: OWrites[CacheItem] =
      (
        (__ \ "id").write[String] and
        (__ \ "data").write[SensitiveString] and
        (__ \ "createdAt").write(MongoJavatimeFormats.instantFormat)  and
        (__ \ "modifiedAt").write(MongoJavatimeFormats.instantFormat)
      )(ci => (ci.id, SensitiveString(Json.stringify(ci.data)), ci.createdAt, ci.modifiedAt))

    OFormat(encryptedReads orElse format, encryptedWrites)
  }
}

case class DataKey[A](unwrap: String) extends AnyVal
