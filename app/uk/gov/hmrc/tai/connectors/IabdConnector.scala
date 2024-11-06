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
import play.api.http.MimeTypes
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.{DesConfig, HipConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.controllers.auth.AuthenticatedRequest
import uk.gov.hmrc.tai.model.admin.{HipToggleEmploymentIabds, HipToggleIabds}
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.{APITypes, HipIabdUpdateEmployeeExpensesAPI}
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot.format
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, UpdateHipIabdEmployeeExpense, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.service.SensitiveFormatService
import uk.gov.hmrc.tai.service.SensitiveFormatService.SensitiveJsValue
import uk.gov.hmrc.tai.util.HodsSource.NpsSource
import uk.gov.hmrc.tai.util.{InvalidateCaches, TaiConstants}

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CachingIabdConnector @Inject() (
  @Named("default") underlying: IabdConnector,
  cachingConnector: CachingConnector,
  invalidateCaches: InvalidateCaches,
  sensitiveFormatService: SensitiveFormatService
)(implicit ec: ExecutionContext)
    extends IabdConnector {

  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    cachingConnector
      .cache(s"iabds-$nino-${taxYear.year}") {
        underlying
          .iabds(nino: Nino, taxYear: TaxYear)
          .map(SensitiveJsValue)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)

  override def updateTaxCodeAmount(
    nino: Nino,
    taxYear: TaxYear,
    employmentId: Int,
    version: Int,
    iabdType: Int,
    amount: Int
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HodUpdateResponse] =
    invalidateCaches.invalidateAll {
      underlying.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount)
    }

  override def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(implicit
    hc: HeaderCarrier
  ): Future[List[NpsIabdRoot]] =
    cachingConnector.cache(s"iabds-$nino-$year-$iabdType") {
      underlying.getIabdsForType(nino, year, iabdType)
    }(sensitiveFormatService.sensitiveFormatFromReadsWritesJsArray[List[NpsIabdRoot]], implicitly)

  override def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse] =
    invalidateCaches.invalidateAll {
      underlying.updateExpensesData(nino, year, iabdType, version, expensesData, apiType)
    }
}

