/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Format
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.tai.config.{CacheConfig, MongoConfig}
import uk.gov.hmrc.tai.model.UpstreamErrorResponseFormat
import uk.gov.hmrc.tai.util.BaseSpec

class TaiSessionCacheRepositorySpec extends BaseSpec { // scalastyle:off magic.number

  val mongoHost = "localhost"
  var mongoPort: Int = 27017

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(Span(30, Seconds), Span(1, Millis))

  private val mockMongoConfig = mock[MongoConfig]
  private val mockCacheConfig = mock[CacheConfig]

  private def buildFormRepository(mongoHost: String, mongoPort: Int) = {
    val databaseName = "sessions"
    val mongoUri = s"mongodb://$mongoHost:$mongoPort/$databaseName?heartbeatFrequencyMS=1000&rm.failover=default"
    new TaiSessionCacheRepository(mockMongoConfig, MongoComponent(mongoUri), mockCacheConfig)
  }

  var taiSessionCacheRepository: TaiSessionCacheRepository = _

  override def beforeEach(): Unit = {
    when(mockMongoConfig.mongoTTL).thenReturn(60)
    when(mockCacheConfig.cacheErrorInSecondsTTL).thenReturn(10L)

    taiSessionCacheRepository = buildFormRepository(mongoHost, mongoPort)
  }

  private val key: DataKey[Either[UpstreamErrorResponse, String]] = DataKey("key")
  private val storedValueRight = Right("test")
  private val upstreamErrorResponse = UpstreamErrorResponse("error", 500, 500)
  private val storedValueLeft = Left(upstreamErrorResponse)
  private implicit val fmt: Format[Either[UpstreamErrorResponse, String]] = UpstreamErrorResponseFormat.format

  "getEitherFromSession" must {
    "return a right" in {
      val result = for {
        _ <- taiSessionCacheRepository.putSession(key, storedValueRight)
        result: Option[Either[UpstreamErrorResponse, String]] <- taiSessionCacheRepository.getEitherFromSession(key)
      } yield result
      whenReady(result) { r =>
        r mustBe Some(Right("test"))
      }
    }
    "return a left which has not expired" in {
      val result = for {
        _ <- taiSessionCacheRepository.putSession(key, storedValueLeft)
        result: Option[Either[UpstreamErrorResponse, String]] <- taiSessionCacheRepository.getEitherFromSession(key)
      } yield result
      whenReady(result) { r =>
        r mustBe Some(Left(upstreamErrorResponse))
      }
    }
    "return a none when a left has expired" in {
      when(mockCacheConfig.cacheErrorInSecondsTTL).thenReturn(0L)
      val result = for {
        _ <- taiSessionCacheRepository.putSession(key, storedValueLeft)
        result: Option[Either[UpstreamErrorResponse, String]] <- taiSessionCacheRepository.getEitherFromSession(key)
      } yield result
      whenReady(result) { r =>
        r mustBe None
      }
    }
  }
}
