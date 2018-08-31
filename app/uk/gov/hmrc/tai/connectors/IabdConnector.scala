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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class IabdConnector @Inject()(config: DesConfig,
                              httpHandler: HttpHandler,
                              iabdUrls: IabdUrls){

  def iabds(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {
    if(taxYear > TaxYear()){
      Future.successful(Json.arr())
    } else {
      val hcWithHodHeaders = hc.withExtraHeaders("Gov-Uk-Originator-Id" -> config.originatorId)
      val url = iabdUrls.iabdUrlDes(nino, taxYear)
      httpHandler.getFromApi(url, APITypes.DesIabdAllAPI)(hcWithHodHeaders)
    }
  }
}
