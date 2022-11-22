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
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CacheConnector @Inject()(
  taiCacheRepository: TaiCacheRepository,
  taiUpdateIncomeCacheRepository: TaiUpdateIncomeCacheRepository,
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
    taiUpdateIncomeCacheRepository.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    taiCacheRepository.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }
    taiCacheRepository.save(cacheId.value)(key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    taiCacheRepository.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      taiCacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        case None => None
      }
    } recover {
      case JsResultException(_) => None
    } else {
      taiCacheRepository.findById(cacheId.value) map {
        case Some(cache) => (cache.data \ key).validateOpt[T].asOpt.flatten
        case None        => None
      }
    }

  def findUpdateIncome[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      OptionT(taiUpdateIncomeCacheRepository.findById(cacheId.value))
        .map { cache =>
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        }
        .value
        .map(_.flatten)
    } recover {
      case JsResultException(_) => None
    } else {
      taiUpdateIncomeCacheRepository.findById(cacheId.value) map {
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
      taiCacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            (cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue
          } else {
            Nil
          }
        case None => Nil
      }
    } else {
      taiCacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Seq[T]].isSuccess) {
            (cache.data \ key).as[Seq[T]]
          } else {
            Nil
          }
        case None => Nil
      }
    }

  def findOptSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[Seq[T]]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      taiCacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          if ((cache.data \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
            Some((cache.data \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue)
          } else {
            None
          }
        case None => None
      }
    } else {
      taiCacheRepository.findById(cacheId.value) map {
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
    taiCacheRepository.deleteEntity(cacheId.value).map(_ => true)

}
