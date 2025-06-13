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
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.*
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.{DesConfig, HipConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.admin.HipTaxAccountHistoryToggle
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.SensitiveFormatService
import uk.gov.hmrc.tai.service.SensitiveFormatService.SensitiveJsValue
import uk.gov.hmrc.tai.util.TaiConstants

import java.nio.charset.StandardCharsets
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CachingTaxAccountConnector @Inject() (
  @Named("default") underlying: TaxAccountConnector,
  cachingConnector: CachingConnector,
  sensitiveFormatService: SensitiveFormatService
)(implicit ec: ExecutionContext)
    extends TaxAccountConnector {

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    cachingConnector
      .cache(s"tax-account-$nino-${taxYear.year}") {
        underlying
          .taxAccount(nino: Nino, taxYear: TaxYear)
          .map(SensitiveJsValue.apply)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] =
    cachingConnector
      .cache(s"tax-account-history-$nino-$iocdSeqNo") {
        underlying
          .taxAccountHistory(nino: Nino, iocdSeqNo: Int)
          .map(SensitiveJsValue.apply)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)
}

class DefaultTaxAccountConnector @Inject() (
  httpHandler: HttpHandler,
  desConfig: DesConfig,
  taxAccountUrls: TaxAccountUrls,
  hipConfig: HipConfig,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext)
    extends TaxAccountConnector {

  private def getUuid: String = UUID.randomUUID().toString

  private def hcWithDesHeaders(implicit hc: HeaderCarrier): Seq[(String, String)] =
    Seq(
      "Gov-Uk-Originator-Id" -> desConfig.originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      "CorrelationId"        -> getUuid
    )

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val (baseUrl, originatorId, extraInfo) =
      (hipConfig.baseURL, hipConfig.originatorId, Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret)))

    val urlToRead = s"$baseUrl/person/${nino.nino}/tax-account/${taxYear.year}"
    httpHandler
      .getFromApi(urlToRead, APITypes.NpsTaxAccountAPI, basicHeaders(originatorId, hc, extraInfo))
      .map {
        case response if response == JsObject.empty => throw new NotFoundException(response.toString)
        case response                               => response
      }

  }

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] =
    featureFlagService.get(HipTaxAccountHistoryToggle).flatMap { toggle =>
      val (url, headers) =
        if (toggle.isEnabled) {
          (
            s"${hipConfig.baseURL}/person/$nino/tax-account/history/$iocdSeqNo",
            basicHeaders(hipConfig.originatorId, hc, Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret)))
          )
        } else {
          (taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, iocdSeqNo), hcWithDesHeaders)
        }

      httpHandler.getFromApi(url, APITypes.DesTaxAccountAPI, headers)
    }
}

trait TaxAccountConnector {
  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue]

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue]

  def basicHeaders(
    originatorId: String,
    hc: HeaderCarrier,
    hipExtraInfo: Option[(String, String)]
  ): Seq[(String, String)] = {
    val hipAuth = hipExtraInfo.fold[Seq[(String, String)]](Seq.empty) { case (clientId, clientSecret) =>
      val token = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes(StandardCharsets.UTF_8))
      Seq(
        HeaderNames.authorisation -> s"Basic $token"
      )
    }
    Seq(
      play.api.http.HeaderNames.CONTENT_TYPE -> MimeTypes.JSON,
      "Gov-Uk-Originator-Id"                 -> originatorId,
      HeaderNames.xSessionId                 -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId                 -> hc.requestId.fold("-")(_.value),
      "CorrelationId"                        -> UUID.randomUUID().toString
    ) ++ hipAuth
  }
}
