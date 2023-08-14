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
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpResponse}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.{GateKeeperRule, IabdUpdateAmount, IabdUpdateAmountFormats}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NpsConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  formats: IabdUpdateAmountFormats,
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

  def getEmployments(nino: Nino, year: Int)(implicit hc: HeaderCarrier)
    : Future[(List[NpsEmployment], List[model.nps2.NpsEmployment], Int, List[GateKeeperRule])] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    val json = getFromNps[JsValue](urlToRead, APITypes.NpsEmploymentAPI, basicNpsHeaders(hc))
    json.map { x =>
      (x._1.as[List[NpsEmployment]], x._1.as[List[model.nps2.NpsEmployment]], x._2, Nil)
    }
  }

  def getEmploymentDetails(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    getFromNps[JsValue](urlToRead, APITypes.NpsEmploymentAPI, basicNpsHeaders(hc)).map(_._1)
  }

  def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = npsPathUrl(nino, s"iabds/$year/$iabdType")
    getFromNps[List[NpsIabdRoot]](urlToRead, APITypes.NpsIabdSpecificAPI, basicNpsHeaders(hc)).map(x => x._1)
  }

  def getIabds(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = npsPathUrl(nino, s"iabds/$year")
    getFromNps[List[NpsIabdRoot]](urlToRead, APITypes.NpsIabdAllAPI, basicNpsHeaders(hc)).map(x => x._1)
  }

  private def extraNpsHeaders(hc: HeaderCarrier, version: Int, txId: String) =
    Seq(
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "ETag"                 -> version.toString,
      "X-TXID"               -> txId,
      "Gov-Uk-Originator-Id" -> originatorId,
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  def updateEmploymentData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    updateAmounts: List[IabdUpdateAmount],
    apiType: APITypes = APITypes.NpsIabdUpdateEstPayAutoAPI)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    if (updateAmounts.nonEmpty) {
      val postUrl = npsPathUrl(nino, s"iabds/$year/employment/$iabdType")
      postToNps[List[IabdUpdateAmount]](postUrl, apiType, updateAmounts, extraNpsHeaders(hc, version, sessionOrUUID))(
        implicitly,
        formats.formatList)
    } else {
      Future(HttpResponse(OK, ""))
    }

  private def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None            => UUID.randomUUID().toString.replace("-", "")
    }
}
