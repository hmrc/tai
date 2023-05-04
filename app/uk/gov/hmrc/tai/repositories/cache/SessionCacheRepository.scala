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

package uk.gov.hmrc.tai.repositories.cache

import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongo.cache.{DataKey, SessionCacheRepository => CacheRepository}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.domain.AnnualAccount

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

@Singleton
class SessionCacheRepository @Inject()(appConfig: MongoConfig, mongoComponent: MongoComponent)(implicit
                                                                                               ec: ExecutionContext
) extends CacheRepository(
      mongoComponent = mongoComponent,
      collectionName = "sessions",
      ttl = Duration(appConfig.mongoTTL, TimeUnit.SECONDS),
      timestampSupport = new CurrentTimestampSupport(),
      sessionIdKey = SessionKeys.sessionId
    )
