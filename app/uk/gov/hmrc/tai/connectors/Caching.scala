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

import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

trait Caching {
  def cacheConnector: CacheConnector

  def cache(mongoKey: String, jsonFromApi: => Future[JsValue])(implicit hc: HeaderCarrier): Future[JsValue] = {
    val sessionId = fetchSessionId(hc)
    cacheConnector.findJson(sessionId, mongoKey).flatMap {
      case Some(jsonFromCache) => Future.successful(jsonFromCache)
      case _ => jsonFromApi.flatMap(cacheConnector.createOrUpdateJson(sessionId, _, mongoKey))
    }
  }

  def fetchSessionId(headerCarrier: HeaderCarrier): String = {
    headerCarrier.sessionId.map(_.value).getOrElse(throw new RuntimeException("Error while fetching session id"))
  }

}
