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
import play.api.libs.json.{JsPath, JsResultException, Reads, Writes}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey, MongoCacheRepository}
import uk.gov.hmrc.mongo.{MongoComponent, MongoDatabaseCollection, TimestampSupport}
import uk.gov.hmrc.mdc.Mdc
import uk.gov.hmrc.tai.config.CacheConfig

import java.time.Instant
import javax.inject.Singleton
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case object NinoCacheId extends CacheIdType[Nino] {
  override def run: Nino => String = _.nino
}

@Singleton
abstract class SessionCacheRepository(
  mongoComponent: MongoComponent,
  override val collectionName: String,
  replaceIndexes: Boolean = true,
  ttl: Duration,
  timestampSupport: TimestampSupport,
  cacheConfig: CacheConfig
)(implicit ec: ExecutionContext)
    extends MongoDatabaseCollection with Logging {

  /*
   * This class exists in hmrc-mongo library as a generic MongoCacheRepository.This implementation uses the
   * NINO as the cache identifier.
   */

  private[cache] val cacheRepo: MongoCacheRepository[Nino] = new MongoCacheRepository[Nino](
    mongoComponent = mongoComponent,
    collectionName = collectionName,
    replaceIndexes = replaceIndexes,
    ttl = ttl,
    timestampSupport = timestampSupport,
    cacheIdType = NinoCacheId
  )

  override val indexes: Seq[IndexModel] =
    cacheRepo.indexes

  def putSession[T: Writes](
    dataKey: DataKey[T],
    data: T,
    nino: Nino
  )(implicit ec: ExecutionContext): Future[(String, String)] =
    Mdc.preservingMdc {
      cacheRepo
        .put[T](nino)(dataKey, data)
        .map(res => "nino" -> res.id)
    }

  def getFromSession[T: Reads](dataKey: DataKey[T], nino: Nino): Future[Option[T]] =
    Mdc.preservingMdc {
      cacheRepo.get[T](nino)(dataKey)
    }

  private def getWithDateTime[A](
    nino: Nino
  )(dataKey: DataKey[A])(implicit reads: Reads[A]): Future[Option[(A, Instant)]] = {
    def dataPath: JsPath =
      dataKey.unwrap.split('.').foldLeft[JsPath](JsPath)(_ \ _)
    cacheRepo
      .findById(nino)
      .map {
        _.flatMap { cache =>
          dataPath
            .asSingleJson(cache.data)
            .validateOpt[A]
            .fold(e => throw JsResultException(e), identity)
            .map(b => (b, cache.modifiedAt))
        }
      }
  }

  def getEitherFromSession[A, B, T <: Either[A, B]](
    dataKey: DataKey[T],
    nino: Nino
  )(implicit rds: Reads[T]): Future[Option[T]] =
    Mdc.preservingMdc {
      getWithDateTime[T](nino)(dataKey)
        .map {
          case None                          => None
          case Some(Tuple2(r @ Right(_), _)) => Some(r)
          case Some(Tuple2(item, instant)) =>
            val ttl = cacheConfig.cacheErrorInSecondsTTL
            if (instant.plusSeconds(ttl).isBefore(Instant.now())) {
              None
            } else {
              Some(item)
            }
        }
        .recoverWith { case NonFatal(error) =>
          logger.error("Failed to read data from session cache: ", error)
          Future.successful(None)
        }
    }

  def deleteFromSession[T](dataKey: DataKey[T], nino: Nino): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.delete(nino)(dataKey)
    }

  def deleteAllFromSession(nino: Nino): Future[Unit] =
    Mdc.preservingMdc {
      cacheRepo.deleteEntity(nino)
    }
}
