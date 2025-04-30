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

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CitizenDetailsConnector, TaxAccountConnector}
import uk.gov.hmrc.tai.controllers.auth.AuthenticatedRequest
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.*
import uk.gov.hmrc.tai.model.domain.response.*
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.helper.TaxCodeIncomeHelper

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IncomeService @Inject() (
  employmentService: EmploymentService,
  citizenDetailsConnector: CitizenDetailsConnector,
  taxAccountConnector: TaxAccountConnector,
  iabdService: IabdService,
  taxCodeIncomeHelper: TaxCodeIncomeHelper,
  auditor: Auditor
)(implicit ec: ExecutionContext)
    extends Logging {

  def nonMatchingCeasedEmployments(nino: Nino, year: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[Employment]] = {
    def filterNonMatchingCeasedEmploymentsWithEndDate(
      employments: Seq[Employment],
      taxCodeIncomes: Seq[TaxCodeIncome]
    ): Seq[Employment] =
      /* Employment filtering uses data from the Employment Details and Payrolled Benefits API, not the Tax Account Details API.
         The Tax Account Details API retains employment records from the last coding, meaning deleted employments
         remain with their last-known status (e.g., 'live'). In contrast, the Employment Details and Payrolled Benefits API
         does not include deleted employments, ensuring accurate filtering.
       */
      employments
        .filter(emp => emp.employmentStatus != Live)
        .filter(emp => !taxCodeIncomes.exists(tci => tci.employmentId.contains(emp.sequenceNumber)))
        .filter(_.endDate.isDefined)

    for {
      taxCodeIncomes <- EitherT[Future, UpstreamErrorResponse, Seq[TaxCodeIncome]](
                          taxCodeIncomeHelper.fetchTaxCodeIncomes(nino, year).map(Right(_))
                        )
      filteredTaxCodeIncomes = taxCodeIncomes.filter(income => income.componentType == EmploymentIncome)
      employments <- employmentService.employmentsAsEitherT(nino, year)
      result = filterNonMatchingCeasedEmploymentsWithEndDate(employments.employments, filteredTaxCodeIncomes)
    } yield result
  }

  def matchedTaxCodeIncomesForYear(
    nino: Nino,
    year: TaxYear,
    incomeType: TaxCodeIncomeComponentType,
    status: TaxCodeIncomeStatus
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, UpstreamErrorResponse, Seq[IncomeSource]] = {

    def filterMatchingEmploymentsToIncomeSource(
      employments: Seq[Employment],
      filteredTaxCodeIncomes: Seq[TaxCodeIncome],
      status: TaxCodeIncomeStatus
    ): Seq[IncomeSource] =
      filteredTaxCodeIncomes.flatMap { income =>
        employments
          .filter(emp => if (status == NotLive) emp.employmentStatus != Live else emp.employmentStatus == status)
          .filter(emp => income.employmentId.contains(emp.sequenceNumber))
          .map(IncomeSource(income, _))
      }

    for {
      taxCodeIncomes <- EitherT[Future, UpstreamErrorResponse, Seq[TaxCodeIncome]](
                          taxCodeIncomeHelper.fetchTaxCodeIncomes(nino, year).map(Right(_))
                        )
      filteredTaxCodeIncomes = taxCodeIncomes.filter(income => income.componentType == incomeType)
      employments <- employments(filteredTaxCodeIncomes, nino, year)
    } yield filterMatchingEmploymentsToIncomeSource(
      employments.employments,
      filteredTaxCodeIncomes,
      status
    )
  }

  def untaxedInterest(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[income.UntaxedInterest]] =
    incomes(nino, TaxYear()).map(_.nonTaxCodeIncomes.untaxedInterest)

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Seq[TaxCodeIncome]] = {
    lazy val eventualIncomes = taxCodeIncomeHelper.fetchTaxCodeIncomes(nino, year)
    lazy val eventualEmployments = employmentService
      .employmentsAsEitherT(nino, year)
      .leftMap { error =>
        logger.warn(s"EmploymentService.employments - failed to retrieve employments: ${error.getMessage}")
        Employments(Seq.empty[Employment], None)
      }
      .merge

    for {
      employments <- eventualEmployments
      taxCodes    <- eventualIncomes
    } yield {
      val map = employments.employments.groupBy(_.sequenceNumber)
      taxCodes.map { taxCode =>
        val employmentStatus = for {
          id         <- taxCode.employmentId
          employment <- map.get(id).flatMap(_.headOption)
        } yield employment.employmentStatus

        if (employmentStatus.isEmpty) {
          logger.error(
            s"No employment found with id `${taxCode.employmentId} in employment details API for nino `${nino.nino}` and tax year `${year.year}`. See DDCNL-9780"
          )
        }

        taxCode.copy(status = employmentStatus.getOrElse(taxCode.status))
      }
    }
  }

  def incomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Incomes] =
    taxAccountConnector.taxAccount(nino, year).flatMap { jsValue =>
      val nonTaxCodeIncome =
        jsValue.as[Seq[OtherNonTaxCodeIncome]](OtherNonTaxCodeIncomeHipReads.otherNonTaxCodeIncomeReads)
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

  def employments(filteredTaxCodeIncomes: Seq[TaxCodeIncome], nino: Nino, year: TaxYear)(implicit
    headerCarrier: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Employments] =
    if (filteredTaxCodeIncomes.isEmpty) {
      EitherT.rightT[Future, UpstreamErrorResponse](Employments(Seq.empty[Employment], None))
    } else {
      employmentService.employmentsAsEitherT(nino, year)
    }

  def updateTaxCodeIncome(nino: Nino, year: TaxYear, employmentId: Int, amount: Int)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ): Future[IncomeUpdateResponse] = {

    val auditEventForIncomeUpdate: String => Unit = (currentAmount: String) =>
      auditor.sendDataEvent(
        transactionName = "Update Multiple Employments Data",
        detail = Map(
          "nino"          -> nino.value,
          "year"          -> year.toString,
          "employmentId"  -> employmentId.toString,
          "newAmount"     -> amount.toString,
          "currentAmount" -> currentAmount
        )
      )

    // TODO: incorrect use of an etag. A fresh etag should not be use at point of submission.
    citizenDetailsConnector
      .getEtag(nino)
      .flatMap {
        case Some(version) =>
          for {
            incomeAmount <- taxCodeIncomeHelper.incomeAmountForEmploymentId(nino, year, employmentId)
            incomeUpdateResponse <-
              iabdService.updateTaxCodeAmount(nino, year, employmentId, version.etag.toInt, amount)
          } yield {
            if (incomeUpdateResponse == IncomeUpdateSuccess) {
              auditEventForIncomeUpdate(incomeAmount.getOrElse("Unknown"))
            }
            incomeUpdateResponse
          }
        case None => Future.successful(IncomeUpdateFailed("Could not find an ETag"))
      }
      .recover { case ex: Exception =>
        ex.printStackTrace()
        logger.error(s"IncomeService.updateTaxCodeIncome - failed to update income: ${ex.getMessage}")
        IncomeUpdateFailed("Could not parse etag")
      }
  }
}
