/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.Logger
import play.api.Configuration
import play.api.libs.json.{JsResultException, JsValue, Json, Reads, Writes}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import cats.implicits._
import cats.data.OptionT
import com.mongodb.client.model.Indexes.ascending
import org.mongodb.scala.model._
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import scala.concurrent.duration.{Duration, SECONDS}

/*@Singleton
class TaiCacheRepository @Inject()(mongo: MongoComponent, mongoConfig: MongoConfig)(implicit ec: ExecutionContext)
    extends PlayMongoRepository(
      mongoComponent = mongo,
      collectionName = "TAI",
      domainFormat = MongoFormats.objectIdFormat,
      indexes = Seq(IndexModel(ascending("_id_"), IndexOptions().unique(true))) /* IndexModel() instances, see Migrate index definitions below  */
    )
//extends CacheMongoRepository("TAI", mongoConfig.mongoTTL)(mongo.mongoConnector.db, ec)

class TaiCacheRepositoryUpdateIncome @Inject()(mongo: MongoComponent, mongoConfig: MongoConfig)(
  implicit ec: ExecutionContext)
    extends PlayMongoRepository(
      mongoComponent = mongo,
      collectionName = "TaiUpdateIncome",
      domainFormat = MongoFormats.objectIdFormat,
      indexes = Seq(IndexModel(ascending("_id_"), IndexOptions().unique(true))) /* IndexModel() instances, see Migrate index definitions below  */
    )*/
@Singleton
class TaiCacheRepository @Inject()(mongo: MongoComponent, mongoConfig: MongoConfig, timestampSupport: TimestampSupport)(
  implicit ec: ExecutionContext)
    extends MongoCacheRepository[String](
      mongoComponent = mongo,
      collectionName = "TAI",
      replaceIndexes = false,
      ttl = Duration(mongoConfig.mongoTTLUpdateIncome, SECONDS),
      timestampSupport = timestampSupport,
      cacheIdType = CacheIdType.SimpleCacheId
    )

class TaiCacheRepositoryUpdateIncome @Inject()(
  mongo: MongoComponent,
  mongoConfig: MongoConfig,
  timestampSupport: TimestampSupport)(implicit ec: ExecutionContext)
    extends MongoCacheRepository(
      mongoComponent = mongo,
      collectionName = "TaiUpdateIncome",
      replaceIndexes = false,
      ttl = Duration(mongoConfig.mongoTTLUpdateIncome, SECONDS),
      timestampSupport = timestampSupport,
      cacheIdType = CacheIdType.SimpleCacheId
    )
//indexes = Seq(IndexModel(ascending("_id_"), IndexOptions().unique(true)))

@Singleton
class CacheConnector @Inject()(
  cacheRepository: TaiCacheRepository,
  cacheRepositoryUpdateIncome: TaiCacheRepositoryUpdateIncome,
  mongoConfig: MongoConfig,
  configuration: Configuration)(implicit ec: ExecutionContext)
    extends MongoFormatter {

  implicit lazy val compositeSymmetricCrypto
    : CompositeSymmetricCrypto = new ApplicationCrypto(configuration.underlying).JsonCrypto
  private val defaultKey = "TAI-DATA"

  def createOrUpdateIncome[T](cacheId: CacheId, data: T, key: String = defaultKey)(
    implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepositoryUpdateIncome.put(cacheId.value)(DataKey(key), jsonData).map(_ => data)
  }

  def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    println("Calling cacheRepository.put")
    cacheRepository.put[String](cacheId.value)(DataKey[String](key), jsonData.toString()).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    cacheRepository.put(cacheId.value)(DataKey(key), jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepository.put(cacheId.value)(DataKey(key), jsonData).map(_ => data)
  }

  def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        case None => None
      }
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) => (cache.data \ key).validateOpt[T].asOpt.flatten
        case None        => None
      }
    }

  def findUpdateIncome[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      OptionT(cacheRepositoryUpdateIncome.findById(cacheId.value))
        .map { cache =>
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        }
        .value
        .map(_.flatten)
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepositoryUpdateIncome.findById(cacheId.value) map {
        case Some(cache) =>
          (cache.data \ key).validateOpt[T].asOpt.flatten
        case None => None

      }
    }

  def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, key)

  def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            (cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue
          } else {
            Nil
          }
        case None => {
          Nil
        }
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Seq[T]].isSuccess) {
            (cache.data \ key).as[Seq[T]]
          } else {
            Nil
          }
        case None => {
          Nil
        }
      }
    }

  def findOptSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[Seq[T]]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            Some((cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue)
          } else {
            None
          }
        case None => None
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Seq[T]].isSuccess) {
            Some((cache.data \ key).as[Seq[T]])
          } else {
            None
          }
        case None => None
      }
    }

  def removeById(cacheId: CacheId): Future[Boolean] =
    cacheRepository.deleteEntity(cacheId.value).map(_ => true)

}
