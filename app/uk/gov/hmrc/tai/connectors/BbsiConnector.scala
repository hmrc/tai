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

import java.util.UUID.randomUUID

import com.google.inject.{Inject, Singleton}
import com.kenshoo.play.metrics.Metrics
import com.typesafe.scalalogging.LazyLogging
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.metrics.HasMetrics
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BbsiConnector @Inject()(val metrics: Metrics, http: HttpClient, urls: BbsiUrls, config: DesConfig)(
  implicit ec: ExecutionContext)
    extends LazyLogging with HasMetrics {

  private def extraHeaders(implicit hc: HeaderCarrier): HeaderCarrier =
    hc.withExtraHeaders(
      "Environment"   -> config.environment,
      "Authorization" -> s"Bearer ${config.authorization}",
      "X-Session-ID"  -> hc.sessionId.getOrElse("").toString,
      "X-Request-ID"  -> hc.requestId.getOrElse("").toString,
      "Content-Type"  -> TaiConstants.contentType,
      "CorrelationId" -> randomUUID().toString
    )

  def bankAccounts(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    withMetricsTimerAsync("bbsi") { _ =>
      http.GET[HttpResponse](urls.bbsiUrl(nino, taxYear))(implicitly, extraHeaders, implicitly)
    }
}
