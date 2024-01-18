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
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.admin.TaxCodeHistoryFromIfToggle
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.util.UUID
import javax.inject.Named
import scala.concurrent.{ExecutionContext, Future}

class CachingTaxCodeHistoryConnector @Inject()(@Named("default")
                                               underlying: TaxCodeHistoryConnector,
                                               cachingConnector: CachingConnector
                                              )
extends TaxCodeHistoryConnector {

  override def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] =
    cachingConnector.cache(s"tax-code-history-$nino-${year.year}") {
      underlying.taxCodeHistory(nino, year)
    }

}

class DefaultTaxCodeHistoryConnector @Inject()(httpHandler: HttpHandler,
                                               config: DesConfig,
                                               desUrls: TaxCodeChangeFromDesUrl,
                                               ifUrls: TaxCodeChangeFromIfUrl,
                                               featureFlagService: FeatureFlagService)
                                              (implicit ec: ExecutionContext)
  extends TaxCodeHistoryConnector {

  implicit private def createHeader(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    Seq(
      "Environment" -> config.environment,
      "Authorization" -> config.authorization,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId" -> UUID.randomUUID().toString,
      "OriginatorId" -> config.originatorId
    )
  }

  override def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] = {
    featureFlagService.get(TaxCodeHistoryFromIfToggle).flatMap{ toggle =>
      val url = if(toggle.isEnabled) ifUrls.taxCodeChangeUrl(nino, year)
                else desUrls.taxCodeChangeFromDesUrl(nino, year)
       httpHandler.getFromApi(url = url, api = APITypes.TaxCodeChangeAPI, headers = createHeader).map(json =>
         json.as[TaxCodeHistory]) recover { case e => throw e }
      }
    }
}

trait TaxCodeHistoryConnector {
  def taxCodeHistory(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory]
}
