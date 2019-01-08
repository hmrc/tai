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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.TaiRoot
import uk.gov.hmrc.tai.model.domain.income.{Incomes, TaxCodeIncome}
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.domain.{Employment, income}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{IncomeRepository, TaxAccountRepository}

import scala.concurrent.Future

@Singleton
class IncomeService @Inject()(employmentService: EmploymentService,
                              taxAccountService: TaxAccountService,
                              incomeRepository: IncomeRepository,
                              taxAccountRepository: TaxAccountRepository,
                              auditor: Auditor) {

  def untaxedInterest(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[income.UntaxedInterest]] = {
    incomes(nino, TaxYear()).map(_.nonTaxCodeIncomes.untaxedInterest)
  }

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] = {
    incomeRepository.taxCodeIncomes(nino, year)
  }

  def incomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Incomes] = {
    incomeRepository.incomes(nino, year)
  }

  def updateTaxCodeIncome(nino: Nino, year: TaxYear, employmentId: Int, amount: Int)
                          (implicit hc: HeaderCarrier): Future[IncomeUpdateResponse] = {

    val auditEventForIncomeUpdate: String => Unit = (currentAmount: String) => {
      auditor.sendDataEvent(
        transactionName = "Update Multiple Employments Data",
        detail = Map("nino" -> nino.value,
          "year" -> year.toString,
          "employmentId" -> employmentId.toString,
          "newAmount" -> amount.toString,
        "currentAmount" -> currentAmount))
    }

    for {
      incomeAmount <- incomeAmountForEmploymentId(nino, year, employmentId)
      personDetails <- taxAccountService.personDetails(nino)
      incomeUpdateResponse <- updateTaxCodeAmount(nino, year, employmentId, personDetails.version, amount)
    } yield {

      if(incomeUpdateResponse == IncomeUpdateSuccess) auditEventForIncomeUpdate(incomeAmount.getOrElse("Unknown"))
      incomeUpdateResponse
    }
  }

  private def incomeAmountForEmploymentId(nino: Nino, year: TaxYear, employmentId: Int)
                                 (implicit hc: HeaderCarrier): Future[Option[String]] = {
    taxCodeIncomes(nino, year) map { taxCodeIncomes =>
      taxCodeIncomes.find(_.employmentId.contains(employmentId)) match {
        case Some(taxCodeIncome) => Some(taxCodeIncome.amount.toString())
        case _ => None
      }
    }
  }

  private def updateTaxCodeAmount(nino: Nino, year: TaxYear, employmentId: Int, version: Int, amount: Int)
                                 (implicit hc: HeaderCarrier): Future[IncomeUpdateResponse] = {
    for {
      updateAmountResult <- taxAccountRepository.updateTaxCodeAmount(nino, year, version, employmentId, NewEstimatedPay.code, amount)
    } yield {
      updateAmountResult match {
        case HodUpdateSuccess => IncomeUpdateSuccess
        case HodUpdateFailure => IncomeUpdateFailed(s"Hod update failed for ${year.year} update")
      }
    }
  }

  def retrieveTaxCodeIncomeAmount(nino: Nino, employmentId: Int, taxCodeIncomes: Seq[TaxCodeIncome]): BigDecimal = {

    val taxCodeIncome = for {
      taxCodeIncome <- taxCodeIncomes.find(_.employmentId.contains(employmentId))
    } yield taxCodeIncome.amount

    taxCodeIncome.getOrElse(0)
  }

  def retrieveEmploymentAmountYearToDate(nino: Nino, employment: Option[Employment]): BigDecimal = {

    val amountYearToDate = for {
      employment <- employment
      latestAnnualAccount <- employment.latestAnnualAccount
      latestPayment <- latestAnnualAccount.latestPayment
    } yield latestPayment.amountYearToDate

    amountYearToDate.getOrElse(0)
  }
}
