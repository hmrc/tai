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

import cats.data.OptionT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiCacheRepository @Inject()(mongo: MongoComponent, mongoConfig: MongoConfig, timestampSupport: TimestampSupport)(
  implicit ec: ExecutionContext)
    extends MongoCacheRepository[String](
      mongo,
      "TAI",
      true,
      ttl = Duration(mongoConfig.mongoTTL, SECONDS),
      timestampSupport,
      CacheIdType.SimpleCacheId)

class TaiCacheRepositoryUpdateIncome @Inject()(
  mongo: MongoComponent,
  mongoConfig: MongoConfig,
  timestampSupport: TimestampSupport)(implicit ec: ExecutionContext)
    extends MongoCacheRepository[String](
      mongo,
      "TaiUpdateIncome",
      true,
      ttl = Duration(mongoConfig.mongoTTLUpdateIncome, SECONDS),
      timestampSupport,
      CacheIdType.SimpleCacheId)

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

  def createOrUpdateIncome[T](cacheId: CacheId, data: T, dataKey: String = defaultKey)(
    implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepositoryUpdateIncome.put[String](cacheId.value)(DataKey(dataKey), jsonData.toString).map(_ => data)
  }

  def createOrUpdate[T](cacheId: CacheId, data: T, dataKey: String = defaultKey)(
    implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }

    //cacheRepository.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
    cacheRepository.put[String](cacheId.value)(DataKey(dataKey), jsonData.toString).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, dataKey: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    cacheRepository.put[String](cacheId.value)(DataKey(dataKey), jsonData.toString).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], dataKey: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepository.put[String](cacheId.value)(DataKey(dataKey), jsonData.toString).map(_ => data)
  }

  def find[T](cacheId: CacheId, dataKey: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          (cache.data \ dataKey).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        case None => None
      }
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) => (cache.data \ dataKey).validateOpt[T].asOpt.flatten
        case None        => None
      }
    }

  def findUpdateIncome[T](cacheId: CacheId, dataKey: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      OptionT(cacheRepositoryUpdateIncome.findById(cacheId.value))
        .map { cache =>
          (cache.data \ dataKey).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        }
        .value
        .map(_.flatten)
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepositoryUpdateIncome.findById(cacheId.value) map {
        case Some(cache) =>
          (cache.data \ dataKey).validateOpt[T].asOpt.flatten
        case None => None
      }
    }

  def findJson(cacheId: CacheId, dataKey: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, dataKey)

  def findSeq[T](cacheId: CacheId, dataKey: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ dataKey).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            (cache.data \ dataKey).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue
          } else {
            Nil
          }
        case None => Nil
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ dataKey).validate[Seq[T]].isSuccess) {
            (cache.data \ dataKey).as[Seq[T]]
          } else {
            Nil
          }
        case None => Nil
      }
    }

  def findOptSeq[T](cacheId: CacheId, dataKey: String = defaultKey)(implicit reads: Reads[T]): Future[Option[Seq[T]]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ dataKey).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            Some((cache.data \ dataKey).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue)
          } else {
            None
          }
        case None => None
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ dataKey).validate[Seq[T]].isSuccess) {
            Some((cache.data \ dataKey).as[Seq[T]])
          } else {
            None
          }
        case None => None
      }
    }

  def removeById(cacheId: CacheId): Future[Boolean] =
    cacheRepository.deleteEntity(cacheId.value).map(_ => true)

}
