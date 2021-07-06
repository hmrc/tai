/*
 * Copyright 2021 HM Revenue & Customs
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

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.domain.formatters.BbsiHodFormatters
import uk.gov.hmrc.tai.util.TaiConstants

import java.util.UUID

@Singleton
class BbsiConnector @Inject()(httpHandler: HttpHandler, urls: BbsiUrls, config: DesConfig)(
  implicit ec: ExecutionContext) {

  def createHeader(implicit hc: HeaderCarrier) = Seq(
    "Environment"          -> config.environment,
    "Authorization"        -> s"Bearer ${config.authorization}",
    "Content-Type"         -> TaiConstants.contentType,
    HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
    HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value),
    "CorrelationId"        -> UUID.randomUUID().toString
  )

  def bankAccounts(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[BankAccount]] =
    httpHandler.getFromApi(urls.bbsiUrl(nino, taxYear), APITypes.BbsiAPI, createHeader) map { json =>
      json.as[Seq[BankAccount]](BbsiHodFormatters.bankAccountHodReads)
    }
}
