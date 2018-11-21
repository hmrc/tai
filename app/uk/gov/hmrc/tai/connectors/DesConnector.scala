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

import java.util.UUID

import com.google.inject.{Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.response.{ExpensesUpdateFailure, ExpensesUpdateResponse, ExpensesUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats, IabdUpdateExpensesAmount, IabdUpdateExpensesAmountFormats}
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.Future

@Singleton
class DesConnector @Inject()(httpClient: HttpClient,
                             metrics: Metrics,
                             auditor: Auditor,
                             formats: IabdUpdateAmountFormats,
                             config: DesConfig) extends BaseConnector(auditor, metrics, httpClient) with NpsFormatter  {

  override def originatorId = config.originatorId

  def desPathUrl(nino: Nino, path: String) = s"${config.baseURL}/pay-as-you-earn/individuals/$nino/$path"

  def commonHeaderValues = Seq(
    "Environment" -> config.environment,
    "Authorization" -> config.authorization,
    "Content-Type" -> TaiConstants.contentType)

  def header: HeaderCarrier = HeaderCarrier(extraHeaders = commonHeaderValues)

  def headerValuesForUpdate(version: Int) = Seq("Originator-Id" -> originatorId, "ETag" -> version.toString)

  def headerForUpdate(version: Int): HeaderCarrier = HeaderCarrier(extraHeaders = commonHeaderValues ++ headerValuesForUpdate(version))

  def getIabdsForTypeFromDes(nino: Nino, year: Int, iabdType: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = desPathUrl(nino, s"iabds/tax-year/$year?type=$iabdType")
    implicit val hc: HeaderCarrier = header
    getFromDes[List[NpsIabdRoot]](urlToRead, APITypes.DesIabdSpecificAPI).map(x => x._1)
  }

  def getIabdsFromDes(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    val urlToRead = desPathUrl(nino, s"iabds/tax-year/$year")
    implicit val hc: HeaderCarrier = header
    getFromDes[List[NpsIabdRoot]](urlToRead, APITypes.DesIabdAllAPI).map(x => x._1)
  }

  def getCalculatedTaxAccountFromDes(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[(NpsTaxAccount, Int, JsValue)] = {
    val urlToRead = desPathUrl(nino, s"tax-account/tax-year/$year?calculation=true")
    implicit val hc: HeaderCarrier = header
    getFromDes[JsValue](urlToRead, APITypes.DesTaxAccountAPI).map(x => (x._1.as[NpsTaxAccount], x._2, x._1))
  }

  def getCalculatedTaxAccountRawResponseFromDes(nino: Nino, year: Int)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val urlToRead = desPathUrl(nino, s"tax-account/tax-year/$year?calculation=true")
    implicit val hc: HeaderCarrier = header
    httpClient.GET[HttpResponse](urlToRead)
  }

  def updateEmploymentDataToDes(nino: Nino, year: Int, iabdType: Int, version: Int,
                           updateAmounts: List[IabdUpdateAmount],
                           apiType: APITypes = APITypes.DesIabdUpdateEstPayAutoAPI)
                          (implicit hc: HeaderCarrier): Future[HttpResponse] = {
    if (updateAmounts.nonEmpty) {
      val postUrl = desPathUrl(nino, s"iabds/$year/employment/$iabdType")
      postToDes[List[IabdUpdateAmount]](postUrl, apiType, updateAmounts)(headerForUpdate(version), formats.formatList)
    } else {
      Future(HttpResponse(OK))
    }
  }

  def updateExpensesDataToDes(nino: Nino, year: Int, iabdType: Int, version: Int, updateAmount: IabdUpdateExpensesAmount,
                                apiType: APITypes = APITypes.DesIabdUpdateFlatRateExpensesAPI)
                               (implicit hc: HeaderCarrier): Future[ExpensesUpdateResponse] = {

    val expensesFormat = new IabdUpdateExpensesAmountFormats

    val postUrl = desPathUrl(nino, s"iabds/$year/$iabdType")
    postToDes[IabdUpdateExpensesAmount](
      url = postUrl,
      api = apiType,
      postData = updateAmount
    )(headerForUpdate(version), expensesFormat.iabdUpdateExpensesAmountWrites).map {
      _ => ExpensesUpdateSuccess
    }.recover{
      case _ => ExpensesUpdateFailure
    }
  }

  def sessionOrUUID(implicit hc: HeaderCarrier): String = {
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None => UUID.randomUUID().toString.replace("-", "")
    }
  }
}
