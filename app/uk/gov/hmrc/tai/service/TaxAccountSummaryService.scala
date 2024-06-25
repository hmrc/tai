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
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.formatters.TaxAccountSummaryHodFormatters
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.TaxAccountHodFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class TaxAccountSummaryService @Inject() (
  taxAccountConnector: TaxAccountConnector,
  codingComponentService: CodingComponentService,
  incomeService: IncomeService,
  totalTaxService: TotalTaxService
)(implicit
  ec: ExecutionContext
) extends TaxAccountSummaryHodFormatters with TaxAccountHodFormatters {

  def taxAccountSummary(nino: Nino, year: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[TaxAccountSummary] =
    for {
      totalEstimatedTax       <- totalEstimatedTax(nino, year)
      taxFreeAmountComponents <- codingComponentService.codingComponents(nino, year)
      taxCodeIncomes          <- incomeService.taxCodeIncomes(nino, year)
      totalTax                <- totalTaxService.totalTax(nino, year)
      taxFreeAllowance        <- totalTaxService.taxFreeAllowance(nino, year)
    } yield {

      val taxFreeAmount = taxFreeAmountCalculation(taxFreeAmountComponents)
      val totalIyaIntoCY = taxCodeIncomes map (_.inYearAdjustmentIntoCY) sum
      val totalIya = taxCodeIncomes map (_.totalInYearAdjustment) sum
      val totalIyatIntoCYPlusOne = taxCodeIncomes map (_.inYearAdjustmentIntoCYPlusOne) sum
      val incomeCategoriesSum = totalTax.incomeCategories.map(_.totalTaxableIncome).sum
      val totalEstimatedIncome =
        if (incomeCategoriesSum == 0) totalTax.incomeCategories.map(_.totalIncome).sum
        else incomeCategoriesSum + taxFreeAllowance

      TaxAccountSummary(
        totalEstimatedTax,
        taxFreeAmount,
        totalIyaIntoCY,
        totalIya,
        totalIyatIntoCYPlusOne,
        totalEstimatedIncome,
        taxFreeAllowance
      )
    }

  def totalEstimatedTax(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[BigDecimal] = {
    val componentTypesCanAffectTotalEst: Seq[TaxComponentType] =
      Seq(UnderPaymentFromPreviousYear, OutstandingDebt, EstimatedTaxYouOweThisYear)

    taxAccountConnector
      .taxAccount(nino, year)
      .flatMap { taxAccount =>
        val totalTax = taxAccount.as[BigDecimal](taxAccountSummaryReads)
        val componentsCanAffectTotal = taxAccount
          .as[Seq[CodingComponent]](codingComponentReads)
          .filter(c => componentTypesCanAffectTotalEst.contains(c.componentType))
        Future(totalTax + componentsCanAffectTotal.map(_.inputAmount.getOrElse(BigDecimal(0))).sum)
      }
  }

  private[service] def taxFreeAmountCalculation(codingComponents: Seq[CodingComponent]): BigDecimal =
    codingComponents.foldLeft(BigDecimal(0))((total: BigDecimal, component: CodingComponent) =>
      component.componentType match {
        case _: AllowanceComponentType => total + component.amount.abs
        case _                         => total - component.amount.abs
      }
    )
}
