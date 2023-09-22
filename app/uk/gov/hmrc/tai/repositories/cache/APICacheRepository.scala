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

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json.{Format, JsValue, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.play.http.logging.Mdc
import uk.gov.hmrc.tai.config.MongoConfig

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

object APICacheRepository {

  final case class Data(_id: String, json: JsValue, lastUpdated: Instant = Instant.now)

  object Data extends MongoJavatimeFormats.Implicits {

    implicit lazy val format: Format[Data] =
      Json.format[Data]
  }
}

@Singleton
class APICacheRepository @Inject()(
                                    config: MongoConfig,
                                    mongoComponent: MongoComponent
                                  )(implicit ec: ExecutionContext)
  extends PlayMongoRepository[APICacheRepository.Data](
    collectionName = "api-cache",
    mongoComponent = mongoComponent,
    domainFormat = APICacheRepository.Data.format,
    indexes = Seq(
      IndexModel(
        Indexes.ascending("lastUpdated"),
        IndexOptions()
          .name("api-cache-ttl")
          .expireAfter(config.cacheTTL, TimeUnit.SECONDS)
      )
    ),
    replaceIndexes = false
  ) {

  private def byId(id: String): Bson = Filters.equal("_id", id)

  def get(key: String): Future[Option[JsValue]] =
    Mdc.preservingMdc {
      collection
        .find(byId(key))
        .headOption()
        .map(_.map(_.json))
    }

  def set(key: String, value: JsValue): Future[Boolean] =
    Mdc.preservingMdc {
      collection
        .replaceOne(
          filter = byId(key),
          replacement = APICacheRepository.Data(key, value),
          options = ReplaceOptions().upsert(true)
        )
        .toFuture()
        .map(result => result.wasAcknowledged())
    }

  def invalidate(id: String): Future[Boolean] =
    Mdc.preservingMdc {
      collection
        .deleteOne(byId(id))
        .toFuture()
        .map(result => result.wasAcknowledged())
    }
}
