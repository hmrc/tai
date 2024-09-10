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
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.config.{DesConfig, NpsConfig}
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.SensitiveFormatService
import uk.gov.hmrc.tai.service.SensitiveFormatService.SensitiveJsValue
import uk.gov.hmrc.tai.util.TaiConstants

import java.util.UUID
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
          .map(SensitiveJsValue)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] =
    cachingConnector
      .cache(s"tax-account-history-$nino-$iocdSeqNo") {
        underlying
          .taxAccountHistory(nino: Nino, iocdSeqNo: Int)
          .map(SensitiveJsValue)
      }(sensitiveFormatService.sensitiveFormatJsValue[JsValue], implicitly)
      .map(_.decryptedValue)
}

class DefaultTaxAccountConnector @Inject() (
  httpHandler: HttpHandler,
  npsConfig: NpsConfig,
  desConfig: DesConfig,
  taxAccountUrls: TaxAccountUrls
) extends TaxAccountConnector {
  private def getUuid = UUID.randomUUID().toString

  private def hcWithHodHeaders(implicit hc: HeaderCarrier) =
    Seq(
      "Gov-Uk-Originator-Id" -> npsConfig.originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "CorrelationId"        -> getUuid
    )

  private def hcWithDesHeaders(implicit hc: HeaderCarrier) =
    Seq(
      "Gov-Uk-Originator-Id" -> desConfig.originatorId,
      HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
      HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
      "Environment"          -> desConfig.environment,
      "Authorization"        -> desConfig.authorization,
      "Content-Type"         -> TaiConstants.contentType,
      "CorrelationId"        -> getUuid
    )

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    httpHandler.getFromApi(taxAccountUrls.taxAccountUrl(nino, taxYear), APITypes.NpsTaxAccountAPI, hcWithHodHeaders)

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, iocdSeqNo)
    httpHandler.getFromApi(url, APITypes.DesTaxAccountAPI, hcWithDesHeaders)
  }
}

trait TaxAccountConnector {
  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue]

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue]
}
