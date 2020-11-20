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

import com.google.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import play.api.http.Status._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.http._
import uk.gov.hmrc.tai.config.DesConfig
import uk.gov.hmrc.tai.model.domain.formatters.BbsiHodFormatters
import uk.gov.hmrc.tai.util.TaiConstants

@Singleton
class BbsiConnector @Inject()(httpHandler: HttpHandler, urls: BbsiUrls, config: DesConfig)(
  implicit ec: ExecutionContext) {

  def createHeader: HeaderCarrier =
    HeaderCarrier(
      extraHeaders = Seq(
        "Environment"   -> config.environment,
        "Authorization" -> s"Bearer ${config.authorization}",
        "Content-Type"  -> TaiConstants.contentType))

  def bankAccounts(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[HttpResponse, Seq[BankAccount]]] = {
    implicit val hc: HeaderCarrier = createHeader

    httpHandler.getFromApiV2(urls.bbsiUrl(nino, taxYear), APITypes.BbsiAPI) map { response =>
      response.status match {
        case OK =>
          response.json.asOpt[Seq[BankAccount]](BbsiHodFormatters.bankAccountHodReads) match {
            case Some(accounts) => Right(accounts)
            case None           => Left(HttpResponse(INTERNAL_SERVER_ERROR, "Could not parse Json"))
          }
        case _ => Left(response)
      }
    } recover {
      case e => Left(HttpResponse(INTERNAL_SERVER_ERROR, e.getMessage))
    }
  }
}
