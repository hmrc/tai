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

import org.mongodb.scala.model.IndexModel
import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, MongoDatabaseCollection, TimestampSupport}
import uk.gov.hmrc.play.http.logging.Mdc

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

case object SessionCacheId extends CacheIdType[HeaderCarrier] {
  override def run: HeaderCarrier => String =
    _.sessionId
      .map(_.value)
      .getOrElse(throw NoSessionException)

  case object NoSessionException extends Exception("Could not find sessionId")
}

@Singleton
abstract class SessionCacheRepository @Inject() (
  mongoComponent: MongoComponent,
  override val collectionName: String,
  replaceIndexes: Boolean = true,
  ttl: Duration,
  timestampSupport: TimestampSupport
)(implicit ec: ExecutionContext)
    extends MongoDatabaseCollection {
  /*
    This class exists in hmrc-mongo library but uses the sessionId from the session in the request
    which does not exist in a backend service.
    This class has been adapted from the one from hmrc-mongo to use the session id from the headerCarrier instead.
   */

  private val cacheRepo: MongoCacheRepository[HeaderCarrier] = new MongoCacheRepository[HeaderCarrier](
    mongoComponent = mongoComponent,
    collectionName = collectionName,
    replaceIndexes = replaceIndexes,
    ttl = ttl,
    timestampSupport = timestampSupport,
    cacheIdType = SessionCacheId
  )

  override val indexes: Seq[IndexModel] =
    cacheRepo.indexes

  def putSession[T: Writes](
    dataKey: DataKey[T],
    data: T
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[(String, String)] =
    Mdc.preservingMdc {
      cacheRepo
        .put[T](hc)(dataKey, data)
        .map(res => "sessionId" -> res.id)
    }

  def getFromSession[T: Reads](dataKey: DataKey[T])(implicit hc: HeaderCarrier): Future[Option[T]] =
    Mdc.preservingMdc {
      cacheRepo.get[T](hc)(dataKey)
    }

  def deleteFromSession[T](dataKey: DataKey[T])(implicit hc: HeaderCarrier): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.delete(hc)(dataKey)
    }

  def deleteAllFromSession(implicit hc: HeaderCarrier): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.deleteEntity(hc)
    }
}
