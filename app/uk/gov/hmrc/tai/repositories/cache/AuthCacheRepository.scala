/*
 * Copyright 2026 HM Revenue & Customs
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

import play.api.libs.json.{Reads, Writes}
import uk.gov.hmrc.tai.config.{CryptoProvider, MongoConfig}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

@Singleton
class AuthCacheRepository @Inject() (
  mongoConfig: MongoConfig,
  cryptoProvider: CryptoProvider,
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends GenericCacheRepository[HeaderCarrier](
      mongoComponent = mongoComponent,
      crypto = cryptoProvider.get,
      collectionName = "taiAuthCache",
      ttl = Duration(mongoConfig.mongoAuthTTL, TimeUnit.SECONDS),
      timestampSupport = new CurrentTimestampSupport(),
      cacheIdType = RequestCacheId
    ) {

  def putSession[T: Writes](dataKey: DataKey[T], data: T)(implicit
    hc: HeaderCarrier
  ): scala.concurrent.Future[(String, String)] =
    put(hc, dataKey, data)

  def getFromSession[T: Reads](dataKey: DataKey[T])(implicit hc: HeaderCarrier): scala.concurrent.Future[Option[T]] =
    get(hc, dataKey)

  def deleteFromSession[T](dataKey: DataKey[T])(implicit hc: HeaderCarrier): scala.concurrent.Future[Unit] =
    delete(hc, dataKey)

  def deleteAllFromSession(implicit hc: HeaderCarrier): scala.concurrent.Future[Unit] =
    deleteAll(hc)
}
