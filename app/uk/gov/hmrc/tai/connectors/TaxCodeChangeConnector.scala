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
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.NpsConfig
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future
import scala.util.Random

@Singleton
class TaxCodeChangeConnector @Inject()(config: NpsConfig,
                                       taxCodeChangeUrl: TaxCodeChangeUrl,
                                       httpHandler: HttpHandler){

  def taxCodeChanges(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] = {

    val response = Json.obj(
      "nino" -> new Generator(new Random).nextNino,
      "taxHistoryList" -> Seq(
        Json.obj(
          "employmentId" -> 1234567890,
          "p2Issued" -> true
        )
      )
    )

    Future.successful(response)
  }
}
