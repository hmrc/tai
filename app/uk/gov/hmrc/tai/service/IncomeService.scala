/*
 * Copyright 2021 HM Revenue & Customs
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
import play.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.domain.{Employment, EmploymentIncome, TaxCodeIncomeComponentType, income}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{IncomeRepository, TaxAccountRepository}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IncomeService @Inject()(
  employmentService: EmploymentService,
  citizenDetailsConnector: CitizenDetailsConnector,
  incomeRepository: IncomeRepository,
  taxAccountRepository: TaxAccountRepository,
  auditor: Auditor)(implicit ec: ExecutionContext) {

  private def filterIncomesByType(taxCodeIncomes: Seq[TaxCodeIncome], incomeType: TaxCodeIncomeComponentType) =
    taxCodeIncomes.filter(income => income.componentType == incomeType)

  def nonMatchingCeasedEmployments(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    def filterNonMatchingCeasedEmploymentsWithEndDate(
      employments: Seq[Employment],
      taxCodeIncomes: Seq[TaxCodeIncome]): Seq[Employment] =
      employments
        .filter(emp => emp.employmentStatus != Live)
        .filter(emp => !taxCodeIncomes.exists(tci => tci.employmentId.contains(emp.sequenceNumber)))
        .filter(_.endDate.isDefined)

    for {
      taxCodeIncomes <- taxCodeIncomes(nino, year)
      filteredTaxCodeIncomes = taxCodeIncomes.filter(income => income.componentType == EmploymentIncome)
      employments <- employmentService.employments(nino, year)
      result = filterNonMatchingCeasedEmploymentsWithEndDate(employments, filteredTaxCodeIncomes)
    } yield result
  }

  def matchedTaxCodeIncomesForYear(
    nino: Nino,
    year: TaxYear,
    incomeType: TaxCodeIncomeComponentType,
    status: TaxCodeIncomeStatus)(implicit hc: HeaderCarrier): Future[Seq[IncomeSource]] = {

    def filterMatchingEmploymentsToIncomeSource(
      employments: Seq[Employment],
      filteredTaxCodeIncomes: Seq[TaxCodeIncome],
      status: TaxCodeIncomeStatus): Seq[IncomeSource] =
      filteredTaxCodeIncomes.flatMap { income =>
        employments
          .filter(emp => if (status == NotLive) emp.employmentStatus != Live else emp.employmentStatus == status)
          .filter(emp => income.employmentId.contains(emp.sequenceNumber))
          .map(IncomeSource(income, _))
      }

    for {
      taxCodeIncomes <- taxCodeIncomes(nino, year)
      filteredTaxCodeIncomes = filterIncomesByType(taxCodeIncomes, incomeType)
      employments <- employments(filteredTaxCodeIncomes, nino, year)
    } yield
      filterMatchingEmploymentsToIncomeSource(employments, filterIncomesByType(taxCodeIncomes, incomeType), status)
  }

  def untaxedInterest(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[income.UntaxedInterest]] =
    incomes(nino, TaxYear()).map(_.nonTaxCodeIncomes.untaxedInterest)

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] =
    incomeRepository.taxCodeIncomes(nino, year)

  def incomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Incomes] =
    incomeRepository.incomes(nino, year)

  def employments(filteredTaxCodeIncomes: Seq[TaxCodeIncome], nino: Nino, year: TaxYear)(
    implicit headerCarrier: HeaderCarrier): Future[Seq[Employment]] =
    if (filteredTaxCodeIncomes.isEmpty) {
      Future.successful(Seq.empty[Employment])
    } else {
      employmentService.employments(nino, year)
    }

  def updateTaxCodeIncome(nino: Nino, year: TaxYear, employmentId: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[IncomeUpdateResponse] = {

    val auditEventForIncomeUpdate: String => Unit = (currentAmount: String) => {
      auditor.sendDataEvent(
        transactionName = "Update Multiple Employments Data",
        detail = Map(
          "nino"          -> nino.value,
          "year"          -> year.toString,
          "employmentId"  -> employmentId.toString,
          "newAmount"     -> amount.toString,
          "currentAmount" -> currentAmount)
      )
    }

    citizenDetailsConnector
      .getEtag(nino)
      .flatMap {
        case Some(version) =>
          for {
            incomeAmount         <- incomeAmountForEmploymentId(nino, year, employmentId)
            incomeUpdateResponse <- updateTaxCodeAmount(nino, year, employmentId, version.etag.toInt, amount)
          } yield {
            if (incomeUpdateResponse == IncomeUpdateSuccess) {
              auditEventForIncomeUpdate(incomeAmount.getOrElse("Unknown"))
            }
            incomeUpdateResponse
          }
        case None => Future.successful(IncomeUpdateFailed("Could not find an ETag"))
      }
      .recover {
        case ex: Exception =>
          Logger.error(s"IncomeService.updateTaxCodeIncome - failed to update income: ${ex.getMessage}")
          IncomeUpdateFailed("Could not parse etag")
      }
  }

  private def incomeAmountForEmploymentId(nino: Nino, year: TaxYear, employmentId: Int)(
    implicit hc: HeaderCarrier): Future[Option[String]] =
    taxCodeIncomes(nino, year) map { taxCodeIncomes =>
      taxCodeIncomes.find(_.employmentId.contains(employmentId)).map(_.amount.toString())
    }

  private def updateTaxCodeAmount(nino: Nino, year: TaxYear, employmentId: Int, version: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[IncomeUpdateResponse] =
    for {
      updateAmountResult <- taxAccountRepository
                             .updateTaxCodeAmount(nino, year, version, employmentId, NewEstimatedPay.code, amount)
    } yield {
      updateAmountResult match {
        case HodUpdateSuccess => IncomeUpdateSuccess
        case HodUpdateFailure => IncomeUpdateFailed(s"Hod update failed for ${year.year} update")
      }
    }
}
