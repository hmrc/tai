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

package uk.gov.hmrc.tai.connectors

import cats.data._
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.{JsResultException, JsValue, Json, Reads, Writes}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.mongo.cache.{CacheIdType, CacheItem, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.tai.config.MongoConfig

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}
@Singleton
class TaiCacheRepository @Inject()(mongo: MongoComponent, mongoConfig: MongoConfig, timestampSupport: TimestampSupport, configuration: Configuration)(
  implicit ec: ExecutionContext)
    extends MongoCacheRepository[String](
      mongoComponent = mongo,
      collectionName = "TAI",
      replaceIndexes = true,
      ttl = Duration(mongoConfig.mongoTTL, SECONDS),
      timestampSupport = timestampSupport,
      cacheIdType = CacheIdType.SimpleCacheId
    ) {

  implicit lazy val compositeSymmetricCrypto
  : CompositeSymmetricCrypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  def save[A: Writes](cacheId: String)(key: String, data: A): Future[CacheItem] = {

    val jsonData: JsValue = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[A]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    put[JsValue](cacheId)(DataKey(key), jsonData)
  }

   def findById[T: Reads](cacheId: String, key: String): Future[Option[T]] =
    OptionT(super.findById(cacheId)).map {
      cache =>
        if (mongoConfig.mongoEncryptionEnabled) {
          val jsonDecryptor = new JsonDecryptor[T]()
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        }
        else (cache.data \ key).validateOpt[T].asOpt.flatten
    }.value.map(_.flatten).recover {
      case JsResultException(_) => None
    }

  def findSeq[T: Reads](cacheId: CacheId, key: String): Future[Seq[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      OptionT(super.findById(cacheId.value)).map {
        cache =>
          if ((cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            (cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue
          } else {
            Nil
          }
      }.value.map(_.getOrElse(Nil))
    } else {
      OptionT(super.findById(cacheId.value)).map {
        case cache =>
          if ((cache.data \ key).validate[Seq[T]].isSuccess) {
            (cache.data \ key).as[Seq[T]]
          } else {
            Nil
          }
      }.value.map(_.getOrElse(Nil))
    }

  def findOptSeq[T: Reads](cacheId: CacheId, key: String): Future[Option[Seq[T]]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      (for {
        cache <- OptionT(super.findById(cacheId.value))
        if (cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess
      } yield (cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue).value
    } else {
      (for {
        cache <- OptionT(super.findById(cacheId.value))
        if (cache.data \ key).validate[Seq[T]].isSuccess
      } yield (cache.data \ key).as[Seq[T]]).value
    }
}
