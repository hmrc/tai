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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.calculation.IncomeCategory.{incomeCategorySeqReads, taxFreeAllowanceReads}
import uk.gov.hmrc.tai.model.domain.calculation.{IncomeCategory, TotalTax}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxAccountHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TotalTaxService @Inject() (
  taxAccountConnector: TaxAccountConnector,
  taxAccountHelper: TaxAccountHelper
)(implicit ec: ExecutionContext) {

  def totalTax(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[TotalTax] = {
    val taxAccountDetails = taxAccountConnector.taxAccount(nino, year)
    for {
      incomeCategories     <- taxAccountDetails.map(_.as[Seq[IncomeCategory]](incomeCategorySeqReads))
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
    taxAccountConnector
      .taxAccount(nino, year)
      .map(_.as[BigDecimal](taxFreeAllowanceReads))
}
