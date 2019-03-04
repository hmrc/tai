/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.concurrent.Execution.Implicits.defaultContext
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

import scala.concurrent.Future

@Singleton
class TaxAccountConnector @Inject()(npsConfig: NpsConfig,
                                    desConfig: DesConfig,
                                    taxAccountUrls: TaxAccountUrls,
                                    iabdUrls: IabdUrls,
                                    IabdUpdateAmountFormats: IabdUpdateAmountFormats,
                                    httpHandler: HttpHandler,
                                    featureTogglesConfig: FeatureTogglesConfig) extends HodsSource {

  def taxAccount(nino:Nino, taxYear:TaxYear)(implicit hc:HeaderCarrier): Future[JsValue] = {

    def taxAccountURL = (url: String, originatorId: String) => {
      val hcWithHodHeaders = hc.withExtraHeaders("Gov-Uk-Originator-Id" -> originatorId)
      httpHandler.getFromApi(url, APITypes.NpsTaxAccountAPI)(hcWithHodHeaders)
    }

    def npsURL(url: String) = taxAccountURL(url, npsConfig.originatorId)
    def desURL(url: String) = taxAccountURL(url, desConfig.originatorId)


    (featureTogglesConfig.desEnabled, featureTogglesConfig.confirmedAPIEnabled) match {
      case(false, false) => npsURL(taxAccountUrls.taxAccountUrlNpsCalculation(nino, taxYear))
      case(false, true) => npsURL(taxAccountUrls.taxAccountUrlNpsConfirmed(nino, taxYear))
      case(true, true) => desURL(taxAccountUrls.taxAccountUrlDesConfirmed(nino, taxYear))
      case(true, false) => desURL(taxAccountUrls.taxAccountUrlDesCalculation(nino, taxYear))
    }
  }


  def taxAccountHistory(nino: Nino, iocdSeqNo: Int)(implicit hc:HeaderCarrier): Future[JsValue] = {
    implicit val hc: HeaderCarrier = createHeader.withExtraHeaders("Gov-Uk-Originator-Id" -> desConfig.originatorId)
    val url = taxAccountUrls.taxAccountHistoricSnapshotUrl(nino, iocdSeqNo)
    httpHandler.getFromApi(url, APITypes.DesTaxAccountAPI)
  }

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, amount: Int)
                         (implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {

    if(featureTogglesConfig.desUpdateEnabled) {
      val url = iabdUrls.desIabdEmploymentUrl(nino, taxYear, iabdType)
      val amountList =  List(
        IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(DesSource))
      )
      val requestHeader = headersForUpdate(hc, version, sessionOrUUID, desConfig.originatorId)

      httpHandler.postToApi[List[IabdUpdateAmount]](url, amountList, APITypes.DesIabdUpdateEstPayAutoAPI)(
        requestHeader, IabdUpdateAmountFormats.formatList
      ).map { _ => HodUpdateSuccess}.recover { case _ => HodUpdateFailure }
    }
    else {
      val url = iabdUrls.npsIabdEmploymentUrl(nino, taxYear, iabdType)
      val amountList = List(
          IabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(NpsSource))
      )
      val requestHeader = headersForUpdate(hc, version, sessionOrUUID, npsConfig.originatorId)

      httpHandler.postToApi[List[IabdUpdateAmount]](url, amountList, APITypes.NpsIabdUpdateEstPayManualAPI)(
        requestHeader, IabdUpdateAmountFormats.formatList
      ).map { _ => HodUpdateSuccess}.recover{ case _ => HodUpdateFailure}
    }

  }

  def sessionOrUUID(implicit hc: HeaderCarrier): String = {
    hc.sessionId match {
      case Some(sessionId) => sessionId.value
      case None => UUID.randomUUID().toString.replace("-", "")
    }
  }

  def headersForUpdate(hc: HeaderCarrier, version: Int, txId: String, originatorId: String): HeaderCarrier = {
    hc.withExtraHeaders("ETag" -> version.toString, "X-TXID" -> txId, "Gov-Uk-Originator-Id" -> originatorId)
  }

  val createHeader = HeaderCarrier(extraHeaders =
    Seq(
      "Environment" -> desConfig.environment,
      "Authorization" -> desConfig.authorization,
      "Content-Type" -> TaiConstants.contentType))
}