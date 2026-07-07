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
import play.api.libs.json.{JsObject, JsValue}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.*
import uk.gov.hmrc.tai.config.HipConfig
import uk.gov.hmrc.tai.connectors.cache.CachingConnector
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.SensitiveFormatService
import uk.gov.hmrc.tai.service.SensitiveFormatService.SensitiveJsValue

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
  hipConfig: HipConfig
)(implicit ec: ExecutionContext)
    extends TaxAccountConnector {

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val (baseUrl, originatorId, extraInfo) =
      (hipConfig.baseURL, hipConfig.originatorId, Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret)))

    val urlToRead = s"$baseUrl/person/${nino.nino}/tax-account/${taxYear.year}"
    httpHandler
      .getFromApi(urlToRead, APITypes.NpsTaxAccountAPI, HipHeaders.get(originatorId, hc, extraInfo))
      .map {
        case response if response == JsObject.empty => throw new NotFoundException(response.toString)
        case response                               => response
      }
  }

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"${hipConfig.baseURL}/person/$nino/tax-account/history/$iocdSeqNo"
    val headers = HipHeaders.get(hipConfig.originatorId, hc, Some(Tuple2(hipConfig.clientId, hipConfig.clientSecret)))
    httpHandler.getFromApi(url, APITypes.DesTaxAccountAPI, headers)
  }
}

trait TaxAccountConnector {
  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue]

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue]

}
