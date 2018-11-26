/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.libs.json.JsObject
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.TaxAccountHodFormatters
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.util.TaiConstants

// TODO: Execution Context
import play.api.libs.concurrent.Execution.Implicits.defaultContext


import scala.concurrent.Future
import scala.util.Try

class TaxAccountHistoryConnector @Inject()(metrics: Metrics,
                                           httpClient: HttpClient,
                                           auditor: Auditor,
                                           config: DesConfig,
                                           urlConfig: TaxAccountHistoryUrl)
  extends BaseConnector(auditor, metrics, httpClient) with TaxAccountHodFormatters {

  override val originatorId: String = config.originatorId

  implicit private val header: HeaderCarrier = {
    val commonHeaderValues = Seq(
      "Environment" -> config.environment,
      "Authorization" -> config.authorization,
      "Content-Type" -> TaiConstants.contentType)

    HeaderCarrier(extraHeaders = commonHeaderValues)
  }

  def taxAccountHistory(nino: Nino, taxCodeId: Int): Future[Try[Seq[CodingComponent]]] = {
    type Response = (JsObject, Int)

    val url = urlConfig.taxAccountHistoricSnapshotUrl(nino, taxCodeId)
    getFromDes[JsObject](url, APITypes.TaxAccountHistoryAPI).map( (responseJson: Response) => {
      Try(responseJson._1.as[Seq[CodingComponent]](codingComponentReads))
    })
  }
}
