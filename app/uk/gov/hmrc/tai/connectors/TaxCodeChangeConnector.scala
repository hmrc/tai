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

import com.google.inject.Inject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.{ExecutionContext, Future}

class TaxCodeChangeConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  auditor: Auditor,
  config: DesConfig,
  taxCodeChangeUrl: TaxCodeChangeUrl)(
  implicit ec: ExecutionContext
) extends BaseConnector(auditor, metrics, httpClient) {

  override val originatorId = config.originatorId

  implicit private def createHeader(implicit hc: HeaderCarrier): Seq[(String, String)] = {
    Seq(
      "Environment"          -> config.environment,
      "Authorization"        -> config.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value)
    )
  }

  def taxCodeHistory(nino: Nino, from: TaxYear, to: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] = {
    val url = taxCodeChangeUrl.taxCodeChangeUrl(nino, from, to)

    getFromDes[TaxCodeHistory](url = url, api = APITypes.TaxCodeChangeAPI, headers = createHeader).map(_._1)
  }
}
