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
class EmploymentRepository @Inject()(cacheConnector: CacheConnector, npsConnector: NpsConnector, auditor: Auditor) {

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

  def employmentsForYear(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    fetchEmploymentFromCache(nino).flatMap { allEmployments =>
      allEmployments
        .filterNot(e => e.startDate.isAfter(year.end) || (e.endDate.isDefined && e.endDate.get.isBefore(year.start))) match {
        case Nil                     => employmentsFromHod(nino, year)
        case employmentsForGivenYear => Future.successful(employmentsForGivenYear)
      }
    }

  def checkAndUpdateCache(cacheId: CacheId, employments: Seq[Employment])(
    implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    if (employments.nonEmpty) {
      modifyCache(cacheId, employments).map(_ => employments)
    } else {
      Future.successful(employments)
    }

  def modifyCache(cacheId: CacheId, employments: Seq[Employment]): Future[Seq[Employment]] =
    for {
      cachedEmployments <- cacheConnector.findSeq[Employment](cacheId, EmploymentMongoKey)(
                            EmploymentMongoFormatters.formatEmployment)
      newCachedEmployments <- cacheConnector
                               .createOrUpdateSeq[Employment](
                                 cacheId,
                                 cachedEmployments ++ employments,
                                 EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
    } yield newCachedEmployments

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

  private def fetchEmploymentFromCache(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[Employment]] =
    cacheConnector
      .findSeq[Employment](CacheId(nino), EmploymentMongoKey)(EmploymentMongoFormatters.formatEmployment)
}
