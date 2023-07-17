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

package uk.gov.hmrc.tai.repositories.cache

import cats.data.OptionT
import cats.implicits._
import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.Codecs
import com.google.inject.name.Named
import uk.gov.hmrc.tai.connectors.cache.{CacheId, TaiUpdateIncomeCacheConnector}
import uk.gov.hmrc.tai.model.domain.{CacheItem, DataKey}

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}

trait TaiUpdateIncomeCacheRepository {
  def createOrUpdateIncome[T](cacheId: CacheId, data: T, key: String)(implicit writes: Writes[T]): Future[T]
  def findUpdateIncome[T](cacheId: CacheId, key: String)(implicit reads: Reads[T]): Future[Option[T]]
}

@Singleton
class DefaultTaiUpdateIncomeCacheRepository @Inject()(taiUpdateIncomeCacheConnector: TaiUpdateIncomeCacheConnector)(
  implicit ec: ExecutionContext)
    extends TaiUpdateIncomeCacheRepository {

  private val defaultKey = "TAI-DATA"

  override def createOrUpdateIncome[T](cacheId: CacheId, data: T, key: String = defaultKey)(
    implicit writes: Writes[T]): Future[T] = {

    val jsonData = Json.toJson(data)
    taiUpdateIncomeCacheConnector.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  private def findById[T](cacheId: CacheId, key: String = defaultKey)(func: String => Future[Option[CacheItem]])(
    implicit reads: Reads[T]): Future[Option[T]] =
    OptionT(func(cacheId.value))
      .map { cache =>
        (cache.data \ key).validateOpt[T].asOpt.flatten
      }
      .value
      .map(_.flatten) recover {
      case JsResultException(_) => None
    }

  override def findUpdateIncome[T](cacheId: CacheId, key: String)(implicit reads: Reads[T]): Future[Option[T]] =
    findById(cacheId, key)(taiUpdateIncomeCacheConnector.findById)(reads)

}

class CachingTaiUpdateIncomeCacheRepository @Inject()(
  @Named("default") underlying: TaiUpdateIncomeCacheRepository, // TODO -- SEE UNDERLYING
  taiUpdateIncomeMongoRepository: TaiUpdateIncomeMongoRepository
)(implicit ec: ExecutionContext)
    extends TaiUpdateIncomeCacheRepository {
  override def createOrUpdateIncome[T](cacheId: CacheId, data: T, key: String)(
    implicit writes: Writes[T]): Future[T] = {

    val jsonData = Json.toJson(data)
    val id = cacheId.value
    val dataKey = DataKey(key)
    taiUpdateIncomeMongoRepository.collection
      .findOneAndUpdate(
        filter = Filters.eq("id", id),
        update = Updates.combine(
          Updates.setOnInsert("id", id),
          Updates.set("data." + dataKey.unwrap, Codecs.toBson(jsonData)),
          Updates.set("modifiedAt", Instant.now(Clock)),
          Updates.setOnInsert("createdAt", Instant.now(Clock))
        ),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()
      .map(_ => data)
  }

  override def findUpdateIncome[T](cacheId: CacheId, key: String)(implicit reads: Reads[T]): Future[Option[T]] =
    taiUpdateIncomeMongoRepository.collection
      .find(Filters.eq("id", cacheId.value))
      .headOption()
      .map(_.flatMap(cacheItem => (cacheItem.data \ key).validateOpt[T].asOpt).flatten)
      .recover { case JsResultException(_) => None }
}
