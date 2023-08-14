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

package uk.gov.hmrc.tai.repositories.cache

import cats.data.OptionT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter, Protected}
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.cache.{CacheId, TaiCacheConnector}
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiCacheRepository @Inject()(taiCacheConnector: TaiCacheConnector,
                                   mongoConfig: MongoConfig,
                                   configuration: Configuration)(implicit ec: ExecutionContext)
  extends MongoFormatter {

  implicit lazy val symmetricCryptoFactory: Encrypter with Decrypter =
    new ApplicationCrypto(configuration.underlying).JsonCrypto

  private val defaultKey = "TAI-DATA"


  def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    taiCacheConnector.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }
    taiCacheConnector.save(cacheId.value)(key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    taiCacheConnector.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  private def findById[T](cacheId: CacheId, key: String = defaultKey)
                         (func: String => Future[Option[CacheItem]])
                         (implicit reads: Reads[T]): Future[Option[T]] = {

    OptionT(func(cacheId.value)).map {
      cache =>
        if (mongoConfig.mongoEncryptionEnabled) {
          val jsonDecryptor = new JsonDecryptor[T]()
          (cache.data \ key).toOption.map { jsValue =>
            jsValue.as[Protected[T]](jsonDecryptor).decryptedValue
          }
        }
        else {
          (cache.data \ key).toOption.map { jsValue =>
            jsValue.as[T]
          }
        }
    }.value.map(_.flatten) recover {
      case JsResultException(_) => None
    }
  }


  def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    findById(cacheId, key)(taiCacheConnector.findById)(reads)

  def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, key)

  def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    findOptSeq(cacheId, key)(reads).map(_.getOrElse(Nil))

  def findOptSeq[T: Reads](cacheId: CacheId, key: String = defaultKey): Future[Option[Seq[T]]] = {
    implicit val reads: Reads[Protected[Seq[T]]] = if (mongoConfig.mongoEncryptionEnabled) {
      new JsonDecryptor[Seq[T]]()
    } else {
      (json: JsValue) => implicitly[Reads[Seq[T]]].reads(json).map(Protected(_))
    }
    for {
      cache <- OptionT(taiCacheConnector.findById(cacheId.value))
      if (cache.data \ key).validate[Protected[Seq[T]]].isSuccess
    } yield (cache.data \ key).as[Protected[Seq[T]]].decryptedValue
  }.value

  def removeById(cacheId: CacheId): Future[Boolean] =
    taiCacheConnector.deleteEntity(cacheId.value).map(_ => true) 

}
