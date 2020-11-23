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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.http.Status._
import play.api.libs.json.{JsError, JsSuccess}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.connectors.{BbsiConnector, CacheConnector, CacheId}
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.domain.formatters.{BbsiHodFormatters, BbsiMongoFormatters}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BbsiRepository @Inject()(cacheConnector: CacheConnector, bbsiConnector: BbsiConnector)(
  implicit ec: ExecutionContext) {

  private val BBSIKey = "BankAndBuildingSocietyInterest"

  def bbsiDetails(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[HttpResponse, Seq[BankAccount]]] = {
    val cacheId = CacheId(nino)

    cacheConnector.findOptSeq[BankAccount](cacheId, BBSIKey)(BbsiMongoFormatters.bbsiFormat) flatMap {
      case None =>
        fetchBankAccounts(nino, taxYear) flatMap {
          case Right(accounts) =>
            cacheConnector.createOrUpdateSeq(cacheId, populateId(accounts), BBSIKey)(BbsiMongoFormatters.bbsiFormat) map {
              Right(_)
            }
          case error @ _ => Future.successful(error)
        }
      case Some(accounts) => Future.successful(Right(accounts))
    }
  }

  private def populateId(accounts: Seq[BankAccount]): Seq[BankAccount] = {

    def updateIds(acc: Seq[BankAccount], tup: (BankAccount, Int)) = acc :+ tup._1.copy(id = tup._2)

    accounts.zipWithIndex
      .map { case (account: BankAccount, index: Int) => (account, index + 1) }
      .foldLeft(Seq.empty[BankAccount])(updateIds)
  }

  private def fetchBankAccounts(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[HttpResponse, Seq[BankAccount]]] =
    bbsiConnector.bankAccounts(nino, taxYear) map { response =>
      response.status match {
        case OK =>
          response.json.validate[Seq[BankAccount]](BbsiHodFormatters.bankAccountHodReads) match {
            case JsSuccess(value, _) => Right(value)
            case JsError(_)          => Left(HttpResponse(INTERNAL_SERVER_ERROR, "Could not parse Json"))
          }
        case _ => Left(response)
      }
    } recover {
      case e => Left(HttpResponse(INTERNAL_SERVER_ERROR, e.getMessage))
    }

}
