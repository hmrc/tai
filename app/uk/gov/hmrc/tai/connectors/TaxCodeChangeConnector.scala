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

import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

case class TaxCodeHistoryItem(employmentId: Int, p2Issued: Boolean, p2Date: String)

object TaxCodeHistoryItem {
  implicit val reads: Reads[TaxCodeHistoryItem] = (
    (__ \ "employmentId").read[Int] and
      (__ \ "p2Issued").read[Boolean] and
      (__ \ "p2Date").read[String]
    )(TaxCodeHistoryItem.apply _)
}

case class TaxCodeHistoryDetails(nino: Nino, taxCodeHistoryItems: Seq[TaxCodeHistoryItem])

object TaxCodeHistoryDetails {

  implicit val reads: Reads[TaxCodeHistoryDetails] = (
    (__ \ "nino").read[Nino] and
      (__ \ "taxHistoryList").read[Seq[TaxCodeHistoryItem]]
    )(TaxCodeHistoryDetails.apply _)
}

@Singleton
class TaxCodeChangeConnector @Inject()(config: NpsConfig,
                                       urlConfig: TaxCodeChangeUrl,
                                       httpHandler: HttpHandler){

  def taxCodeHistory(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    val url = s"http://localhost:9332/personal-tax-account/tax-code/history/api/v1/${nino.nino}/${taxYear.year}"
    httpHandler.getFromApi(url, APITypes.TaxCodeChangeAPI)
  }
}
