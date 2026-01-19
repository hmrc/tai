/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.tai.config.{DesConfig, HipConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.admin.HipIabdsUpdateExpensesToggle
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.enums.APITypes.{APITypes, HipIabdUpdateEmployeeExpensesAPI}
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.nps2.IabdType.hipMapping
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateAmount, UpdateHipIabdEmployeeExpense, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.service.SensitiveFormatService
import uk.gov.hmrc.tai.service.SensitiveFormatService.SensitiveJsValue
import uk.gov.hmrc.tai.util.HodsSource.NpsSource
import uk.gov.hmrc.tai.util.TaiConstants

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CachingIabdConnector @Inject() (
  @Named("default") underlying: IabdConnector,
  cachingConnector: CachingConnector,
  sensitiveFormatService: SensitiveFormatService
)(implicit ec: ExecutionContext)
    extends IabdConnector {

  override def updateTaxCodeAmount(
    nino: Nino,
    taxYear: TaxYear,
    employmentId: Int,
    version: Int,
    iabdType: Int,
    amount: Int
  )(implicit hc: HeaderCarrier): Future[HodUpdateResponse] =
    cachingConnector.invalidateAll {
      underlying.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount)
    }

  override def getIabdsForType(nino: Nino, year: Int, iabdType: String)(implicit
    hc: HeaderCarrier
  ): Future[JsValue] =
    cachingConnector
      .cache(s"iabds-$nino-$year-$iabdType") {
        underlying.getIabdsForType(nino, year, iabdType).map(SensitiveJsValue.apply)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)

  override def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    cachingConnector.invalidateAll {
      underlying.updateExpensesData(nino, year, iabdType, version, expensesData, apiType)
    }
}

class DefaultIabdConnector @Inject() (
  httpHandler: HttpHandler,
  desConfig: DesConfig,
  hipConfig: HipConfig,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends IabdConnector {

  private def getUuid = UUID.randomUUID().toString

  private def hipAuthHeaders(clientIdAndSecret: Option[(String, String)]): Seq[(String, String)] =
    clientIdAndSecret.fold[Seq[(String, String)]](Seq.empty) { case (clientId, clientSecret) =>
      val token = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(StandardCharsets.UTF_8))
      Seq(HeaderNames.authorisation -> s"Basic $token")
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

  override def getIabdsForType(nino: Nino, taxYear: Int, iabdType: String)(implicit
    hc: HeaderCarrier
  ): Future[JsValue] =
    if (taxYear > TaxYear().year) {
      Future.successful(JsArray(Seq.empty))
    } else {
      val (url, originatorId, extraInfo) = (
        s"${hipConfig.baseURL}/iabd/taxpayer/$nino/tax-year/$taxYear?type=$iabdType",
        hipConfig.originatorId,
        Some(hipConfig.clientId -> hipConfig.clientSecret)
      )
      httpHandler.getFromApi(url, APITypes.NpsIabdAllAPI, headersForIabds(originatorId, hc, extraInfo))
    }

  override def updateTaxCodeAmount(
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
      ) ++ hipAuthHeaders(Some(hipConfig.clientId -> hipConfig.clientSecret)) ++ Seq(
        "gov-uk-originator-id" -> hipConfig.originatorId,
        "correlationId"        -> getUuid
      )

    val iabdTypeArgument = URLEncoder.encode(hipMapping(iabdType), "UTF-8").replace("+", "%20")

    httpHandler
      .putToApi(
        url =
          s"${hipConfig.baseURL}/iabd/taxpayer/$nino/tax-year/${taxYear.year}/employment/$empId/type/$iabdTypeArgument",
        data = Json.toJson(
          IabdUpdateAmount(grossAmount = amount, source = Some(NpsSource), currentOptimisticLock = Some(version))
        )(IabdUpdateAmount.writesHip),
        api = APITypes.NpsIabdUpdateEstPayManualAPI,
        headers = requestHeader
      )(implicitly)
      .map(_ => HodUpdateSuccess)
      .recover { case _ => HodUpdateFailure }
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

  override def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    featureFlagService.get(HipIabdsUpdateExpensesToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        val (baseUrl, originatorId, extraInfo) =
          (hipConfig.baseURL, hipConfig.originatorId, Some(hipConfig.clientId -> hipConfig.clientSecret))

        httpHandler.putToApiHttpClientV2[UpdateHipIabdEmployeeExpense](
          s"$baseUrl/iabd/taxpayer/$nino/tax-year/$year/type/${IabdType.hipMapping(iabdType)}",
          UpdateHipIabdEmployeeExpense(version, expensesData.grossAmount),
          HipIabdUpdateEmployeeExpensesAPI,
          HipHeaders.get(originatorId, hc, extraInfo) ++ Seq("ETag" -> version.toString)
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

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier
  ): Future[HodUpdateResponse]

  def getIabdsForType(nino: Nino, year: Int, iabdType: String)(implicit hc: HeaderCarrier): Future[JsValue]

  def updateExpensesData(
    nino: Nino,
    year: Int,
    iabdType: Int,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    apiType: APITypes
  )(implicit hc: HeaderCarrier): Future[HttpResponse]

}
