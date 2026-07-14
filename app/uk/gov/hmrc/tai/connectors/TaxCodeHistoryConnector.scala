/*
 * Copyright 2024 HM Revenue & Customs
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

import com.google.inject.Inject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.tai.config.IfConfig
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.TaxCodeHistory.{reads, writes}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.SensitiveFormatService

import java.util.UUID
import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

trait TaxCodeHistoryConnector {
  def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory]
}

class CachingTaxCodeHistoryConnector @Inject() (
  @Named("default") underlying: TaxCodeHistoryConnector,
  cachingConnector: CachingConnector,
  sensitiveFormatService: SensitiveFormatService
) extends TaxCodeHistoryConnector {

  override def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] = {
    val cacheKey = s"tax-code-history-$nino-${year.year}"
    cachingConnector.cache(cacheKey) {
      underlying.taxCodeHistory(nino, year)
    }(sensitiveFormatService.sensitiveFormatFromReadsWrites[TaxCodeHistory], implicitly)
  }

}

class DefaultTaxCodeHistoryConnector @Inject() (
  httpHandler: HttpHandler,
  ifConfig: IfConfig,
  ifUrls: TaxCodeChangeFromIfUrl
)(implicit ec: ExecutionContext)
    extends TaxCodeHistoryConnector {

  override def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] = {
    val url: String = ifUrls.taxCodeChangeUrl(nino, year)

    httpHandler
      .getFromApi(
        url = url,
        api = APITypes.TaxCodeChangeAPI,
        headers = createHeader(),
        timeoutInMilliseconds = Some(ifConfig.timeoutInMilliseconds)
      )
      .map(_.as[TaxCodeHistory])
  }

  private def createHeader()(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Environment"          -> ifConfig.environment,
      "Authorization"        -> ifConfig.authorization,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString,
      "originator-id"        -> ifConfig.originatorId
    )
}
