/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories.deprecated

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.connectors.deprecated.BbsiConnector
import uk.gov.hmrc.tai.model.domain.BankAccount
import uk.gov.hmrc.tai.model.domain.formatters.BbsiMongoFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BbsiRepository @Inject() (taiCacheRepository: TaiCacheRepository, bbsiConnector: BbsiConnector)(implicit
  ec: ExecutionContext
) {

  val BBSIKey = "BankAndBuildingSocietyInterest"

  def bbsiDetails(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[BankAccount]] = {
    val cacheId = CacheId(nino)

    taiCacheRepository.findOptSeq[BankAccount](cacheId, BBSIKey)(BbsiMongoFormatters.bbsiFormat) flatMap {
      case None =>
        for {
          accounts <- bbsiConnector.bankAccounts(nino, taxYear)
          accountsWithId <- taiCacheRepository
                              .createOrUpdateSeq(cacheId, populateId(accounts), BBSIKey)(BbsiMongoFormatters.bbsiFormat)
        } yield accountsWithId
      case Some(accounts) => Future.successful(accounts)
    }
  }

  private def populateId(accounts: Seq[BankAccount]): Seq[BankAccount] = {

    def updateIds(acc: Seq[BankAccount], tup: (BankAccount, Int)) = acc :+ tup._1.copy(id = tup._2)

    accounts.zipWithIndex
      .map { case (account: BankAccount, index: Int) => (account, index + 1) }
      .foldLeft(Seq.empty[BankAccount])(updateIds)
  }
}
