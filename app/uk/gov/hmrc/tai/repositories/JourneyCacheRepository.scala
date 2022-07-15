/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JourneyCacheRepository @Inject()(cacheConnector: CacheConnector)(implicit ec: ExecutionContext) {

  val JourneyCacheSuffix = "_journey_cache"

  def currentCache(cacheId: CacheId, journeyName: String): Future[Option[Map[String, String]]] = {
    journeyName match {
      case "update-income" => cacheConnector.findUpdateIncome[Map[String, String]](cacheId, journeyName + JourneyCacheSuffix)
      case _ => cacheConnector.find[Map[String, String]](cacheId, journeyName + JourneyCacheSuffix)

    }
  }

  def currentCache(cacheId: CacheId, journeyName: String, key: String): Future[Option[String]] =
    currentCache(cacheId, journeyName).map({
      case Some(cache) => cache.get(key)
      case _ => None
    })

  def cached(cacheId: CacheId, journeyName: String, cache: Map[String, String]): Future[Map[String, String]] = {
    journeyName match {
      case "update-income" =>
        currentCache(cacheId, journeyName).flatMap(existingCache => {
          val toCache =
            existingCache match {
              case Some(existing) => existing ++ cache
              case _ => cache
            }
          cacheConnector.createOrUpdateIncome[Map[String, String]](cacheId, toCache, journeyName + JourneyCacheSuffix)
        })
      case _ =>
        currentCache(cacheId, journeyName).flatMap(existingCache => {
          val toCache =
            existingCache match {
              case Some(existing) => existing ++ cache
              case _ => cache
            }
          cacheConnector.createOrUpdate[Map[String, String]](cacheId, toCache, journeyName + JourneyCacheSuffix)
        })
    }

  }

  def cached(cacheId: CacheId, journeyName: String, key: String, value: String): Future[Map[String, String]] =
    cached(cacheId, journeyName, Map(key -> value))

  def flush(cacheId: CacheId, journeyName: String): Future[Boolean] =
    cacheConnector
      .createOrUpdate[Map[String, String]](cacheId, Map.empty[String, String], journeyName + JourneyCacheSuffix) map {
      _ =>
        true
    }
}
