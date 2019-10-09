/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.Play
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.modules.reactivemongo.MongoDbConnection
import uk.gov.hmrc.cache.TimeToLive
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheRepository
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.Future

@Singleton
class TaiCacheRepository @Inject() extends TimeToLive {
  private val expireAfter: Long = defaultExpireAfter
  val repo: CacheRepository = CacheRepository("TAI", expireAfter, Cache.mongoFormats)
}
@Singleton
class CacheConnector @Inject()(cacheRepository: TaiCacheRepository, mongoConfig: MongoConfig)
    extends MongoDbConnection with MongoFormatter {

  implicit val compositeSymmetricCrypto: CompositeSymmetricCrypto = new ApplicationCrypto(
    Play.current.configuration.underlying).JsonCrypto
  private val defaultKey = "TAI-DATA"

  def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }

    cacheRepository.repo.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    cacheRepository.repo.createOrUpdate(cacheId.value, key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if (mongoConfig.mongoEncryptionEnabled) {
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepository.repo.createOrUpdate(cacheId.value, key, jsonData).map(_ => data)
  }

  def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepository.repo.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            if ((json \ key).validate[Protected[T]](jsonDecryptor).isSuccess) {
              Some((json \ key).as[Protected[T]](jsonDecryptor).decryptedValue)
            } else {
              None
            }
          }
        case None => {
          None
        }
      }
    } else {
      cacheRepository.repo.findById(cacheId.value) map {
        case Some(cache) =>
          cache.data flatMap { json =>
            if ((json \ key).validate[T].isSuccess) {
              Some((json \ key).as[T])
            } else {
              None
            }
          }
        case None => {
          None
        }
      }
    }

  def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, key)

  def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      cacheRepository.repo.findById(cacheId.value) map {
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
      cacheRepository.repo.findById(cacheId.value) map {
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
      cacheRepository.repo.findById(cacheId.value) map {
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
      cacheRepository.repo.findById(cacheId.value) map {
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
      writeResult <- cacheRepository.repo.removeById(cacheId.value)
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
