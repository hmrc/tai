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
import uk.gov.hmrc.tai.model.TaxCodeRecord
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeChangeRecord}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeRecordConstants
import uk.gov.hmrc.time.TaxYearResolver
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering

import scala.concurrent.Future

class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector) extends TaxCodeChangeService with
  TaxCodeRecordConstants {

  private def validForService(taxCodeRecords: Seq[TaxCodeRecord]): Boolean = {
    val calculationDates = taxCodeRecords.map(_.dateOfCalculation).distinct
    lazy val latestDate = calculationDates.min

    calculationDates.length >= 2 && TaxYearResolver.fallsInThisTaxYear(latestDate)
  }

  def hasTaxCodeChanged(nino: Nino): Future[Boolean] = {
    val currentYear = TaxYear()

    taxCodeChangeConnector.taxCodeHistory(nino, currentYear) map { taxCodeHistory =>

      validForService(taxCodeHistory.operatedTaxCodeRecords)

    } recover {
      case exception:JsResultException =>
        Logger.warn(s"Failed to retrieve TaxCodeRecord for $nino with exception:${exception.getMessage}")
        false
      case ex => throw ex
    }
  }

  def taxCodeChange(nino: Nino): Future[TaxCodeChange] = {

    taxCodeChangeConnector.taxCodeHistory(nino, TaxYear()) map { taxCodeHistory =>

      if (validForService(taxCodeHistory.operatedTaxCodeRecords)) {

        val recordsGroupedByDate: Map[LocalDate, Seq[TaxCodeRecord]] = taxCodeHistory.operatedTaxCodeRecords.groupBy(_.dateOfCalculation)

        val currentDate :: previousDate :: _ = recordsGroupedByDate.keys.toList.sorted

        val currentRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(currentDate)

        val previousRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(previousDate)

        val previousEndDate = currentRecords.head.dateOfCalculation.minusDays(1)

        val currentTaxCodeChanges: Seq[TaxCodeChangeRecord] = currentRecords.map( currentRecord =>  TaxCodeChangeRecord(currentRecord.taxCode,
                                                        currentRecord.dateOfCalculation,
                                                        TaxYearResolver.endOfCurrentTaxYear,
                                                        currentRecord.employerName,
                                                        currentRecord.payrollNumber,
                                                        currentRecord.employmentId,
                                                        primaryEmployment(currentRecord)))

        val previousTaxCodeChanges: Seq[TaxCodeChangeRecord] = previousRecords.map( previousRecord =>  TaxCodeChangeRecord(previousRecord.taxCode,
                                                        previousStartDate(previousRecord.dateOfCalculation),
                                                        previousEndDate,
                                                        previousRecord.employerName,
                                                        previousRecord.payrollNumber,
                                                        previousRecord.employmentId,
                                                        primaryEmployment(previousRecord)))

        TaxCodeChange(currentTaxCodeChanges, previousTaxCodeChanges)

      } else {
        TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])
      }
    }
  }

  private def previousStartDate(date: LocalDate): LocalDate = {
    if (date isBefore TaxYearResolver.startOfCurrentTaxYear) {
      TaxYearResolver.startOfCurrentTaxYear
    } else {
      date
    }
  }

  private def primaryEmployment(taxCodeRecord: TaxCodeRecord): Boolean = {
    taxCodeRecord.employmentType == "PRIMARY"
  }
}

@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean]

  def taxCodeChange(nino: Nino): Future[TaxCodeChange]

}

//    val testForAChange = (taxCodes: Seq[TaxCodeRecord]) => taxCodes match {
//      case Seq(TaxCodeRecord(_,_,_,_,NonAnnualCode,_,_,_),TaxCodeRecord(_,_,_,_,AnnualCode,_,_,_),_*) => true
//      case Seq(TaxCodeRecord(_,_,_,_,NonAnnualCode,_,_,_),TaxCodeRecord(_,_,_,_,NonAnnualCode,_,_,_),_*) => true
//      case _ => false
//    }


//      taxCodeHistory
//        .operatedTaxCodeRecords
//        .groupBy(_.dateOfCalculation)
//        .values
//        .toSeq
//        .map((sortedByDate andThen testForAChange)(_))
//        .foldLeft(false)((a: Boolean, b: Boolean) => a || b)
