/*
 * Copyright 2018 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import org.joda.time.LocalDate
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsResultException
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeChangeRecord}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{AnnualCode, NonAnnualCode, TaxCodeRecord}
import uk.gov.hmrc.tai.util.TaxCodeRecordConstants
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future

class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector) extends TaxCodeChangeService with
  TaxCodeRecordConstants {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean] = {
    val currentYear = TaxYear()

    taxCodeChangeConnector.taxCodeHistory(nino, currentYear) map { taxCodeHistory =>
      sortedByDate(taxCodeHistory.operatedTaxCodeRecords) match {
        case Seq(TaxCodeRecord(_,_,_,_,NonAnnualCode),TaxCodeRecord(_,_,_,_,AnnualCode),_*) => true
        case Seq(TaxCodeRecord(_,_,_,_,NonAnnualCode),TaxCodeRecord(_,_,_,_,NonAnnualCode),_*) => true
        case _ => false
      }
    }recover {
      case exception:JsResultException =>
        Logger.warn(s"Failed to retrieve TaxCodeRecord for $nino with exception:${exception.getMessage}")
        false
      case ex => throw ex
    }
  }

  def taxCodeChange(nino: Nino): Future[TaxCodeChange] = {

    taxCodeChangeConnector.taxCodeHistory(nino, TaxYear()) map { taxCodeHistory =>
      val currentRecord :: previousRecord :: _ = sortedByDate(taxCodeHistory.operatedTaxCodeRecords)

      val previousEndDate = currentRecord.dateOfCalculation.minusDays(1)

      val currentTaxCodeChange = TaxCodeChangeRecord(currentRecord.taxCode,
                                                     currentRecord.dateOfCalculation,
                                                     TaxYearResolver.endOfCurrentTaxYear,
                                                     currentRecord.employerName)

      val previousTaxCodeChange = TaxCodeChangeRecord(previousRecord.taxCode,
                                                      previousStartDate(previousRecord.dateOfCalculation),
                                                      previousEndDate,
                                                      previousRecord.employerName)

      TaxCodeChange(currentTaxCodeChange, previousTaxCodeChange)
    }
  }

  private val sortedByDate = (records: Seq[TaxCodeRecord]) => {
    implicit val dateTimeOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isAfter _)

    records.sortBy(_.dateOfCalculation)
  }

  private def previousStartDate(date: LocalDate): LocalDate = {
    if (date isBefore TaxYearResolver.startOfCurrentTaxYear) {
      TaxYearResolver.startOfCurrentTaxYear
    } else {
      date
    }
  }
}

@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean]

  def taxCodeChange(nino: Nino): Future[TaxCodeChange]

}
