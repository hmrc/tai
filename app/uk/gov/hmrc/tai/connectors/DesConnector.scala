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

package uk.gov.hmrc.tai.connectors

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.UpdateIabdEmployeeExpense
import uk.gov.hmrc.tai.util.TaiConstants

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DesConnector @Inject()(
  httpClient: HttpClient,
  metrics: Metrics,
  config: DesConfig)(
  implicit ec: ExecutionContext
) extends BaseConnector(metrics, httpClient) with NpsFormatter {

  override def originatorId: String = config.originatorId

  def daPtaOriginatorId: String = config.daPtaOriginatorId

  private def desPathUrl(nino: Nino, path: String) = s"${config.baseURL}/pay-as-you-earn/individuals/$nino/$path"

  private def createDesHeader(implicit hc: HeaderCarrier) =
    Seq(
      "Environment"          -> config.environment,
      "Authorization"        -> config.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  private def headerForUpdate(version: Int, originatorId: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    createDesHeader ++ Seq(
      "Originator-Id" -> originatorId,
      "ETag"          -> version.toString
    )

  def getIabdsForTypeFromDes(nino: Nino, year: Int, iabdType: Int)(
    implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = desPathUrl(nino, s"iabds/tax-year/$year?type=$iabdType")
    getFromDes[List[NpsIabdRoot]](url = urlToRead, api = APITypes.DesIabdSpecificAPI, headers = createDesHeader).map(x => x._1)
  }

  def updateExpensesDataToDes(
                               nino: Nino,
                               year: Int,
                               iabdType: Int,
                               version: Int,
                               expensesData: List[UpdateIabdEmployeeExpense],
                               apiType: APITypes)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val postUrl = desPathUrl(nino, s"iabds/$year/$iabdType")

    postToDes[List[UpdateIabdEmployeeExpense]](postUrl, apiType, expensesData, headerForUpdate(version, daPtaOriginatorId))
  }

}
