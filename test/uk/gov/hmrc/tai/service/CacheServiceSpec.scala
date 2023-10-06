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

package uk.gov.hmrc.tai.service

import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.repositories.deprecated.TaiCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class CacheServiceSpec extends BaseSpec {

  "invalidateTaiData" must {
    "remove the session data" when {
      "cache is enabled" in {
        val mockCacheConnector = mock[TaiCacheRepository]
        when(mockCacheConnector.removeById(any()))
          .thenReturn(Future.successful(true))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(true)
        when(mockMongoConfig.mongoEncryptionEnabled)
          .thenReturn(false)

        val sut = new CacheService(mockMongoConfig, mockCacheConnector)

        sut.invalidateTaiCacheData(nino)(hc)

        verify(mockCacheConnector, times(1))
          .removeById(any())
      }
    }

    "not call remove data operation" when {
      "cache is disabled" in {
        val mockCacheConnector = mock[TaiCacheRepository]
        when(mockCacheConnector.removeById(any()))
          .thenReturn(Future.successful(true))

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEnabled)
          .thenReturn(false)
        when(mockMongoConfig.mongoEncryptionEnabled)
          .thenReturn(false)

        val sut = new CacheService(mockMongoConfig, mockCacheConnector)

        sut.invalidateTaiCacheData(nino)(hc)

        verify(mockCacheConnector, never)
          .createOrUpdate(any(), any(), any())(any())
      }
    }
  }
}
