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
class CacheConnector @Inject()(mongoConfig: MongoConfig) extends MongoDbConnection
    with TimeToLive
    with MongoFormatter {

  implicit val compositeSymmetricCrypto: CompositeSymmetricCrypto = new ApplicationCrypto(Play.current.configuration.underlying).JsonCrypto

  private val expireAfter: Long = defaultExpireAfter
  private val defaultKey = "TAI-DATA"

  val cacheRepository: CacheRepository = CacheRepository("TAI", expireAfter, Cache.mongoFormats)

  def createOrUpdate[T](id: String, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[T]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }

    cacheRepository.createOrUpdate(id, key, jsonData).map(_ => data)
  }

  def createOrUpdateJson(id: String, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[JsValue]()
      Json.toJson(Protected(json))(jsonEncryptor)
    } else {
      json
    }

    cacheRepository.createOrUpdate(id, key, jsonData).map(_ => json)
  }

  def createOrUpdateSeq[T](id: String, data: Seq[T], key: String = defaultKey)(implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = if(mongoConfig.mongoEncryptionEnabled){
      val jsonEncryptor = new JsonEncryptor[Seq[T]]()
      Json.toJson(Protected(data))(jsonEncryptor)
    } else {
      Json.toJson(data)
    }
    cacheRepository.createOrUpdate(id, key, jsonData).map(_ => data)
  }

  def find[T](id: String, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] = {
    if(mongoConfig.mongoEncryptionEnabled){
      val jsonDecryptor = new JsonDecryptor[T]()
      cacheRepository.findById(id) map {
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
      cacheRepository.findById(id) map {
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
      cacheRepository.findById(id) map {
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
      cacheRepository.findById(id) map {
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
      cacheRepository.findById(id) map {
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
      cacheRepository.findById(id) map {
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
      writeResult <- cacheRepository.removeById(id)
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