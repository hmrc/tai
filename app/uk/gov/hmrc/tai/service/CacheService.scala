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

import com.google.inject.Inject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.repositories.cache.TaiCacheRepository

class CacheService @Inject()(mongoConfig: MongoConfig, taiCacheRepository: TaiCacheRepository) {
  def invalidateTaiCacheData(nino: Nino)(implicit hc: HeaderCarrier): Unit =
    if (mongoConfig.mongoEnabled) {
      taiCacheRepository.removeById(CacheId(nino))
    } else {
      ()
    }
}
