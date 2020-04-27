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

  //TODO can this be tidied
  def employment(nino: Nino, id: Int)(
    implicit hc: HeaderCarrier): Future[Either[EmploymentRetrievalError, Employment]] =
    employmentsForYear(nino, TaxYear()) flatMap { empForYear =>
      if (empForYear.exists(_.hasTempUnavailableStubAccount)) {
        Future.successful(Left(EmploymentAccountStubbed))
      } else {
        //TODO use the employments above?
        fetchEmploymentFromCache(nino) map { emp =>
          emp.find(_.sequenceNumber == id) match {
            case Some(employment) => Right(employment)
            case None => {
              val sequenceNumbers = emp.map(_.sequenceNumber).mkString(", ")
              Logger.warn(s"employment id: $id not found in employment sequence numbers: $sequenceNumbers")
              Left(EmploymentNotFound)
            }
          }
        }
      }
    }

  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    fetchEmploymentFromCache(nino) flatMap {
      case Nil => employmentsFromHod(nino, year) flatMap (addEmploymentsToCache(CacheId(nino), _))
      case (employments) =>
        onlyAccountsForGivenYear(employments, year) match {
          case Nil                => employmentsFromHod(nino, year) flatMap (modifyCache(CacheId(nino), _))
          case employmentsForYear => employmentsFromCache(employmentsForYear, nino, year)
        }
    }

  private def employmentsFromCache(cachedEmployments: Seq[Employment], nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    if (isCallToRtiRequired(cachedEmployments)) {
      subsequentRTICall(nino, taxYear, cachedEmployments) flatMap {
        case Right(accounts) =>
          modifyCache(CacheId(nino), unifiedEmployments(cachedEmployments, accounts, nino, taxYear))
        case Left(_) => Future.successful(cachedEmployments)
      }
    } else {
      Future.successful(cachedEmployments)
    }

  private def onlyAccountsForGivenYear(employments: Seq[Employment], year: TaxYear): Seq[Employment] =
    employments.collect {
      case employment if employment.hasAnnualAccountsForYear(year) =>
        employment.copy(annualAccounts = employment.annualAccountsForYear(year))
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

  private def isCallToRtiRequired(cachedEmployments: Seq[Employment]): Boolean =
    cachedEmployments.exists(_.hasTempUnavailableStubAccount) && featureToggle.rtiEnabled

  private def subsequentRTICall(nino: Nino, taxYear: TaxYear, employmentsWithStub: Seq[Employment])(
    implicit hc: HeaderCarrier): Future[Either[String, Seq[AnnualAccount]]] =
    rtiCall(nino, taxYear).map(Right(_)) recover {
      case error: HttpException => {
        error.responseCode match {
          case 404 => Right(stubAccounts(Unavailable, employmentsWithStub, taxYear))
          case _   => Left("No update required")
        }
      }
    }

  //TODO recover
  def addEmploymentsToCache(cacheId: CacheId, employments: Seq[Employment]): Future[Seq[Employment]] =
    cacheConnector.createOrUpdateSeq[Employment](cacheId, employments, EmploymentMongoKey)(
      EmploymentMongoFormatters.formatEmployment)

  private def modifyCache(cacheId: CacheId, employments: Seq[Employment]): Future[Seq[Employment]] =
    for {
      currentCacheEmployments <- cacheConnector.findSeq[Employment](cacheId, EmploymentMongoKey)(
                                  EmploymentMongoFormatters.formatEmployment)
      cachedEmploymentWithoutStubs = currentCacheEmployments.filterNot(_.hasTempUnavailableStubAccount)
      modifiedEmployments = employments map (amendEmployment(_, cachedEmploymentWithoutStubs))
      unmodifiedEmployments = cachedEmploymentWithoutStubs.filterNot(currentCachedEmployment =>
        modifiedEmployments.map(_.key).contains(currentCachedEmployment.key))
      updateCache = unmodifiedEmployments ++ modifiedEmployments
      _ <- cacheConnector.createOrUpdateSeq[Employment](cacheId, updateCache, EmploymentMongoKey)(
            EmploymentMongoFormatters.formatEmployment)
    } yield employments

  def amendEmployment(employment: Employment, currentCacheEmployments: Seq[Employment]): Employment =
    currentCacheEmployments.find(_.key == employment.key) match {
      case Some(cachedEmployment) => mergedEmployment(cachedEmployment, employment)
      case None                   => employment
    }

  def mergedEmployment(prevEmp: Employment, newEmp: Employment): Employment =
    newEmp.copy(annualAccounts = newEmp.annualAccounts ++ prevEmp.annualAccounts)

  def unifiedEmployments(employments: Seq[Employment], accounts: Seq[AnnualAccount], nino: Nino, taxYear: TaxYear)(
    implicit hc: HeaderCarrier): Seq[Employment] = {
    val accountAssignedEmployments = accounts flatMap { account =>
      associatedEmployment(account, employments, nino, taxYear)
    }
    val unified = combinedDuplicates(accountAssignedEmployments)
    val nonUnified = employments.filterNot(emp => unified.map(_.key).contains(emp.key)) map { emp =>
      emp.copy(annualAccounts = Seq(AnnualAccount(emp.key, taxYear, Unavailable, Nil, Nil)))
    }
    unified ++ nonUnified
  }

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

  def combinedDuplicates(employments: Seq[Employment]): Seq[Employment] =
    employments.map(_.key).distinct map { distinctKey =>
      val duplicates = employments.filter(_.key == distinctKey)
      duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
    }

  private def fetchEmploymentFromCache(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector
      .findSeq[Employment](CacheId(nino), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
}