class DefaultIabdConnector @Inject() (
  httpHandler: HttpHandler,
  npsConfig: NpsConfig,
  desConfig: DesConfig,
  hipConfig: HipConfig,
  iabdUrls: IabdUrls,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends IabdConnector {

  private def getUuid = UUID.randomUUID().toString

  private def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None            => UUID.randomUUID().toString.replace("-", "")
    }

  private def hipAuthHeaders(clientIdAndSecret: Option[(String, String)]): Seq[(String, String)] =
    clientIdAndSecret.fold[Seq[(String, String)]](Seq.empty) { case (clientId, clientSecret) =>
      val token = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(StandardCharsets.UTF_8))
      Seq(
        HeaderNames.authorisation -> s"Basic $token"
      )
    }

  private def headersForIabds(
    originatorId: String,
    hc: HeaderCarrier,
    clientIdAndSecret: Option[(String, String)]
  ): Seq[(String, String)] =
    Seq(
      play.api.http.HeaderNames.CONTENT_TYPE -> MimeTypes.JSON,
      "Gov-Uk-Originator-Id"                 -> originatorId,
      HeaderNames.xSessionId                 -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId                 -> hc.requestId.fold("-")(_.value),
      "CorrelationId"                        -> UUID.randomUUID().toString
    ) ++ hipAuthHeaders(clientIdAndSecret)

  private def headersForGetIabdsForType(implicit hc: HeaderCarrier) =
    Seq(
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString
    )

  override def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    if (taxYear > TaxYear()) {
      Future.successful(JsArray(Seq.empty))
    } else {
      featureFlagService.get(HipToggleIabds).flatMap { toggle =>
        val (url, originatorId, extraInfo) =
          if (toggle.isEnabled) {
            (
              s"${hipConfig.baseURL}/iabd/taxpayer/$nino/tax-year/${taxYear.year}",
              hipConfig.originatorId,
              Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret))
            )
          } else {
            (s"${npsConfig.baseURL}/person/${nino.nino}/iabds/${taxYear.year}", npsConfig.originatorId, None)
          }

        httpHandler.getFromApi(url, APITypes.NpsIabdAllAPI, headersForIabds(originatorId, hc, extraInfo)).recover {
          case _: NotFoundException =>
            Json.toJson(Json.obj("error" -> "NOT_FOUND"))
        }
      }
    }

  private def updateTaxCodeAmountHip(
    nino: Nino,
    taxYear: TaxYear,
    empId: Int,
    version: Int,
    iabdType: Int,
    amount: Int
  )(implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {
    val requestHeader: Seq[(String, String)] =
      Seq(
        HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
        HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value)
      ) ++ hipAuthHeaders(Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret))) ++ Seq(
        "gov-uk-originator-id" -> hipConfig.originatorId,
        "correlationId"        -> getUuid
      )
    val iabdTypeAsString = IabdType.hipMapping(iabdType)
    httpHandler
      .putToApi[IabdUpdateAmount](
        url =
          s"${hipConfig.baseURL}/iabd/taxpayer/$nino/tax-year/${taxYear.year}/employment/$empId/type/$iabdTypeAsString",
        data = IabdUpdateAmount(grossAmount = amount, source = Some(NpsSource), currentOptimisticLock = Some(version)),
        api = APITypes.NpsIabdUpdateEstPayManualAPI,
        headers = requestHeader
      )(
        implicitly,
        IabdUpdateAmount.writesHip
      )
      .map(_ => HodUpdateSuccess)
      .recover { case _ => HodUpdateFailure }
  }

  private def updateTaxCodeAmountSquid(
    nino: Nino,
    taxYear: TaxYear,
    empId: Int,
    version: Int,
    iabdType: Int,
    amount: Int
  )(implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {
    val requestHeader: Seq[(String, String)] =
      Seq(
        HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
        HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value)
      ) ++ Seq(
        "ETag"                 -> version.toString,
        "X-TXID"               -> sessionOrUUID,
        "Gov-Uk-Originator-Id" -> npsConfig.originatorId,
        "CorrelationId"        -> getUuid
      )

    httpHandler
      .postToApi[IabdUpdateAmount](
        url = iabdUrls.npsIabdEmploymentUrl(nino, taxYear, iabdType),
        data = IabdUpdateAmount(employmentSequenceNumber = Some(empId), grossAmount = amount, source = Some(NpsSource)),
        api = APITypes.NpsIabdUpdateEstPayManualAPI,
        headers = requestHeader
      )(
        implicitly,
        (updateAmount: IabdUpdateAmount) => Json.arr(Json.toJson(updateAmount))
      )
      .map(_ => HodUpdateSuccess)
      .recover { case _ => HodUpdateFailure }
  }

  override def updateTaxCodeAmount(
    nino: Nino,
    taxYear: TaxYear,
    empId: Int,
    version: Int,
    iabdType: Int,
    amount: Int
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HodUpdateResponse] =
    featureFlagService.get(HipToggleEmploymentIabds).flatMap { toggle =>
      if (toggle.isEnabled) {
        updateTaxCodeAmountHip(nino, taxYear, empId, version, iabdType, amount)
      } else {
        updateTaxCodeAmountSquid(nino, taxYear, empId, version, iabdType, amount)
      }

    }

  override def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(implicit
    hc: HeaderCarrier
  ): Future[List[NpsIabdRoot]] = {
    val urlToRead = s"${desConfig.baseURL}/pay-as-you-earn/individuals/$nino/iabds/tax-year/$year?type=$iabdType"
    httpHandler
      .getFromApi(url = urlToRead, api = APITypes.DesIabdSpecificAPI, headers = headersForGetIabdsForType)
      .map(_.as[List[NpsIabdRoot]])
  }

  private def headersForUpdateExpensesData(version: Int, originatorId: String)(implicit
    hc: HeaderCarrier
  ): Seq[(String, String)] =
    Seq(
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString,
      "Originator-Id"        -> originatorId,
      "ETag"                 -> version.toString
    )

  private def headersForHipUpdateExpensesData(version: Int)(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Environment"          -> hipConfig.environment,
      "Authorization"        -> hipConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> UUID.randomUUID().toString,
      "Originator-Id"        -> hipConfig.originatorId,
      "ETag"                 -> version.toString
    )

  override def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse] =
    featureFlagService.get(HipToggleIabds).flatMap { toggle =>
      if (toggle.isEnabled) {
        httpHandler.putToApi[UpdateHipIabdEmployeeExpense](
          s"${hipConfig.baseURL}/iabd/taxpayer/$nino/tax-year/$year/type/${IabdType.hipMapping(iabdType)}",
          UpdateHipIabdEmployeeExpense(version, expensesData.grossAmount),
          HipIabdUpdateEmployeeExpensesAPI,
          headersForHipUpdateExpensesData(version)
        )
      } else {
        httpHandler.postToApi[List[UpdateIabdEmployeeExpense]](
          s"${desConfig.baseURL}/pay-as-you-earn/individuals/$nino/iabds/$year/$iabdType",
          List(expensesData),
          apiType,
          headersForUpdateExpensesData(version, desConfig.daPtaOriginatorId)
        )
      }
    }
}

trait IabdConnector {

  def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue]

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ): Future[HodUpdateResponse]

  def getIabdsForType(nino: Nino, year: Int, iabdType: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]]

  def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier, request: AuthenticatedRequest[_]): Future[HttpResponse]

}
