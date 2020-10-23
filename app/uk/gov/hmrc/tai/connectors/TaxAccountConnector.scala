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
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.{DesConfig, FeatureTogglesConfig, NpsConfig}
import uk.gov.hmrc.tai.model.IabdUpdateAmountFormats
import uk.gov.hmrc.tai.model.IabdUpdateAmount
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.{HodsSource, TaiConstants}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxAccountConnector @Inject()(
  npsConfig: NpsConfig,
  desConfig: DesConfig,
  taxAccountUrls: TaxAccountUrls,
  iabdUrls: IabdUrls,
  IabdUpdateAmountFormats: IabdUpdateAmountFormats,
  httpHandler: HttpHandler,
  featureTogglesConfig: FeatureTogglesConfig)(implicit ec: ExecutionContext)
    extends HodsSource {

  def hcWithHodHeaders(implicit hc: HeaderCarrier): HeaderCarrier =
    if (featureTogglesConfig.desEnabled) {
      createHeader.withExtraHeaders("Gov-Uk-Originator-Id" -> desConfig.originatorId)
    } else {
      hc.withExtraHeaders("Gov-Uk-Originator-Id" -> npsConfig.originatorId)
    }

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    httpHandler.getFromApi(taxAccountUrls.taxAccountUrl(nino, taxYear), APITypes.NpsTaxAccountAPI)(hcWithHodHeaders)

  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc: HeaderCarrier): Future[JsValue] = {
    implicit val hc: HeaderCarrier = createHeader.withExtraHeaders("Gov-Uk-Originator-Id" -> desConfig.originatorId)
    val url = taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, iocdSeqNo)
    httpHandler.getFromApi(url, APITypes.DesTaxAccountAPI)
  }

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse] =
    if (featureTogglesConfig.desUpdateEnabled) {
      val url = iabdUrls.desIabdEmploymentUrl(nino, taxYear, iabdType)
      val amountList = List(
        IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(DesSource))
      )
      val requestHeader = headersForUpdate(hc, version, sessionOrUUID, desConfig.originatorId)

      httpHandler
        .postToApi[List[IabdUpdateAmount]](url, amountList, APITypes.DesIabdUpdateEstPayAutoAPI)(
          requestHeader,
          IabdUpdateAmountFormats.formatList
        )
        .map { _ =>
          HodUpdateSuccess
        }
        .recover { case _ => HodUpdateFailure }
    } else {
      val url = iabdUrls.npsIabdEmploymentUrl(nino, taxYear, iabdType)
      val amountList = List(
        IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(NpsSource))
      )
      val requestHeader = headersForUpdate(hc, version, sessionOrUUID, npsConfig.originatorId)

      httpHandler
        .postToApi[List[IabdUpdateAmount]](url, amountList, APITypes.NpsIabdUpdateEstPayManualAPI)(
          requestHeader,
          IabdUpdateAmountFormats.formatList
        )
        .map { _ =>
          HodUpdateSuccess
        }
        .recover { case _ => HodUpdateFailure }
    }

  def sessionOrUUID(implicit hc: HeaderCarrier): String =
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None            => UUID.randomUUID().toString.replace("-", "")
    }

  def headersForUpdate(hc: HeaderCarrier, version: Int, txId: String, originatorId: String): HeaderCarrier =
    hc.withExtraHeaders("ETag" -> version.toString, "X-TXID" -> txId, "Gov-Uk-Originator-Id" -> originatorId)

  val createHeader = HeaderCarrier(
    extraHeaders = Seq(
      "Environment"   -> desConfig.environment,
      "Authorization" -> desConfig.authorization,
      "Content-Type"  -> TaiConstants.contentType))
}
