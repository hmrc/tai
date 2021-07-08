/*
 * Copyright 2021 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{eq => meq}
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.Json
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future
import scala.language.postfixOps

class CachingSpec extends BaseSpec with IntegrationPatience {

  "cache" must {
    "return the json from cache" when {
      "the key is present in the cache" in {
        val sut = cacheTest
        val jsonFromCache = Json.obj("aaa"  -> "bbb")
        val jsonFromFunction = Json.obj("c" -> "d")
        when(cacheConnector.findJson(meq(cacheId), meq(mongoKey)))
          .thenReturn(Future.successful(Some(jsonFromCache)))
        val result = sut.cacheFromApi(nino, mongoKey, Future.successful(jsonFromFunction)).futureValue
        result mustBe jsonFromCache

        verify(metrics, times(1)).incrementCacheHitCounter()
      }
    }

    "return the json from the supplied function" when {
      "the key is not present in the cache" in {
        val sut = cacheTest
        val jsonFromFunction = Json.obj("c" -> "d")
        when(cacheConnector.findJson(meq(cacheId), meq(mongoKey)))
          .thenReturn(Future.successful(None))
        when(
          cacheConnector
            .createOrUpdateJson(meq(cacheId), meq(jsonFromFunction), meq(mongoKey)))
          .thenReturn(Future.successful(jsonFromFunction))
        val result = sut.cacheFromApi(nino, mongoKey, Future.successful(jsonFromFunction)).futureValue
        result mustBe jsonFromFunction

        verify(metrics, times(1)).incrementCacheMissCounter()
      }
    }
  }

  val mongoKey = "mongoKey1"

  def cacheTest = new CachingTest
  val cacheConnector = mock[CacheConnector]
  val metrics = mock[Metrics]
  val cacheMetricsConfig = mock[CacheMetricsConfig]

  when(cacheMetricsConfig.cacheMetricsEnabled).thenReturn(true)

  class CachingTest extends Caching(cacheConnector, metrics, cacheMetricsConfig)
}
