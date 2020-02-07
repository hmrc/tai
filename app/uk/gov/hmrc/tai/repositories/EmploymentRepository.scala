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
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, NpsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.{EmploymentHodFormatters, EmploymentMongoFormatters}
import uk.gov.hmrc.tai.model.tai.TaxYear
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound, EmploymentRetrievalError}

import scala.concurrent.Future

@Singleton
class EmploymentRepository @Inject()(
  rtiConnector: RtiConnector,
  cacheConnector: CacheConnector,
  npsConnector: NpsConnector,
  auditor: Auditor) {

  private val EmploymentMongoKey = "EmploymentData"

  def employment(nino: Nino, id: Int)(
    implicit hc: HeaderCarrier): Future[Either[EmploymentRetrievalError, Employment]] =
    employmentsForYear(nino, TaxYear()) flatMap { empForYear =>
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

  //TODO: Ensure this has tests, if not, add them!
  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    fetchEmploymentFromCache(nino).flatMap { allEmployments =>
      allEmployments.filter(
        e =>
          e.startDate.isAfter(year.start) &&
            ((e.endDate.isDefined && year.start.isBefore(e.endDate.get)) || e.endDate.isEmpty)) match {
        case Nil                     => employmentsFromHod(nino, year)
        case employmentsForGivenYear => Future.successful(employmentsForGivenYear)
      }
    }

  //TODO: Do we care about the realTimeStatus of the accounts?
  def checkAndUpdateCache(cacheId: CacheId, employments: Seq[Employment])(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] =
//    val employmentsWithKnownAccountState =
//      employments.filterNot(_.annualAccounts.map(_.realTimeStatus).contains(TemporarilyUnavailable))
//    if (employmentsWithKnownAccountState.nonEmpty) {
//      modifyCache(cacheId, employmentsWithKnownAccountState).map(_ => employments)
//    } else {
    Future.successful(employments)
//    }

  def modifyCache(cacheId: CacheId, employments: Seq[Employment]): Future[Seq[Employment]] =
    for {
      cachedEmployments <- cacheConnector
                            .createOrUpdateSeq[Employment](cacheId, employments, EmploymentMongoKey)(
                              EmploymentMongoFormatters.formatEmployment)
    } yield cachedEmployments

  def employmentsFromHod(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = {
    val employmentsFuture: Future[JsValue] = npsConnector.getEmploymentDetails(nino, taxYear.year)

    for {
      employments <- employmentsFuture map {
                      _.as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads).employments
                    }
      employmentDomainResult <- checkAndUpdateCache(CacheId(nino), employments)
    } yield {
      employmentDomainResult
    }
  }

  //TODO: Ensure that new binding logic mimics this behavour and then remove properly.
//  def monitorAndAuditAssociatedEmployment(
//    emp: Option[Employment],
//    account: AnnualAccount,
//    employments: Seq[Employment],
//    nino: String,
//    taxYear: String)(implicit hc: HeaderCarrier): Option[Employment] =
//    if (emp.isDefined) {
//      emp
//    } else {
//      val employerKey = employments.map { employment =>
//        s"${employment.name} : ${employment.key}; "
//      }.mkString
//
//      auditor.sendDataEvent(
//        transactionName = "NPS RTI Data Mismatch",
//        detail = Map(
//          "nino"                -> nino,
//          "tax year"            -> taxYear,
//          "NPS Employment Keys" -> employerKey,
//          "RTI Account Key"     -> account.key)
//      )
//
//      Logger.warn(
//        "EmploymentRepository: Failed to identify an Employment match for an AnnualAccount instance. NPS and RTI data may not align.")
//      None
//    }

  private def fetchEmploymentFromCache(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector
      .findSeq[Employment](CacheId(nino), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
}
