/*
 * Copyright 2021 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.ETag

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CitizenDetailsConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  auditor: Auditor,
  urls: CitizenDetailsUrls)(implicit ec: ExecutionContext)
    extends BaseConnector(auditor, metrics, httpClient) with Logging {

  override val originatorId: String = ""

  def getEtag(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[ETag]] =
    httpClient.GET(urls.etagUrl(nino)) flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(response.json.asOpt[ETag])
        case errorStatus =>
          logger.error(s"[CitizenDetailsService.getEtag] Failed to get an ETag from citizen-details: $errorStatus")
          Future.successful(None)
      }
    }
}
