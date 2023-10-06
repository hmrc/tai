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

package uk.gov.hmrc.tai.util

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.cache.{CacheId, TaiCacheConnector, TaiUpdateIncomeCacheConnector}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class InvalidateCaches @Inject()(taiCacheConnector: TaiCacheConnector,
                                 taiSessionCacheRepository: TaiSessionCacheRepository,
                                 taiUpdateIncomeCacheConnector: TaiUpdateIncomeCacheConnector)(
  implicit ec: ExecutionContext
) {

  def invalidateAll[A](f: => Future[A])(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[A] = {
    val cacheId = CacheId(request.nino).value
    for {
      _ <- taiCacheConnector.deleteEntity(cacheId)
      _ <- taiUpdateIncomeCacheConnector.deleteEntity(cacheId)
      _ <- taiSessionCacheRepository.deleteAllFromSession
      result <- f
    } yield result
  }

}
