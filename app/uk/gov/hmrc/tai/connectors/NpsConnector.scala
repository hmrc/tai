/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import com.google.inject.{Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.{GateKeeperRule, IabdUpdateAmount, IabdUpdateAmountFormats}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NpsConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  auditor: Auditor,
  formats: IabdUpdateAmountFormats,
  config: NpsConfig)(implicit ec: ExecutionContext)
    extends BaseConnector(auditor, metrics, httpClient) with NpsFormatter {

  override val originatorId = config.originatorId

  def npsPathUrl(nino: Nino, path: String) = s"${config.baseURL}/person/$nino/$path"

  def getEmployments(nino: Nino, year: Int)(implicit hc: HeaderCarrier)
    : Future[(List[NpsEmployment], List[model.nps2.NpsEmployment], Int, List[GateKeeperRule])] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    val json = getFromNps[JsValue](urlToRead, APITypes.NpsEmploymentAPI)
    json.map { x =>
      (x._1.as[List[NpsEmployment]], x._1.as[List[model.nps2.NpsEmployment]], x._2, Nil)
    }
  }

  def getEmploymentDetails(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val urlToRead = npsPathUrl(nino, s"employment/$year")
    getFromNps[JsValue](urlToRead, APITypes.NpsEmploymentAPI).map(_._1)
  }

  def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = npsPathUrl(nino, s"iabds/$year/$iabdType")
    getFromNps[List[NpsIabdRoot]](urlToRead, APITypes.NpsIabdSpecificAPI).map(x => x._1)
  }

  def getIabds(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = npsPathUrl(nino, s"iabds/$year")
    getFromNps[List[NpsIabdRoot]](urlToRead, APITypes.NpsIabdAllAPI).map(x => x._1)
  }

  def getCalculatedTaxAccount(nino: Nino, year: Int)(
    implicit hc: HeaderCarrier): Future[(NpsTaxAccount, Int, JsValue)] = {
    val urlToRead = npsPathUrl(nino, s"tax-account/$year/calculation")
    getFromNps[JsValue](urlToRead, APITypes.NpsTaxAccountAPI).map(x => (x._1.as[NpsTaxAccount], x._2, x._1))
  }

  def getCalculatedTaxAccountRawResponse(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val urlToRead = npsPathUrl(nino, s"tax-account/$year/calculation")
    implicit val hc = basicNpsHeaders(HeaderCarrier())
    httpClient.GET[HttpResponse](urlToRead)
  }

  def updateEmploymentData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    updateAmounts: List[IabdUpdateAmount],
    apiType: APITypes = APITypes.NpsIabdUpdateEstPayAutoAPI)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    if (updateAmounts.nonEmpty) {
      val postUrl = npsPathUrl(nino, s"iabds/$year/employment/$iabdType")
      postToNps[List[IabdUpdateAmount]](postUrl, apiType, updateAmounts)(
        extraNpsHeaders(hc, version, sessionOrUUID),
        formats.formatList)
    } else {
      Future(HttpResponse(OK))
    }

  private def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None            => UUID.randomUUID().toString.replace("-", "")
    }
}
