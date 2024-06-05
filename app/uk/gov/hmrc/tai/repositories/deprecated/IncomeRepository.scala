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
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.connectors.{IabdConnector, TaxAccountConnector}
import uk.gov.hmrc.tai.model.domain.formatters.income.{TaxAccountIncomeHodFormatters, TaxCodeIncomeHodFormatters}
import uk.gov.hmrc.tai.model.domain.formatters.{IabdDetails, IabdHodFormatters}
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.UntaxedInterestIncome
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IncomeRepository @Inject() (taxAccountConnector: TaxAccountConnector, iabdConnector: IabdConnector)(implicit
  ec: ExecutionContext
) extends TaxAccountIncomeHodFormatters with TaxCodeIncomeHodFormatters with IabdHodFormatters {

  def incomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Incomes] =
    taxAccountConnector.taxAccount(nino, year).flatMap { jsValue =>
      val nonTaxCodeIncome = jsValue.as[Seq[OtherNonTaxCodeIncome]](nonTaxCodeIncomeReads)
      val (untaxedInterestIncome, otherNonTaxCodeIncome) =
        nonTaxCodeIncome.partition(_.incomeComponentType == UntaxedInterestIncome)

      if (untaxedInterestIncome.nonEmpty) {
        val income = untaxedInterestIncome.head
        val untaxedInterest =
          UntaxedInterest(income.incomeComponentType, income.employmentId, income.amount, income.description)
        Future.successful(
          Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(Some(untaxedInterest), otherNonTaxCodeIncome))
        )
      } else {
        Future.successful(Incomes(Seq.empty[TaxCodeIncome], NonTaxCodeIncome(None, otherNonTaxCodeIncome)))
      }
    }

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] = {
    lazy val taxCodeIncomeFuture = taxAccountConnector
      .taxAccount(nino, year)
      .map(_.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads))
    lazy val iabdDetailsFuture = iabdConnector
      .iabds(nino, year)
      .map { json =>
        val responseNotFound = (json \ "error").asOpt[String].contains("NOT_FOUND")
        if (responseNotFound) {
          throw new NotFoundException(s"No iadbs found for year $year")
        } else {
          json.as[JsValue](iabdEstimatedPayReads)
        }
      }
      .map(_.as[Seq[IabdDetails]])

    for {
      taxCodeIncomes <- taxCodeIncomeFuture
      iabdDetails    <- iabdDetailsFuture
    } yield taxCodeIncomes.map { taxCodeIncome =>
      addIabdDetailsToTaxCodeIncome(iabdDetails, taxCodeIncome)
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
