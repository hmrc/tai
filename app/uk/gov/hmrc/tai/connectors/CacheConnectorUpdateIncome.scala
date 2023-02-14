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

import cats.data.OptionT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.crypto.json.{JsonDecryptor, JsonEncryptor}
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.nps2.MongoFormatter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CacheConnectorUpdateIncome @Inject()(
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

  private def findById[T](cacheId: CacheId, key: String = defaultKey)
                         (func: String => Future[Option[CacheItem]])
                         (implicit reads: Reads[T]): Future[Option[T]] = {

    OptionT(func(cacheId.value)).map {
      cache =>
        if (mongoConfig.mongoEncryptionEnabled) {
          val jsonDecryptor = new JsonDecryptor[T]()
          (cache.data \ key).validateOpt[Protected[T]](jsonDecryptor).asOpt.flatten.map(_.decryptedValue)
        }
        else {
          (cache.data \ key).validateOpt[T].asOpt.flatten
        }
    }.value.map(_.flatten) recover {
      case JsResultException(_) => None
    }
  }

  def findUpdateIncome[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    findById(cacheId, key)(taiUpdateIncomeCacheRepository.findById)(reads)

}
