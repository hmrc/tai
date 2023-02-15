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

package uk.gov.hmrc.tai.connectors.cache

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.concurrent.IntegrationPatience
import play.api.libs.json.Json
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.factory.TaxCodeHistoryFactory
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.repositories.cache.TaiCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CachingSpec extends BaseSpec with IntegrationPatience {

  "cache" must {
    "return the json from cache" when {
      "the key is present in the cache" in {
        val sut = cacheTest
        val jsonFromCache = Json.obj("aaa"  -> "bbb")
        val jsonFromFunction = Json.obj("c" -> "d")
        when(taiCacheRepository.findJson(meq(cacheId), meq(mongoKey)))
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
        when(taiCacheRepository.findJson(meq(cacheId), meq(mongoKey)))
          .thenReturn(Future.successful(None))
        when(
          taiCacheRepository
            .createOrUpdateJson(meq(cacheId), meq(jsonFromFunction), meq(mongoKey)))
          .thenReturn(Future.successful(jsonFromFunction))
        val result = sut.cacheFromApi(nino, mongoKey, Future.successful(jsonFromFunction)).futureValue
        result mustBe jsonFromFunction

        verify(metrics, times(1)).incrementCacheMissCounter()
      }
    }
  }

  val taxCodeHistory = TaxCodeHistoryFactory.createTaxCodeHistory(nino)

  "cacheFromApiV2" must {
    "return the TaxCodeHistory from cache" when {
      "the key is present in the cache" in {
        val sut = cacheTest
        when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(mongoKey))(any()))
          .thenReturn(Future.successful(Some(taxCodeHistory)))
        val result = sut.cacheFromApiV2[TaxCodeHistory](nino, mongoKey, Future.successful(taxCodeHistory)).futureValue
        result mustBe taxCodeHistory

        verify(metrics, times(2)).incrementCacheHitCounter()
      }
    }

    "return the TaxCodeHistory from the supplied function" when {
      "the key is not present in the cache" in {
        val sut = cacheTest
        val jsonFromFunction = Json.obj("c" -> "d")
        when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(mongoKey))(any()))
          .thenReturn(Future.successful(None))
        when(
          taiCacheRepository
            .createOrUpdate[TaxCodeHistory](meq(cacheId), meq(taxCodeHistory), meq(mongoKey))(any()))
          .thenReturn(Future.successful(taxCodeHistory))
        val result = sut.cacheFromApiV2[TaxCodeHistory](nino, mongoKey, Future.successful(taxCodeHistory)).futureValue
        result mustBe taxCodeHistory

        verify(metrics, times(2)).incrementCacheMissCounter()
      }
    }
  }

  val mongoKey = "mongoKey1"

  def cacheTest = new CachingTest
  val taiCacheRepository = mock[TaiCacheRepository]
  val metrics = mock[Metrics]
  val cacheMetricsConfig = mock[CacheMetricsConfig]

  when(cacheMetricsConfig.cacheMetricsEnabled).thenReturn(true)

  class CachingTest extends Caching(taiCacheRepository, metrics, cacheMetricsConfig)
}
