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

package uk.gov.hmrc.tai.connectors

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Reads, Writes}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.metrics.Metrics

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Caching @Inject()(cacheConnector: CacheConnector, metrics: Metrics, cacheMetricsConfig: CacheMetricsConfig)(
  implicit ec: ExecutionContext) {

  def cacheFromApi(nino: Nino, mongoKey: String, jsonFromApi: => Future[JsValue])(
    implicit hc: HeaderCarrier): Future[JsValue] = {
    val cacheId = CacheId(nino)

    cacheConnector.findJson(cacheId, mongoKey).flatMap {
      case Some(jsonFromCache) =>
        if (cacheMetricsConfig.cacheMetricsEnabled) {
          metrics.incrementCacheHitCounter()
        }
        Future.successful(jsonFromCache)
      case _ =>
        if (cacheMetricsConfig.cacheMetricsEnabled) {
          metrics.incrementCacheMissCounter()
        }
        jsonFromApi.flatMap(cacheConnector.createOrUpdateJson(cacheId, _, mongoKey))
    }
  }

  def cacheFromApiV2[A](nino: Nino, mongoKey: String, jsonFromApi: => Future[A])(
    implicit hc: HeaderCarrier,
    writes: Writes[A],
    reads: Reads[A]): Future[A] = {
    val cacheId = CacheId(nino)

    cacheConnector.find[A](cacheId, mongoKey).flatMap {
      case Some(jsonFromCache) =>
        if (cacheMetricsConfig.cacheMetricsEnabled) {
          metrics.incrementCacheHitCounter()
        }
        Future.successful(jsonFromCache)
      case _ =>
        if (cacheMetricsConfig.cacheMetricsEnabled) {
          metrics.incrementCacheMissCounter()
        }
        jsonFromApi.flatMap(cacheConnector.createOrUpdate[A](cacheId, _, mongoKey))
    }
  }
}
