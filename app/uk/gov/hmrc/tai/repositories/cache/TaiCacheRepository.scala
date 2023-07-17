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
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}

import java.time.{Clock, Instant}
import play.api.libs.json._
import uk.gov.hmrc.crypto.Protected
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.tai.connectors.cache.{CacheId, TaiCacheConnector}
import uk.gov.hmrc.tai.model.domain.{CacheItem, DataKey}

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

trait TaiCacheRepository {
  def createOrUpdate[T](cacheId: CacheId, data: T, key: String)(implicit writes: Writes[T]): Future[T]
  def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String): Future[JsValue]

  def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String)(
    implicit writes: Writes[T]): Future[Seq[T]]

  def find[T](cacheId: CacheId, key: String)(implicit reads: Reads[T]): Future[Option[T]]
  def findJson(cacheId: CacheId, key: String): Future[Option[JsValue]]
  def findSeq[T](cacheId: CacheId, key: String)(implicit reads: Reads[T]): Future[Seq[T]]
  def findOptSeq[T: Reads](cacheId: CacheId, key: String): Future[Option[Seq[T]]]
  def removeById(cacheId: CacheId): Future[Boolean]
}

@Singleton
class DefaultTaiCacheRepository @Inject()(taiCacheConnector: TaiCacheConnector)(implicit ec: ExecutionContext) extends TaiCacheRepository {

  private val defaultKey = "TAI-DATA"

  override def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = Json.toJson(data)
    taiCacheConnector.save(cacheId.value)(key, jsonData).map(_ => data)
  }

  override def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    taiCacheConnector.save(cacheId.value)(key, json).map(_ => json)
  }

  override def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(implicit writes: Writes[T]): Future[Seq[T]] = {
    val jsonData = Json.toJson(data)
    taiCacheConnector.save(cacheId.value)(key, jsonData).map(_ => data)
  }


  private def findById[T](cacheId: CacheId, key: String = defaultKey)
                         (func: String => Future[Option[CacheItem]])
                         (implicit reads: Reads[T]): Future[Option[T]] = {

    OptionT(func(cacheId.value)).map {
      cache =>
          (cache.data \ key).toOption.map { jsValue => jsValue.as[T] }
    }.value.map(_.flatten) recover {
      case JsResultException(_) => None
    }
  }

  override def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] =
    findById(cacheId, key)(taiCacheConnector.findById)(reads)
  override def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] =
    find[JsValue](cacheId, key)

  override def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] =
    findOptSeq(cacheId, key)(reads).map(_.getOrElse(Nil))

  override def findOptSeq[T: Reads](cacheId: CacheId, key: String = defaultKey): Future[Option[Seq[T]]] = {
    implicit val reads: Reads[Protected[Seq[T]]] =
      (json: JsValue) => implicitly[Reads[Seq[T]]].reads(json).map(Protected(_))

    for {
      cache <- OptionT(taiCacheConnector.findById(cacheId.value))
      if (cache.data \ key).validate[Protected[Seq[T]]].isSuccess
    } yield (cache.data \ key).as[Protected[Seq[T]]].decryptedValue
  }.value

  def findOptSeq[T: Reads](cacheId: CacheId, key: String = defaultKey): Future[Option[Seq[T]]] = {
    implicit val reads: Reads[Protected[Seq[T]]] =
      (json: JsValue) => implicitly[Reads[Seq[T]]].reads(json).map(Protected(_))

    for {
      cache <- OptionT(taiCacheConnector.findById(cacheId.value))
      if (cache.data \ key).validate[Protected[Seq[T]]].isSuccess
    } yield (cache.data \ key).as[Protected[Seq[T]]].decryptedValue
  }.value

  override def removeById(cacheId: CacheId): Future[Boolean] =
    taiCacheConnector.deleteEntity(cacheId.value).map(_ : Boolean => true)
}


class CachingTaiCacheRepository @Inject()(
                                           @Named("default") underlying: TaiCacheRepository,
                                           taiCacheMongoRepository: TaiCacheMongoRepository
                                         )(implicit ec: ExecutionContext)
  extends TaiCacheRepository {


  private val defaultKey = "TAI-DATA"
  override def createOrUpdate[T](cacheId: CacheId, data: T, key: String = defaultKey)(implicit writes: Writes[T]): Future[T] = {
    val jsonData = Json.toJson(data)
    save(cacheId, jsonData, key)
  }

  override def createOrUpdateJson(cacheId: CacheId, json: JsValue, key: String = defaultKey): Future[JsValue] = {
    save(cacheId, json, key)
  }

  override def createOrUpdateSeq[T](cacheId: CacheId, data: Seq[T], key: String = defaultKey)(
    implicit writes: Writes[T]): Future[Seq[T]] = {

    val jsonData = Json.toJson(data)
    save(cacheId, jsonData, key)
  }

  private def save(cacheId: CacheId, data: JsValue, key: String): Future[T] = {

    val id = cacheId.value
    val dataKey = DataKey(key)
    taiCacheMongoRepository.collection
      .findOneAndUpdate(
        filter = Filters.eq("id", id),
        update = Updates.combine(
          Updates.setOnInsert("id", id),
          Updates.set("data." + dataKey.unwrap, Codecs.toBson(data)),
          Updates.set("modifiedAt", Instant.now(Clock)),
          Updates.setOnInsert("createdAt", Instant.now(Clock))
        ),
        options = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      )
      .toFuture()
      .map(_ => data)
  }

  override def find[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Option[T]] = {

    taiCacheMongoRepository.collection
      .find(Filters.eq("id", cacheId.value))
      .headOption()
      .map(_.flatMap(cacheItem => (cacheItem.data \ key).validateOpt[T].asOpt).flatten)
      .recover { case JsResultException(_) => None }
  }

  override def findJson(cacheId: CacheId, key: String = defaultKey): Future[Option[JsValue]] = {
    find[JsValue](cacheId, key)
  }

  override def findSeq[T](cacheId: CacheId, key: String = defaultKey)(implicit reads: Reads[T]): Future[Seq[T]] = {
    findOptSeq(cacheId, key)(reads).map(_.getOrElse(Nil))
  }

  override def findOptSeq[T: Reads](cacheId: CacheId, key: String = defaultKey): Future[Option[Seq[T]]] = {
    implicit val reads: Reads[Protected[Seq[T]]] = (json: JsValue) => implicitly[Reads[Seq[T]]].reads(json).map(Protected(_))

    for {
      cache <- OptionT({
        taiCacheMongoRepository.collection
          .find(Filters.eq("id", cacheId.value))
          .headOption()
      })
      if (cache.data \ key).validate[Seq[T]].isSuccess
    } yield (cache.data \ key).as[Seq[T]]
  }.value


  override def removeById(cacheId: CacheId): Future[Boolean] = {
    taiCacheMongoRepository.collection
      .deleteOne(Filters.eq("id", cacheId.value))
      .toFuture()
      .map(_ => true)
  }
}