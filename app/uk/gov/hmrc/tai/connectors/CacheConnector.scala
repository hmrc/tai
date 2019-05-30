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
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import reactivemongo.api.commands.{WriteConcern, WriteResult}
import reactivemongo.api.{DB, ReadPreference}
import uk.gov.hmrc.cache.TimeToLive
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.cache.repository.{CacheMongoRepository, CacheRepository}
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaiCacheRepository @Inject()(mongo: ReactiveMongoComponent)(implicit ec: ExecutionContext) extends TimeToLive {
  private val expireAfter: Long = defaultExpireAfter
  private val cacheRepository = new CacheMongoRepository("TAI", expireAfter, Cache.mongoFormats)(mongo.mongoConnector.db, ec)

  def createOrUpdate(id: Id, key: String, toCache: JsValue): Future[DatabaseUpdate[Cache]] = {
    cacheRepository.createOrUpdate(id, key, toCache)
  }

  def findById(id: Id, readPreference: ReadPreference = ReadPreference.primaryPreferred)(implicit ec: ExecutionContext): Future[Option[Cache]] = {
    cacheRepository.findById(id)
  }

  def removeById(id: Id, writeConcern: WriteConcern = WriteConcern.Default)(implicit ec: ExecutionContext): Future[WriteResult] = {
    cacheRepository.removeById(id)
  }
}


@Singleton
class CacheConnector @Inject()(taiCacheRepository: TaiCacheRepository, mongoConfig: MongoConfig) extends MongoFormatter {

  implicit val compositeSymmetricCrypto: CompositeSymmetricCrypto = new ApplicationCrypto(Play.current.configuration.underlying).JsonCrypto
  private val defaultKey = "TAI-DATA"

  def createOrUpdate[T](id: String, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }

    taiCacheRepository.createOrUpdate(id, key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(id: String, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    taiCacheRepository.createOrUpdate(id, key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](id: String, data: Seq[T], key: String = defaultKey)(implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    taiCacheRepository.createOrUpdate(id, key, jsonData).map(_ => data)
  }

  def find[T](id: String, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] = {
    if(mongoConfig.mongoEncryptionEnabled){
      val jsonDecryptor = new JsonDecryptor[T]()
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
  }

  def findJson(id: String, key: String = defaultKey): Future[Option[JsValue]] = find[JsValue](id, key)

  def findSeq[T](id: String, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] = {
    if (mongoConfig.mongoEncryptionEnabled) {
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
  }

  def findOptSeq[T](id: String, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[Seq[T]]] = {
    if(mongoConfig.mongoEncryptionEnabled){
      val jsonDecryptor = new JsonDecryptor[Seq[T]]()
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
      taiCacheRepository.findById(id) map {
        case Some(cache) => cache.data flatMap {
          json =>
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
  }

  def removeById(id: String): Future[Boolean] = {
    for {
      writeResult <- taiCacheRepository.removeById(id)
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
}