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
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateResponse, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.{NpsIabdUpdateAmount, NpsIabdUpdateAmountFormats}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class TaxAccountNpsConnector @Inject()(config: NpsConfig,
                                       taxAccountUrls: TaxAccountUrls,
                                       iabdUrls: IabdUrls,
                                       npsIabdUpdateAmountFormats: NpsIabdUpdateAmountFormats,
                                       httpHandler: HttpHandler){

  def taxAccount(nino:Nino, taxYear:TaxYear)(implicit hc:HeaderCarrier): Future[JsValue] = {
    val hcWithHodHeaders = hc.withExtraHeaders("Gov-Uk-Originator-Id" -> config.originatorId)
    val url = taxAccountUrls.taxAccountUrlNps(nino, taxYear)
    httpHandler.getFromApi(url, APITypes.NpsTaxAccountAPI)(hcWithHodHeaders)
  }

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, employmentId: Int, version: Int, iabdType: Int, source: Int, amount: Int)
                         (implicit hc: HeaderCarrier): Future[HodUpdateResponse] = {

    val url = iabdUrls.iabdEmploymentUrl(nino, taxYear, iabdType)

    httpHandler.postToApi[List[NpsIabdUpdateAmount]](
      url,
      List(NpsIabdUpdateAmount(employmentSequenceNumber = employmentId, grossAmount = amount, source = Some(source))),
      APITypes.NpsIabdUpdateEstPayManualAPI
    )(headersForUpdate(hc, version, sessionOrUUID, config.originatorId), npsIabdUpdateAmountFormats.formatList).map { _ =>
          HodUpdateSuccess

    }.recover{ case _ => HodUpdateFailure}
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
}