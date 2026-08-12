/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mongodb.scala.model.IndexModel
import play.api.Logging
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.tai.model.SensitiveWrapper
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, MongoDatabaseCollection, TimestampSupport}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
abstract class GenericCacheRepository[CacheId] @Inject() (
  mongoComponent: MongoComponent,
  override val collectionName: String,
  val crypto: Encrypter with Decrypter,
  replaceIndexes: Boolean = true,
  ttl: Duration,
  timestampSupport: TimestampSupport,
  cacheIdType: CacheIdType[CacheId]
)(implicit ec: ExecutionContext)
    extends MongoDatabaseCollection with Logging {

  private val cacheRepo: MongoCacheRepository[CacheId] = new MongoCacheRepository[CacheId](
    mongoComponent = mongoComponent,
    collectionName = collectionName,
    replaceIndexes = replaceIndexes,
    ttl = ttl,
    timestampSupport = timestampSupport,
    cacheIdType = cacheIdType
  )

  override val indexes: Seq[IndexModel] = cacheRepo.indexes

  given (Encrypter with Decrypter) = crypto

  def put[T: Writes](cacheId: CacheId, dataKey: DataKey[T], data: T): Future[(String, String)] =
    Mdc.preservingMdc {
      cacheRepo
        .put[SensitiveWrapper[T]](cacheId)(DataKey[SensitiveWrapper[T]](dataKey.unwrap), SensitiveWrapper(data))
        .map(res => "id" -> res.id)
    }

  def get[T: Reads](cacheId: CacheId, dataKey: DataKey[T]): Future[Option[T]] =
    Mdc.preservingMdc {
      cacheRepo
        .get[SensitiveWrapper[T]](cacheId)(DataKey[SensitiveWrapper[T]](dataKey.unwrap))
        .map(_.map(_.decryptedValue)) recoverWith { case NonFatal(error) =>
        logger.error(s"Failed to read data from cache", error)
        Future.successful(None)
      }
    }

  def delete[T](cacheId: CacheId, dataKey: DataKey[T]): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.delete(cacheId)(DataKey[SensitiveWrapper[T]](dataKey.unwrap))
    }

  def deleteAll(cacheId: CacheId): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.deleteEntity(cacheId)
    }
}
