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
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
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
  auditor: Auditor,
  featureToggle: FeatureTogglesConfig) {

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

  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {

    def onlyAccountsForGivenYear(employments: Seq[Employment], year: TaxYear): Seq[Employment] =
      employments.collect {
        case employment if employment.hasAnnualAccountsForYear(year) =>
          employment.copy(annualAccounts = employment.annualAccountsForYear(year))
      }

    fetchEmploymentFromCache(nino) flatMap {
      case Nil => employmentsFromHod(nino, year) flatMap (addEmploymentsToCache(CacheId(nino), _))
      case (employments) =>
        onlyAccountsForGivenYear(employments, year) match {
          case Nil =>
            employmentsFromHod(nino, year) flatMap { unifiedEmployments =>
              modifyCache(CacheId(nino), unifiedEmployments).map(_ => unifiedEmployments)
            }
          case employmentsForYear => employmentsFromCache(employmentsForYear, nino, year)
        }
    }
  }

  private def employmentsFromCache(employmentsForYear: Seq[Employment], nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] = {

    def isCallToRtiRequired(employmentsForYear: Seq[Employment]): Boolean =
      employmentsForYear.exists(_.tempUnavailableStubExistsForYear(taxYear)) && featureToggle.rtiEnabled

    def subsequentRTICall(nino: Nino, taxYear: TaxYear, employmentsWithStub: Seq[Employment])(
      implicit hc: HeaderCarrier): Future[Either[String, Seq[AnnualAccount]]] =
      rtiCall(nino, taxYear).map(Right(_)) recover {
        case error: HttpException => {
          error.responseCode match {
            case 404 => Right(stubAccounts(Unavailable, employmentsWithStub, taxYear))
            case _   => Left("No update required")
          }
        }
      }

    if (isCallToRtiRequired(employmentsForYear)) {
      subsequentRTICall(nino, taxYear, employmentsForYear) flatMap {
        case Right(accounts) =>
          val unifiedEmps = unifiedEmployments(employmentsForYear, accounts, nino, taxYear)
          modifyCache(CacheId(nino), unifiedEmps, taxYear).map(_ => unifiedEmps)
        case Left(_) => Future.successful(employmentsForYear)
      }
    } else {
      Future.successful(employmentsForYear)
    }
  }

  private def rtiCall(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier) =
    rtiConnector
      .getRTIDetails(nino, taxYear) map (_.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads))

  private def employmentsFromHod(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    implicit val ty = taxYear

    def rtiAnnualAccounts(employments: Seq[Employment]): Future[Seq[AnnualAccount]] =
      if (featureToggle.rtiEnabled) {
        rtiCall(nino, taxYear) recover {
          case error: HttpException => {
            val rtiStatus = error.responseCode match {
              case 404 => Unavailable
              case _   => TemporarilyUnavailable
            }

            stubAccounts(rtiStatus, employments, taxYear)
          }
        }
      } else {
        Future.successful(stubAccounts(TemporarilyUnavailable, employments, taxYear))
      }

    for {
      employments <- npsConnector.getEmploymentDetails(nino, taxYear.year) map {
                      _.as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads).employments
                    }
      accounts <- rtiAnnualAccounts(employments)
    } yield unifiedEmployments(employments, accounts, nino, taxYear)
  }

  private def stubAccounts(
    rtiStatus: RealTimeStatus,
    employments: Seq[Employment],
    taxYear: TaxYear): Seq[AnnualAccount] =
    employments.map(_.stubbedAccount(rtiStatus, taxYear))

  private def addEmploymentsToCache(cacheId: CacheId, employments: Seq[Employment]): Future[Seq[Employment]] =
    cacheConnector.createOrUpdateSeq[Employment](cacheId, employments, EmploymentMongoKey)(
      EmploymentMongoFormatters.formatEmployment)

  private def modifyCache(cacheId: CacheId, employments: Seq[Employment]) = {

    def mergeEmployment(prevEmp: Employment, newEmp: Employment): Employment =
      newEmp.copy(annualAccounts = newEmp.annualAccounts ++ prevEmp.annualAccounts)

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, currentCacheEmployments) =>
      currentCacheEmployments.find(_.key == employment.key) match {
        case Some(cachedEmployment) => mergeEmployment(cachedEmployment, employment)
        case None                   => employment
    }

    mergeEmployments(cacheId, employments, amendEmployment)
  }

  private def modifyCache(cacheId: CacheId, employments: Seq[Employment], taxYear: TaxYear): Future[Seq[Employment]] = {

    def mergeEmployment(prevEmp: Employment, newEmp: Employment, taxYear: TaxYear): Employment = {
      val accountsFromOtherYears = prevEmp.annualAccounts.filterNot(_.taxYear == taxYear)
      newEmp.copy(annualAccounts = newEmp.annualAccounts ++ accountsFromOtherYears)
    }

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, currentCacheEmployments) =>
      currentCacheEmployments.find(_.key == employment.key) match {
        case Some(cachedEmployment) => mergeEmployment(cachedEmployment, employment, taxYear)
        case None                   => employment
    }

    mergeEmployments(cacheId, employments, amendEmployment)
  }

  private def mergeEmployments(
    cacheId: CacheId,
    employments: Seq[Employment],
    amendEmployment: (Employment, Seq[Employment]) => Employment): Future[Seq[Employment]] =
    for {
      currentCacheEmployments <- cacheConnector.findSeq[Employment](cacheId, EmploymentMongoKey)(
                                  EmploymentMongoFormatters.formatEmployment)
      modifiedEmployments = employments map (amendEmployment(_, currentCacheEmployments))
      unmodifiedEmployments = currentCacheEmployments.filterNot(currentCachedEmployment =>
        modifiedEmployments.map(_.key).contains(currentCachedEmployment.key))
      updateCache = unmodifiedEmployments ++ modifiedEmployments
      cachedEmployments <- cacheConnector.createOrUpdateSeq[Employment](cacheId, updateCache, EmploymentMongoKey)(
                            EmploymentMongoFormatters.formatEmployment)
    } yield cachedEmployments

  def unifiedEmployments(employments: Seq[Employment], accounts: Seq[AnnualAccount], nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Seq[Employment] = {

    def associatedEmployment(account: AnnualAccount, employments: Seq[Employment], nino: Nino, taxYear: TaxYear)(
      implicit hc: HeaderCarrier): Option[Employment] =
      employments.filter(emp => emp.employerDesignation == account.employerDesignation) match {
        case Seq(single) =>
          Logger.warn(s"single match found for $nino for $taxYear")
          Some(single.copy(annualAccounts = Seq(account)))
        case Nil =>
          Logger.warn(s"no match found for $nino for $taxYear")
          monitorAndAuditAssociatedEmployment(None, account, employments, nino.nino, taxYear.twoDigitRange)
        case many =>
          Logger.warn(s"multiple matches found for $nino for $taxYear")
          monitorAndAuditAssociatedEmployment(
            many.find(_.key == account.key).map(_.copy(annualAccounts = Seq(account))),
            account,
            employments,
            nino.nino,
            taxYear.twoDigitRange)
      }

    def combinedDuplicates(employments: Seq[Employment]): Seq[Employment] =
      employments.map(_.key).distinct map { distinctKey =>
        val duplicates = employments.filter(_.key == distinctKey)
        duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
      }

    val accountAssignedEmployments = accounts flatMap { account =>
      associatedEmployment(account, employments, nino, taxYear)
    }
    val unified = combinedDuplicates(accountAssignedEmployments)
    val nonUnified = employments.filterNot(emp => unified.map(_.key).contains(emp.key)) map { emp =>
      emp.copy(annualAccounts = Seq(AnnualAccount(emp.key, taxYear, Unavailable, Nil, Nil)))
    }
    unified ++ nonUnified
  }

  def monitorAndAuditAssociatedEmployment(
    emp: Option[Employment],
    account: AnnualAccount,
    employments: Seq[Employment],
    nino: String,
    taxYear: String)(implicit hc: HeaderCarrier): Option[Employment] =
    if (emp.isDefined) {
      emp
    } else {
      val employerKey = employments.map { employment =>
        s"${employment.name} : ${employment.key}; "
      }.mkString

      auditor.sendDataEvent(
        transactionName = "NPS RTI Data Mismatch",
        detail = Map(
          "nino"                -> nino,
          "tax year"            -> taxYear,
          "NPS Employment Keys" -> employerKey,
          "RTI Account Key"     -> account.key)
      )

      Logger.warn(
        "EmploymentRepository: Failed to identify an Employment match for an AnnualAccount instance. NPS and RTI data may not align.")
      None
    }

  private def fetchEmploymentFromCache(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector
      .findSeq[Employment](CacheId(nino), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
}
