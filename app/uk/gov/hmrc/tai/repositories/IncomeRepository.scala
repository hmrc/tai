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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.formatters.IabdHodFormatters
import uk.gov.hmrc.tai.model.domain.formatters.income.{TaxAccountIncomeHodFormatters, TaxCodeIncomeHodFormatters}
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.{BankAccount, UntaxedInterestIncome}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future
import scala.util.control.NonFatal


@Singleton
class IncomeRepository @Inject()(taxAccountRepository: TaxAccountRepository,
                                 bbsiRepository: BbsiRepository,
                                 iabdRepository: IabdRepository)
  extends TaxAccountIncomeHodFormatters
    with TaxCodeIncomeHodFormatters
    with IabdHodFormatters {

  def incomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Incomes] = {
    taxAccountRepository.taxAccount(nino, year) flatMap  { jsValue =>
      val nonTaxCodeIncome = jsValue.as[Seq[OtherNonTaxCodeIncome]](nonTaxCodeIncomeReads)
      val (untaxedInterestIncome, otherNonTaxCodeIncome) = nonTaxCodeIncome.partition(_.incomeComponentType == UntaxedInterestIncome)

      if(untaxedInterestIncome.nonEmpty){
        for {
          accounts <- bbsiRepository.bbsiDetails(nino, year).recover({ case NonFatal(_) => Seq.empty[BankAccount] })
        } yield {
          val income = untaxedInterestIncome.head
          val untaxedInterest = UntaxedInterest(income.incomeComponentType, income.employmentId, income.amount, income.description, accounts)
          Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(Some(untaxedInterest), otherNonTaxCodeIncome))
        }
      } else {
        Future.successful(Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(None, otherNonTaxCodeIncome)))
      }
    }
  }

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] = {
    val taxCodeIncomeFuture = taxAccountRepository.taxAccount(nino, year) map (_.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads))
    val iabdDetailsFuture = iabdRepository.iabds(nino, year) map(_.as[Seq[IabdDetails]])

    for {
      taxCodeIncomes <- taxCodeIncomeFuture
      iabdDetails <- iabdDetailsFuture
    } yield {

      Logger.warn(s"taxCodeIncomes size for $nino for $year is [${taxCodeIncomes.size}]")
      Logger.warn(s"iabdDetails size for $nino for $year is [${iabdDetails.size}]")

      taxCodeIncomes.map { taxCodeIncome =>
        addIabdDetailsToTaxCodeIncome(iabdDetails, taxCodeIncome)
      }
    }
  }


  private def addIabdDetailsToTaxCodeIncome(iabdDetails: Seq[IabdDetails], taxCodeIncome: TaxCodeIncome) = {
    val iabdDetail = iabdDetails.find(_.employmentSequenceNumber == taxCodeIncome.employmentId)
    taxCodeIncome.copy(
      iabdUpdateSource = iabdDetail.flatMap(_.source).flatMap(code => IabdUpdateSource.fromCode(code)),
      updateNotificationDate = iabdDetail.flatMap(_.receiptDate),
      updateActionDate = iabdDetail.flatMap(_.captureDate)
    )
  }
}
