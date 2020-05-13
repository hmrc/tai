/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound, EmploymentRetrievalError}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class EmploymentRepository @Inject()(
  rtiConnector: RtiConnector,
  cacheConnector: CacheConnector,
  npsConnector: NpsConnector,
  auditor: Auditor) {

  private val EmploymentMongoKey = "EmploymentData"

  def employment(nino: Nino, id: Int)(
    implicit hc: HeaderCarrier): Future[Either[EmploymentRetrievalError, Employment]] = {
    val taxYear = TaxYear()
    employmentsForYear(nino, taxYear) map { empForYear =>
      if (empForYear.exists(_.tempUnavailableStubExistsForYear(taxYear))) {
        Left(EmploymentAccountStubbed)
      } else {
        empForYear.find(_.sequenceNumber == id) match {
          case Some(employment) => Right(employment)
          case None => {
            val sequenceNumbers = empForYear.map(_.sequenceNumber).mkString(", ")
            Logger.warn(s"employment id: $id not found in employment sequence numbers: $sequenceNumbers")
            Left(EmploymentNotFound)
          }
        }
      }
    }
  }

  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    fetchEmploymentFromCache(nino) flatMap {
      case Nil =>
        employmentsFromHod(nino, year) flatMap (employmentsWithAccounts =>
          addEmploymentsToCache(nino, employmentsWithAccounts.employments))
      case (cachedEmployments) =>
        UnifiedEmployments(cachedEmployments).withAccountsForYear(cachedEmployments, year) match {
          case Nil =>
            employmentsFromHod(nino, year) flatMap { employmentsWithAccounts =>
              for {
                currentCacheEmployments <- fetchEmploymentFromCache(nino)
                mergedEmployments = employmentsWithAccounts.mergeEmployments(currentCacheEmployments)
                _ <- addEmploymentsToCache(nino, mergedEmployments.employments)
              } yield employmentsWithAccounts.employments
            }
          case employmentsForYear => employmentsFromCache(UnifiedEmployments(employmentsForYear), nino, year)
        }
    }

  private def employmentsFromCache(employmentsForYear: UnifiedEmployments, nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] = {

    def isCallToRtiRequired(employmentsForYear: UnifiedEmployments): Boolean =
      employmentsForYear.containsTempAccount(taxYear)

    def modifyCache(employmentsWithAccounts: UnifiedEmployments): Future[Seq[Employment]] =
      for {
        currentCacheEmployments <- fetchEmploymentFromCache(nino)
        mergedEmployments = employmentsWithAccounts
          .mergeEmploymentsForTaxYear(currentCacheEmployments, taxYear)
          .employments
        _ <- addEmploymentsToCache(nino, mergedEmployments)
      } yield employmentsWithAccounts.employments

    if (isCallToRtiRequired(employmentsForYear)) {
      rtiCall(nino, taxYear) flatMap {
        case Right(accounts) => {
          val employmentsWithAccounts = NonUnifiedEmployments(employmentsForYear.employments)
            .combineAccountsWithEmployments(accounts, nino, taxYear)

          modifyCache(employmentsWithAccounts) map (_ => employmentsWithAccounts.employments)
        }
        case Left(Unavailable) => {
          val unavailableAccounts = stubAccounts(Unavailable, employmentsForYear.employments, taxYear)
          val employmentsWithAccounts = NonUnifiedEmployments(employmentsForYear.employments)
            .combineAccountsWithEmployments(unavailableAccounts, nino, taxYear)

          modifyCache(employmentsWithAccounts) map (_ => employmentsWithAccounts.employments)
        }
        case Left(TemporarilyUnavailable) => Future.successful(employmentsForYear.employments)
      }
    } else {
      Future.successful(employmentsForYear.employments)
    }
  }

  private def rtiCall(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Either[UnAvailableRealTimeStatus, Seq[AnnualAccount]]] =
    rtiConnector.getPaymentsForYear(nino, taxYear)

  private def employmentsFromHod(nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[UnifiedEmployments] = {
    implicit val ty = taxYear

    def rtiAnnualAccounts(employments: Seq[Employment]): Future[Seq[AnnualAccount]] =
      rtiCall(nino, taxYear) map {
        case Right(accounts) => accounts
        case Left(rtiStatus) => stubAccounts(rtiStatus, employments, taxYear)
      }

    for {
      employments <- npsConnector.getEmploymentDetails(nino, taxYear.year) map {
                      _.as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads).employments
                    }
      accounts <- rtiAnnualAccounts(employments)
    } yield NonUnifiedEmployments(employments).combineAccountsWithEmployments(accounts, nino, taxYear)
  }

  private def stubAccounts(
    rtiStatus: RealTimeStatus,
    employments: Seq[Employment],
    taxYear: TaxYear): Seq[AnnualAccount] =
    employments.map(_.stubbedAccount(rtiStatus, taxYear))

  private def addEmploymentsToCache(nino: Nino, employments: Seq[Employment])(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector.createOrUpdateSeq[Employment](CacheId(nino), employments, EmploymentMongoKey)(
      EmploymentMongoFormatters.formatEmployment)

  private def fetchEmploymentFromCache(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector
      .findSeq[Employment](CacheId(nino), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
}
