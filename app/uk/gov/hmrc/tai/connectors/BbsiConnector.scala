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

import com.google.inject.{Inject, Singleton}

import scala.concurrent.Future
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.domain.formatters.BbsiHodFormatters
import uk.gov.hmrc.tai.util.TaiConstants

import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class BbsiConnector @Inject()(httpHandler: HttpHandler, urls: BbsiUrls, config: DesConfig) {

  def createHeader(hc: HeaderCarrier): HeaderCarrier =
    hc.withExtraHeaders(
      "Environment"   -> config.environment,
      "Authorization" -> s"Bearer ${config.authorization}",
      "Content-Type"  -> TaiConstants.contentType)

  def bankAccounts(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[BankAccount]] = {
    val headerCarrier: HeaderCarrier = createHeader(hc)
    httpHandler.getFromApi(urls.bbsiUrl(nino, taxYear), APITypes.BbsiAPI)(headerCarrier) map { json =>
      json.as[Seq[BankAccount]](BbsiHodFormatters.bankAccountHodReads)
    }
  }
}
