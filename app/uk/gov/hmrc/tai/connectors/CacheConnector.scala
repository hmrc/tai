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

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiCacheRepository @Inject()(mongo: ReactiveMongoComponent, mongoConfig: MongoConfig)(
  implicit ec: ExecutionContext)
    extends CacheMongoRepository("TAI", mongoConfig.mongoTTL)(mongo.mongoConnector.db, ec)

class TaiCacheRepositoryUpdateIncome @Inject()(mongo: ReactiveMongoComponent, mongoConfig: MongoConfig)(
  implicit ec: ExecutionContext)
  extends CacheMongoRepository("TaiUpdateIncome", mongoConfig.mongoTTLUpdateIncome)(mongo.mongoConnector.db, ec)


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

  def createOrUpdateIncome[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepositoryUpdateIncome.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
  }

  def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }

    cacheRepository.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    cacheRepository.createOrUpdate(cacheId.value, key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepository.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
  }

  def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            (json \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
          }
        case None => {
          None
        }
      }
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            (json \ key).validateOpt[T].asOpt.flatten
          }
        case None => {
          None
        }
      }
    }

  def findUpdateIncome[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] = {
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepositoryUpdateIncome.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            (json \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
          }
        case None => {
          None
        }
      }
    } recover {
      case JsResultException(_) => None
    } else {
      cacheRepositoryUpdateIncome.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            (json \ key).validateOpt[T].asOpt.flatten
          }
        case None => {
          None
        }
      }
    }
  }

  def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, key)

  def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            if ((json \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
              Some((json \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue)
            } else {
              None
            }
          } getOrElse {
            Nil
          }
        case None => {
          Nil
        }
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            if ((json \ key).validate[Seq[T]].isSuccess) {
              Some((json \ key).as[Seq[T]])
            } else {
              None
            }
          } getOrElse {
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
          cache.data flatMap { json =>
            if ((json \ key).validate[Protected[Seq[T]]](jsonDecryptor).isSuccess) {
              Some((json \ key).as[Protected[Seq[T]]](jsonDecryptor).decryptedValue)
            } else {
              None
            }
          }
        case None => {
          None
        }
      }
    } else {
      cacheRepository.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            if ((json \ key).validate[Seq[T]].isSuccess) {
              Some((json \ key).as[Seq[T]])
            } else {
              None
            }
          }
        case None => {
          None
        }
      }
    }

  def removeById(cacheId: CacheId): Future[Boolean] =
    for {
      writeResult <- cacheRepository.removeById(cacheId.value)
    } yield {
      if (writeResult.writeErrors.nonEmpty) {
        val errorMessages = writeResult.writeErrors.map(_.errmsg)
        errorMessages.foreach(Logger.error)
        throw new RuntimeException(errorMessages.head)
      } else {
        writeResult.ok
      }
    }
}
