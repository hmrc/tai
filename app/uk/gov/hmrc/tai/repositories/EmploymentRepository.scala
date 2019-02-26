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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CacheConnector, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.tai.TaxYear

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

@Singleton
class EmploymentRepository @Inject()(rtiConnector: RtiConnector,
                                     cacheConnector: CacheConnector,
                                     npsConnector: NpsConnector,
                                     auditor: Auditor) {

  private val EmploymentMongoKey = "EmploymentData"

  def employment(nino: Nino, id: Int)(implicit hc: HeaderCarrier): Future[Option[Employment]] = {
    for {
      _ <- employmentsForYear(nino, TaxYear())
      employments <- fetchEmploymentFromCache
    } yield employments.find(_.sequenceNumber == id)
  }

  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {

    fetchEmploymentFromCache.flatMap { allEmployments =>
      allEmployments
        .filter(_.annualAccounts.exists(_.taxYear == year))
        .map(e => e.copy(annualAccounts = e.annualAccounts.filter(_.taxYear == year)))
        match {
          case Nil => employmentsFromHod(nino, year)
          case employmentsForGivenYear => Future.successful(employmentsForGivenYear)
        }
    }
  }

  def checkAndUpdateCache(employments: Seq[Employment])(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    val employmentsWithKnownAccountState = employments.filterNot(_.annualAccounts.map(_.realTimeStatus).contains(TemporarilyUnavailable))
    if (employmentsWithKnownAccountState.nonEmpty) {
      modifyCache(employmentsWithKnownAccountState).map(_ => employments)
    } else {
      Future.successful(employments)
    }
  }

  def modifyCache(employments: Seq[Employment])(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    for {
      currentCacheEmployments <- cacheConnector.findSeq[Employment](fetchSessionId(hc), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
      modifiedEmployments = employments map (amendEmployment(_, currentCacheEmployments))
      unmodifiedEmployments = currentCacheEmployments.filterNot(currentCachedEmployment => modifiedEmployments.map(_.key).contains(currentCachedEmployment.key))
      updateCache = unmodifiedEmployments ++ modifiedEmployments
      cachedEmployments <- cacheConnector.createOrUpdateSeq[Employment](fetchSessionId(hc), updateCache, EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
    } yield cachedEmployments
  }

  def amendEmployment(employment: Employment, currentCacheEmployments: Seq[Employment]): Employment = {
    currentCacheEmployments.find(_.key == employment.key) match {
      case Some(cachedEmployment) => mergedEmployment(cachedEmployment, employment)
      case None => employment
    }
  }

  def mergedEmployment(prevEmp: Employment, newEmp: Employment): Employment = {
    newEmp.copy(annualAccounts = newEmp.annualAccounts ++ prevEmp.annualAccounts)
  }

  def employmentsFromHod(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    implicit val ty = taxYear
    val employmentsFuture: Future[JsValue] = npsConnector.getEmploymentDetails(nino, taxYear.year)
    val accountsFuture: Future[JsValue] = rtiConnector.getRTIDetails(nino, taxYear)

    for {
      employments <- employmentsFuture map {
        _.as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads).employments
      }
      accounts <- accountsFuture map {
        _.as[Seq[AnnualAccount]](EmploymentHodFormatters.annualAccountHodReads)
      } recover {
        case error: HttpException => stubAccounts(error.responseCode, employments, taxYear)
      }
      employmentDomainResult <- checkAndUpdateCache(unifiedEmployments(employments, accounts, nino, taxYear))
    } yield {
      employmentDomainResult
    }
  }

  def stubAccounts(respCode: Int, employments: Seq[Employment], taxYear: TaxYear): Seq[AnnualAccount] = {
    val rtiStatus = respCode match {
      case 404 => Unavailable
      case _ => TemporarilyUnavailable
    }
    employments.map(emp => AnnualAccount(emp.key, taxYear, rtiStatus, Nil, Nil))
  }

  def unifiedEmployments(
    employments: Seq[Employment],
    accounts: Seq[AnnualAccount],
    nino: Nino, taxYear: TaxYear)
    (implicit hc: HeaderCarrier): Seq[Employment] = {

    val accountAssignedEmployments = accounts flatMap { account =>
      associatedEmployment(account, employments, nino, taxYear)
    }
    val unified = combinedDuplicates(accountAssignedEmployments)
    val nonUnified = employments.filterNot(emp => unified.map(_.key).contains(emp.key)) map {
      emp => emp.copy(annualAccounts = Seq(AnnualAccount(emp.key, taxYear, Unavailable, Nil, Nil)))
    }
    unified ++ nonUnified
  }

  def associatedEmployment(account: AnnualAccount,
    employments: Seq[Employment],
    nino: Nino, taxYear: TaxYear)
    (implicit hc: HeaderCarrier): Option[Employment] = {

    employments.filter(emp => emp.employerDesignation == account.employerDesignation) match {
      case Seq(single) =>
        Logger.warn(s"single match found for $nino for $taxYear")
        Some(single.copy(annualAccounts = Seq(account)))
      case Nil =>
        Logger.warn(s"no match found for $nino for $taxYear")
        monitorAndAuditAssociatedEmployment(
        None,
        account,
        employments,
        nino.nino,
        taxYear.twoDigitRange)
      case many =>
        Logger.warn(s"multiple matches found for $nino for $taxYear")
        monitorAndAuditAssociatedEmployment(
        many.find(_.key == account.key).map(_.copy(annualAccounts = Seq(account))),
        account,
        employments,
        nino.nino,
        taxYear.twoDigitRange)
    }
  }

  def monitorAndAuditAssociatedEmployment(emp: Option[Employment],
    account: AnnualAccount,
    employments: Seq[Employment],
    nino: String,
    taxYear: String)
    (implicit hc: HeaderCarrier): Option[Employment] = {
    if (emp.isDefined) {
      emp
    } else {
      val employerKey = employments.map { employment =>
        s"${employment.name} : ${employment.key}; "
      }.mkString

      auditor.sendDataEvent(
        transactionName = "NPS RTI Data Mismatch",
        detail = Map(
          "nino" -> nino,
          "tax year" -> taxYear,
          "NPS Employment Keys" -> employerKey,
          "RTI Account Key" -> account.key))

      Logger.warn("EmploymentRepository: Failed to identify an Employment match for an AnnualAccount instance. NPS and RTI data may not align.")
      None
    }
  }

  def combinedDuplicates(employments: Seq[Employment]): Seq[Employment] = {
    employments.map(_.key).distinct map { distinctKey =>
      val duplicates = employments.filter(_.key == distinctKey)
      duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
    }
  }

  private def fetchEmploymentFromCache(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector.findSeq[Employment](fetchSessionId(hc), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)

  private def fetchSessionId(headerCarrier: HeaderCarrier): String =
    headerCarrier.sessionId
      .map(_.value)
      .getOrElse(throw new RuntimeException("Error while fetching session id"))

}