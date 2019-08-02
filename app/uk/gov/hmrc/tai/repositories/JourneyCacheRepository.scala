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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.tai.connectors.CacheConnector

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class JourneyCacheRepository @Inject()(cacheConnector: CacheConnector) {

  val JourneyCacheSuffix = "_journey_cache"

  def currentCache(journeyName: String)(implicit hc: HeaderCarrier): Future[Option[Map[String, String]]] =
    cacheConnector.find[Map[String, String]](sessionId, journeyName + JourneyCacheSuffix)

  def currentCache(journeyName: String, key: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
    currentCache(journeyName).map({
      case Some(cache) => cache.get(key)
      case _           => None
    })

  def cached(journeyName: String, cache: Map[String, String])(implicit hc: HeaderCarrier): Future[Map[String, String]] =
    currentCache(journeyName).flatMap(existingCache => {
      val toCache =
        existingCache match {
          case Some(existing) => existing ++ cache
          case _              => cache
        }
      cacheConnector.createOrUpdate[Map[String, String]](sessionId, toCache, journeyName + JourneyCacheSuffix)
    })

  def cached(journeyName: String, key: String, value: String)(implicit hc: HeaderCarrier): Future[Map[String, String]] =
    cached(journeyName, Map(key -> value))

  def flush(journeyName: String)(implicit hc: HeaderCarrier): Future[Boolean] =
    cacheConnector
      .createOrUpdate[Map[String, String]](sessionId, Map.empty[String, String], journeyName + JourneyCacheSuffix) map {
      _ =>
        true
    }

  def sessionId(implicit hc: HeaderCarrier): String =
    hc.sessionId.map(_.value).getOrElse(throw new RuntimeException("Error while retrieving session id"))
}
