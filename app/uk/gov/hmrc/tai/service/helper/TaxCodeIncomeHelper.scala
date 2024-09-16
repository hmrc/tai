/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service.helper

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.IabdDetails
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.IabdService
import uk.gov.hmrc.tai.util.JsonHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxCodeIncomeHelper @Inject() (
  taxAccountConnector: TaxAccountConnector,
  iabdService: IabdService,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext) {
  def fetchTaxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] = {
    lazy val iabdDetailsFuture = iabdService.retrieveIabdDetails(nino, year)

    for {
      reads <- JsonHelper.getReads(
                 featureFlagService,
                 TaxCodeIncomeHipToggleOff.taxCodeIncomeSourcesReads,
                 TaxCodeIncomeHipToggleOn.taxCodeIncomeSourcesReads
               )
      taxCodeIncomes <- taxAccountConnector
                          .taxAccount(nino, year)
                          .map(_.as[Seq[TaxCodeIncome]](reads))
      iabdDetails <- iabdDetailsFuture
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

  def incomeAmountForEmploymentId(nino: Nino, year: TaxYear, employmentId: Int)(implicit
    hc: HeaderCarrier
  ): Future[Option[String]] = {
    val taxCodeIncomes = fetchTaxCodeIncomes(nino, year)
    taxCodeIncomes.map { taxCodeIncomes =>
      taxCodeIncomes.find(_.employmentId.contains(employmentId)).map(_.amount.toString())
    }
  }
}
