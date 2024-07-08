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
import uk.gov.hmrc.tai.connectors.{CitizenDetailsConnector, IabdConnector, TaxAccountConnector}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.model.domain.formatters.income.{TaxAccountIncomeHodFormatters, TaxCodeIncomeHodFormatters}
import uk.gov.hmrc.tai.model.domain.UntaxedInterestIncome
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.income._
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.domain.{Employment, EmploymentIncome, Employments, TaxCodeIncomeComponentType, income}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IncomeService @Inject() (
  employmentService: EmploymentService,
  citizenDetailsConnector: CitizenDetailsConnector,
  taxAccountConnector: TaxAccountConnector,
  iabdConnector: IabdConnector,
  iabdService: IabdService,
  auditor: Auditor
)(implicit ec: ExecutionContext)
    extends Logging with TaxAccountIncomeHodFormatters with TaxCodeIncomeHodFormatters {

  private def filterIncomesByType(taxCodeIncomes: Seq[TaxCodeIncome], incomeType: TaxCodeIncomeComponentType) =
    taxCodeIncomes.filter(income => income.componentType == incomeType)

  private def fetchTaxCodeIncomes(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeIncome]] = {
    lazy val taxCodeIncomeFuture = taxAccountConnector
      .taxAccount(nino, year)
      .map(_.as[Seq[TaxCodeIncome]](taxCodeIncomeSourcesReads))
    lazy val iabdDetailsFuture = iabdService.retrieveIabdDetails(nino, year)

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

  private def incomeAmountForEmploymentId(nino: Nino, year: TaxYear, employmentId: Int)(implicit
    hc: HeaderCarrier
  ): Future[Option[String]] =
    fetchTaxCodeIncomes(nino, year) map { taxCodeIncomes =>
      taxCodeIncomes.find(_.employmentId.contains(employmentId)).map(_.amount.toString())
    }

  private def updateTaxCodeAmount(nino: Nino, year: TaxYear, employmentId: Int, version: Int, amount: Int)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ): Future[IncomeUpdateResponse] =
    for {
      updateAmountResult <-
        iabdConnector.updateTaxCodeAmount(nino, year, employmentId, version, NewEstimatedPay.code, amount)
    } yield updateAmountResult match {
      case HodUpdateSuccess => IncomeUpdateSuccess
      case HodUpdateFailure => IncomeUpdateFailed(s"Hod update failed for ${year.year} update")
    }

  def nonMatchingCeasedEmployments(nino: Nino, year: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Seq[Employment]] = {
    def filterNonMatchingCeasedEmploymentsWithEndDate(
      employments: Seq[Employment],
      taxCodeIncomes: Seq[TaxCodeIncome]
    ): Seq[Employment] =
      employments
        .filter(emp => emp.employmentStatus != Live)
        .filter(emp => !taxCodeIncomes.exists(tci => tci.employmentId.contains(emp.sequenceNumber)))
        .filter(_.endDate.isDefined)

    for {
      taxCodeIncomes <- EitherT[Future, UpstreamErrorResponse, Seq[TaxCodeIncome]](
                          fetchTaxCodeIncomes(nino, year).map(Right(_))
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
                          fetchTaxCodeIncomes(nino, year).map(Right(_))
                        )
      filteredTaxCodeIncomes = filterIncomesByType(taxCodeIncomes, incomeType)
      employments <- employments(filteredTaxCodeIncomes, nino, year)
    } yield filterMatchingEmploymentsToIncomeSource(
      employments.employments,
      filterIncomesByType(taxCodeIncomes, incomeType),
      status
    )
  }

  def untaxedInterest(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[income.UntaxedInterest]] =
    incomes(nino, TaxYear()).map(_.nonTaxCodeIncomes.untaxedInterest)

  def taxCodeIncomes(nino: Nino, year: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[Seq[TaxCodeIncome]] = {
    lazy val eventualIncomes = fetchTaxCodeIncomes(nino, year)
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

        taxCode.copy(status = employmentStatus.getOrElse(taxCode.status))
      }
    }
  }

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
      .recover { case ex: Exception =>
        logger.error(s"IncomeService.updateTaxCodeIncome - failed to update income: ${ex.getMessage}")
        IncomeUpdateFailed("Could not parse etag")
      }
  }
}
