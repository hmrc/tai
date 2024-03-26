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
import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.RtiConnector
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.connectors.deprecated.NpsConnector
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.error.{EmploymentNotFound, EmploymentRetrievalError}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmploymentRepository @Inject()(
                                      rtiConnector: RtiConnector,
                                      taiCacheRepository: TaiCacheRepository,
                                      npsConnector: NpsConnector,
                                      employmentBuilder: EmploymentBuilder)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private def employmentMongoKey(taxYear: TaxYear) = s"EmploymentData-${taxYear.year}"

  def employment(nino: Nino, id: Int)(
    implicit hc: HeaderCarrier, request: Request[_]): Future[Either[EmploymentRetrievalError, Employment]] = {
    val taxYear = TaxYear()
    employmentsForYear(nino, taxYear) map { employments =>
      employments.employmentById(id) match {
        case Some(employment) => Right(employment)
        case None => {
          val sequenceNumbers = employments.sequenceNumbers.mkString(", ")
          logger.warn(s"employment id: $id not found in employment sequence numbers: $sequenceNumbers")
          Left(EmploymentNotFound)
        }
      }
    }
  }

  def employmentsForYear(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier, request: Request[_]): Future[Employments] =
    fetchEmploymentFromCache(nino, taxYear) flatMap {
      case Employments(Nil) => hodCallWithCaching(nino, taxYear)
      case cachedEmployments @ Employments(_) =>
        cachedEmployments.accountsForYear(taxYear) match {
          case Employments(Nil) =>
            hodCallWithCacheMerge(nino, taxYear, cachedEmployments)
          case employmentsForYear => {
            if (isCallToRtiRequired(taxYear, employmentsForYear)) {
              rtiCallWithCacheUpdate(nino, taxYear, employmentsForYear, cachedEmployments)
            } else {
              Future.successful(employmentsForYear)
            }
          }
        }
    }

  private def hodCallWithCacheMerge(nino: Nino, taxYear: TaxYear, cachedEmployments: Employments)(
    implicit hc: HeaderCarrier, request: Request[_]): Future[Employments] =
    employmentsFromHod(nino, taxYear) flatMap { employmentsWithAccounts =>
      val mergedEmployments = cachedEmployments.mergeEmployments(employmentsWithAccounts.employments)
      addEmploymentsToCache(nino, mergedEmployments, taxYear).map(_ => employmentsWithAccounts)
    }

  private def hodCallWithCaching(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier, request: Request[_]): Future[Employments] =
    employmentsFromHod(nino, taxYear) flatMap (employmentsWithAccounts =>
      addEmploymentsToCache(nino, employmentsWithAccounts.employments, taxYear).map(_ => employmentsWithAccounts))

  private def rtiCallWithCacheUpdate(
    nino: Nino,
    taxYear: TaxYear,
    originalEmployments: Employments,
    cachedEmployments: Employments)(implicit hc: HeaderCarrier, request: Request[_]): Future[Employments] = {

    def rtiCallCombinedWithEmployments(
      originalEmployments: Employments,
      nino: Nino,
      taxYear: TaxYear)(implicit request: Request[_]): Future[Employments] =
      rtiCall(nino, taxYear) map {
        case Right(accounts: Seq[AnnualAccount]) if accounts.isEmpty => {
          val unavailableAccounts = stubAccounts(Unavailable, originalEmployments.employments, taxYear)
          employmentBuilder
            .combineAccountsWithEmployments(originalEmployments.employments, unavailableAccounts, nino, taxYear)
        }
        case Right(accounts: Seq[AnnualAccount]) =>
          employmentBuilder.combineAccountsWithEmployments(originalEmployments.employments, accounts, nino, taxYear)
        case Left(_) => originalEmployments
      }

    rtiCallCombinedWithEmployments(originalEmployments, nino, taxYear) flatMap { rtiUpdatedEmployments =>
      if (rtiUpdatedEmployments != originalEmployments) {
        val mergedEmployments =
          cachedEmployments.mergeEmploymentsForTaxYear(rtiUpdatedEmployments.employments, taxYear)
        addEmploymentsToCache(nino, mergedEmployments, taxYear).map(_ => rtiUpdatedEmployments)
      } else {
        Future.successful(originalEmployments)
      }
    }
  }

  private def isCallToRtiRequired(taxYear: TaxYear, employmentsForYear: Employments): Boolean =
    employmentsForYear.containsTempAccount(taxYear)

  private def rtiCall(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier, request: Request[_]): Future[Either[RtiPaymentsForYearError, Seq[AnnualAccount]]] =
    rtiConnector.getPaymentsForYear(nino, taxYear).value

  private def employmentsFromHod(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier, request: Request[_]): Future[Employments] = {

    def rtiAnnualAccounts(employments: Seq[Employment])(implicit request: Request[_]): Future[Seq[AnnualAccount]] =
      rtiCall(nino, taxYear) map {
        case Right(accounts) if accounts.isEmpty => stubAccounts(Unavailable, employments, taxYear)
        case Right(accounts) => accounts
        case Left(_) => stubAccounts(TemporarilyUnavailable, employments, taxYear)
      }

    for {
      employments <- npsConnector.getEmploymentDetails(nino, taxYear.year) map {
                      _.as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads).employments
                    }
      accounts <- rtiAnnualAccounts(employments)
    } yield employmentBuilder.combineAccountsWithEmployments(employments, accounts, nino, taxYear)
  }

  private def stubAccounts(
    rtiStatus: RealTimeStatus,
    employments: Seq[Employment],
    taxYear: TaxYear): Seq[AnnualAccount] =
    employments map (employment => AnnualAccount(employment.sequenceNumber, taxYear, rtiStatus))

  private def addEmploymentsToCache(nino: Nino, employments: Seq[Employment], taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    taiCacheRepository.createOrUpdateSeq[Employment](CacheId(nino), employments, employmentMongoKey(taxYear))(
      EmploymentMongoFormatters.formatEmployment)

  private def fetchEmploymentFromCache(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Employments] =
    taiCacheRepository
      .findSeq[Employment](CacheId(nino), employmentMongoKey(taxYear))(EmploymentMongoFormatters.formatEmployment) map (Employments(
      _))
}