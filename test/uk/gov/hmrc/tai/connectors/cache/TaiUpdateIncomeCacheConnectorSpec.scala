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

import play.api.Configuration
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.{CacheItem, MongoCacheRepository}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.util.BaseSpec

class TaiUpdateIncomeCacheConnectorSpec extends BaseSpec with DefaultPlayMongoRepositorySupport[CacheItem] {

  implicit lazy val configuration: Configuration = inject[Configuration]
  protected override val repository = new TaiUpdateIncomeCacheConnector(
    mongoComponent,
    inject[MongoConfig],
    inject[CurrentTimestampSupport]
  )

  "TaiCacheConnectorUpdateIncome" must {
    "have the correct collection name" in {
      repository.collectionName mustBe "TaiUpdateIncome"
    }

    "use mongoFormats from Cache" in {
      repository.domainFormat mustBe MongoCacheRepository.format
    }
    "have cache default ttl of 2 days" in {
      val mongoConfig = new MongoConfig(configuration)
      mongoConfig.mongoTTLUpdateIncome mustBe 172800
    }
  }
}
