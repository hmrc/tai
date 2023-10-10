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

package uk.gov.hmrc.tai.connectors.deprecated

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpClient}
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.connectors.BaseConnector
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps2.NpsFormatter

import java.util.UUID
import scala.annotation.nowarn
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NpsConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  config: NpsConfig)(implicit ec: ExecutionContext)
    extends BaseConnector(metrics, httpClient) with NpsFormatter {

  override val originatorId: String = config.originatorId

  def npsPathUrl(nino: Nino, path: String) = s"${config.baseURL}/person/$nino/$path"

  def basicNpsHeaders(hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Gov-Uk-Originator-Id" -> originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  @nowarn("msg=method getFromNps in class BaseConnector is deprecated: this method will be removed. Use uk.gov.hmrc.tai.connectors.HttpHandler.getFromApi instead")
  def getEmploymentDetails(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    getFromNps[JsValue](urlToRead, APITypes.NpsEmploymentAPI, basicNpsHeaders(hc)).map(_._1)
  }

}
