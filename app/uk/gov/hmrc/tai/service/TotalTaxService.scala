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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.Reads
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.admin.HipToggleTaxAccount
import uk.gov.hmrc.tai.model.domain.calculation.TotalTaxHipToggleOff.taxFreeAllowanceReads
import uk.gov.hmrc.tai.model.domain.calculation.{IncomeCategory, TotalTax, TotalTaxHipToggleOff, TotalTaxHipToggleOn}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxAccountHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TotalTaxService @Inject() (
  taxAccountConnector: TaxAccountConnector,
  taxAccountHelper: TaxAccountHelper,
  featureFlagService: FeatureFlagService
)(implicit ec: ExecutionContext) {
  private def getReads[A](readsToggleOff: Reads[A], readsToggleOn: Reads[A]): Future[Reads[A]] =
    featureFlagService.get(HipToggleTaxAccount).map { flag =>
      if (flag.isEnabled) {
        readsToggleOn
      } else {
        readsToggleOff
      }
    }
  def totalTax(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TotalTax] = {
    val taxAccountDetails = taxAccountConnector.taxAccount(nino, year)

    for {
      reads <- getReads(
                 TotalTaxHipToggleOff.incomeCategorySeqReads,
                 TotalTaxHipToggleOn.incomeCategorySeqReads
               )
      incomeCategories     <- taxAccountDetails.map(_.as[Seq[IncomeCategory]](reads))
      totalTaxAmount       <- taxAccountHelper.totalEstimatedTax(nino, year)
      reliefsGivingBackTax <- taxAccountHelper.reliefsGivingBackTaxComponents(taxAccountDetails)
      otherTaxDue          <- taxAccountHelper.otherTaxDueComponents(taxAccountDetails)
      alreadyTaxedAtSource <- taxAccountHelper.alreadyTaxedAtSourceComponents(taxAccountDetails)
      taxOnOtherIncome     <- taxAccountHelper.taxOnOtherIncome(taxAccountDetails)
      taxReliefComponents  <- taxAccountHelper.taxReliefComponents(taxAccountDetails)
    } yield TotalTax(
      totalTaxAmount,
      incomeCategories,
      reliefsGivingBackTax,
      otherTaxDue,
      alreadyTaxedAtSource,
      taxOnOtherIncome,
      taxReliefComponents
    )
  }

  def taxFreeAllowance(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[BigDecimal] =
    getReads(
      TotalTaxHipToggleOff.taxFreeAllowanceReads,
      TotalTaxHipToggleOn.taxFreeAllowanceReads
    ).flatMap { reads =>
      taxAccountConnector
        .taxAccount(nino, year)
        .map(_.as[BigDecimal](reads))
    }
}
