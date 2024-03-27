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

import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, _}
import uk.gov.hmrc.tai.config.{DesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.APITypes
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, IabdUpdateAmountFormats, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.util.HodsSource.NpsSource
import uk.gov.hmrc.tai.util.{InvalidateCaches, TaiConstants}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CachingIabdConnector @Inject()(@Named("default") underlying: IabdConnector,
                                     cachingConnector: CachingConnector,
                                     invalidateCaches: InvalidateCaches)
  extends IabdConnector {



  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[IabdDetails]] = {
    cachingConnector.cache(s"iabds-$nino-${taxYear.year}") {
      underlying.iabds(nino: Nino, taxYear: TaxYear)
    }
  }

  override def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HodUpdateResponse] = {
    invalidateCaches.invalidateAll {
      underlying.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount)
    }
  }

  override def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(
    implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {

    cachingConnector.cache(s"iabds-$nino-$year-$iabdType") {
      underlying.getIabdsForType(nino, year, iabdType)
    }
  }

  override def updateExpensesData(
                                   nino: Nino,
                                   year: Int,
                                   iabdType: Int,
                                   version: Int,
                                   expensesData: List[UpdateIabdEmployeeExpense],
                                   apiType: APITypes)(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse] = {
    invalidateCaches.invalidateAll {
      underlying.updateExpensesData(nino, year, iabdType, version, expensesData, apiType)
    }
  }
}

class DefaultIabdConnector @Inject()(httpHandler: HttpHandler,
                                     npsConfig: NpsConfig,
                                     desConfig: DesConfig,
                                     iabdUrls: IabdUrls,
                                     IabdUpdateAmountFormats: IabdUpdateAmountFormats)(implicit ec: ExecutionContext)
  extends IabdConnector {

  private def getUuid = UUID.randomUUID().toString

  private def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None => UUID.randomUUID().toString.replace("-", "")
    }

  private def headersForIabds(implicit hc: HeaderCarrier) = Seq(
    HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
    HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
    "Gov-Uk-Originator-Id" -> npsConfig.originatorId,
    "CorrelationId" -> UUID.randomUUID().toString
  )

  private def headerForUpdateTaxCodeAmount(hc: HeaderCarrier, version: Int, txId: String, originatorId: String): Seq[(String, String)] =
    Seq(
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "ETag" -> version.toString,
      "X-TXID" -> txId,
      "Gov-Uk-Originator-Id" -> originatorId,
      "CorrelationId" -> getUuid
    )

  private def headersForGetIabdsForType(implicit hc: HeaderCarrier) =
    Seq(
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[IabdDetails]] =
    if (taxYear > TaxYear()) {
      Future.successful(Seq.empty)
    } else {
      val urlNps = iabdUrls.npsIabdUrl(nino, taxYear)
      httpHandler.getFromApi(urlNps, APITypes.NpsIabdAllAPI, headersForIabds).map(_.as[Seq[IabdDetails]])
    }

  override def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HodUpdateResponse] = {
    val url = iabdUrls.npsIabdEmploymentUrl(nino, taxYear, iabdType)
    val iabdUpdateAmount = IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(NpsSource))
    val requestHeader = headerForUpdateTaxCodeAmount(hc, version, sessionOrUUID, npsConfig.originatorId)

    httpHandler.postToApi[IabdUpdateAmount](url, iabdUpdateAmount, APITypes.NpsIabdUpdateEstPayManualAPI, requestHeader)(
      implicitly, IabdUpdateAmountFormats.formatList
    ).map { _ => HodUpdateSuccess }.recover { case _ => HodUpdateFailure }
  }

  override def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(
    implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {

    val urlToRead = s"${desConfig.baseURL}/pay-as-you-earn/individuals/$nino/iabds/tax-year/$year?type=$iabdType"
    httpHandler.getFromApi(url = urlToRead, api = APITypes.DesIabdSpecificAPI, headers = headersForGetIabdsForType).map(_.as[List[NpsIabdRoot]])
  }

  private def headersForUpdateExpensesData(version: Int, originatorId: String)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString,
      "Originator-Id" -> originatorId,
      "ETag"          -> version.toString
    )

  override def updateExpensesData(
                               nino: Nino,
                               year: Int,
                               iabdType: Int,
                               version: Int,
                               expensesData: List[UpdateIabdEmployeeExpense],
                               apiType: APITypes)(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse] = {

    val postUrl = s"${desConfig.baseURL}/pay-as-you-earn/individuals/$nino/iabds/$year/$iabdType"

    httpHandler.postToApi[List[UpdateIabdEmployeeExpense]](postUrl, expensesData, apiType, headersForUpdateExpensesData(version, desConfig.daPtaOriginatorId))
  }
}

trait IabdConnector {

  def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[IabdDetails]]

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HodUpdateResponse]

  def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(
    implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]]

  def updateExpensesData(
                                        nino: Nino,
                                        year: Int,
                                        iabdType: Int,
                                        version: Int,
                                        expensesData: List[UpdateIabdEmployeeExpense],
                                        apiType: APITypes)(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse]
}
