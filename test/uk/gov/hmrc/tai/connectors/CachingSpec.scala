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

package uk.gov.hmrc.tai.connectors

import org.mockito.Matchers
import org.mockito.Mockito._

import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.metrics.Metrics

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.Random

class CachingSpec extends PlaySpec with MockitoSugar {

  "cache" must {
    "return the json from cache" when {
      "the key is present in the cache" in {
        val sut = cacheTest
        val jsonFromCache = Json.obj("aaa"  -> "bbb")
        val jsonFromFunction = Json.obj("c" -> "d")
        when(cacheConnector.findJson(Matchers.eq(cacheId), Matchers.eq(mongoKey))(Matchers.eq(hc)))
          .thenReturn(Future.successful(Some(jsonFromCache)))
        val result = Await.result(sut.cacheFromApi(nino, mongoKey, Future.successful(jsonFromFunction)), 5 seconds)
        result mustBe jsonFromCache

        verify(metrics, times(1)).incrementCacheHitCounter()
      }
    }

    "return the json from the supplied function" when {
      "the key is not present in the cache" in {
        val sut = cacheTest
        val jsonFromFunction = Json.obj("c" -> "d")
        when(cacheConnector.findJson(Matchers.eq(cacheId), Matchers.eq(mongoKey))(Matchers.eq(hc)))
          .thenReturn(Future.successful(None))
        when(
          cacheConnector
            .createOrUpdateJson(Matchers.eq(cacheId), Matchers.eq(jsonFromFunction), Matchers.eq(mongoKey))(
              Matchers.eq(hc)))
          .thenReturn(Future.successful(jsonFromFunction))
        val result = Await.result(sut.cacheFromApi(nino, mongoKey, Future.successful(jsonFromFunction)), 5 seconds)
        result mustBe jsonFromFunction

        verify(metrics, times(1)).incrementCacheMissCounter()
      }
    }
  }

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  val nino = new Generator(Random).nextNino
  val cacheId = CacheId(nino)
  val mongoKey = "mongoKey1"

  def cacheTest = new CachingTest
  val cacheConnector = mock[CacheConnector]
  val metrics = mock[Metrics]
  val cacheMetricsConfig = mock[CacheMetricsConfig]

  when(cacheMetricsConfig.cacheMetricsEnabled).thenReturn(true)

  class CachingTest extends Caching(cacheConnector, metrics, cacheMetricsConfig)
}
