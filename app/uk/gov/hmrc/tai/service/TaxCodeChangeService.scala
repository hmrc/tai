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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeMismatch, TaxCodeRecord}
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future
import scala.util.control.NonFatal

class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector,
                                         auditor: Auditor,
                                         incomeService: IncomeService) extends TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino)(implicit hc: HeaderCarrier): Future[Boolean] = {

    taxCodeHistory(nino, TaxYear()).flatMap { taxCodeHistory =>

      if(validForService(taxCodeHistory.operatedTaxCodeRecords)) {
        taxCodeMismatch(nino).map{ taxCodeMismatch =>
          !taxCodeMismatch.mismatch
        }
      }
      else {
        Future.successful(false)
      }
    }.recover {
      case NonFatal(e) =>
        Logger.warn(s"Could not evaluate tax code history with message ${e.getMessage}", e)
        false
    }

  }

  def taxCodeChange(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeChange] = {

    taxCodeHistory(nino, TaxYear()) map { taxCodeHistory =>

      val taxCodeRecordList = taxCodeHistory.taxCodeRecords

      if (validForService(taxCodeHistory.operatedTaxCodeRecords)) {

        val recordsGroupedByDate: Map[LocalDate, Seq[TaxCodeRecord]] = taxCodeHistory.operatedTaxCodeRecords.groupBy(_.dateOfCalculation)
        val currentDate :: previousDate :: _ = recordsGroupedByDate.keys.toList.sorted
        val currentRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(currentDate)
        val previousRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(previousDate)
        val previousEndDate = currentRecords.head.dateOfCalculation.minusDays(1)

        val currentTaxCodeChanges = currentRecords.map(
          currentRecord =>
            addEndDate(TaxYearResolver.endOfCurrentTaxYear, currentRecord)
        )

        val previousTaxCodeChanges = previousRecords.map(
          taxCodeRecord =>
            addEndDate(
              previousEndDate,
              taxCodeRecord.copy(dateOfCalculation = previousStartDate(taxCodeRecord.dateOfCalculation))
            )
        )

        val taxCodeChange = TaxCodeChange(currentTaxCodeChanges, previousTaxCodeChanges)

        auditTaxCodeChange(nino, taxCodeChange)

        taxCodeChange

      } else if(taxCodeRecordList.size == 1) {
        Logger.warn(s"Only one tax code record returned for $nino" )
        TaxCodeChange(Seq(addEndDate(TaxYearResolver.endOfCurrentTaxYear, taxCodeRecordList.head)),Seq())
      } else if(taxCodeRecordList.size == 0) {
        Logger.warn(s"Zero tax code records returned for $nino" )
        TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])
      } else {
        Logger.warn(s"Returned list of tax codes is not valid for service: $nino")
        TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])
      }
    }
  }

  def taxCodeMismatch(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeMismatch] = {
    val futureMismatch = for {
      unconfirmedTaxCodes <- incomeService.taxCodeIncomes(nino, TaxYear())
      confirmedTaxCodes <- taxCodeChange(nino)
    } yield {
      val unconfirmedTaxCodeList = unconfirmedTaxCodes.map(taxCodeIncome => taxCodeIncome.taxCode).sorted
      val confirmedTaxCodeList = confirmedTaxCodes.current.map(_.taxCode).sorted
      val mismatch = unconfirmedTaxCodeList != confirmedTaxCodeList

      TaxCodeMismatch(mismatch, unconfirmedTaxCodeList , confirmedTaxCodeList)
    }

    futureMismatch.onFailure {
      case NonFatal(exception) =>
        Logger.warn(s"Failed to compare tax codes for $nino with exception:${exception.getMessage}", exception)
    }

    futureMismatch
  }

  private def addEndDate(date: LocalDate, taxCodeRecord: TaxCodeRecord): TaxCodeRecordWithEndDate = {

    val startDate =
      if (taxCodeRecord.dateOfCalculation.isBefore(TaxYearResolver.startOfCurrentTaxYear)) {
        TaxYearResolver.startOfCurrentTaxYear
      }
      else {
        taxCodeRecord.dateOfCalculation
      }

    TaxCodeRecordWithEndDate(
      taxCodeRecord.taxCode, taxCodeRecord.basisOfOperation, startDate , date,
      taxCodeRecord.employerName, taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary
    )
  }

  def latestTaxCodes(nino:Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier):Future[Seq[TaxCodeRecordWithEndDate]] = {

    taxCodeChangeConnector.taxCodeHistory(nino, taxYear, taxYear).map { taxCodeHistory =>

      val datesOutside = logThis(taxYear, taxCodeHistory)

      val groupedTaxCodeRecords: Map[String, Seq[TaxCodeRecord]] = taxCodeHistory.taxCodeRecords.groupBy(_.employerName)

      val records = groupedTaxCodeRecords.values.flatMap {
        taxCodeRecords =>
          val sorted = taxCodeRecords.sortBy(_.dateOfCalculation)
          sorted.filter(_.dateOfCalculation.isEqual(sorted.head.dateOfCalculation))

      }.toSeq

      records.map(addEndDate(taxYear.end, _))

    }
  }

  def logThis(taxYear: TaxYear, taxCodeHistory: TaxCodeHistory) = {
    taxCodeHistory.taxCodeRecords.filter(taxCodeRecord =>
      taxCodeRecord.dateOfCalculation.isBefore(taxYear.start) || taxCodeRecord.dateOfCalculation.isAfter(taxYear.end)).map(
      _.dateOfCalculation
    )
  }

  private def taxCodeHistory(nino: Nino, taxYear: TaxYear): Future[TaxCodeHistory] = {
    taxCodeChangeConnector.taxCodeHistory(nino, taxYear, taxYear.next)
  }

  private def previousStartDate(date: LocalDate): LocalDate = {
    if (date isBefore TaxYearResolver.startOfCurrentTaxYear) {
      TaxYearResolver.startOfCurrentTaxYear
    } else {
      date
    }
  }

  private def auditTaxCodeChange(nino: Nino, taxCodeChange: TaxCodeChange)(implicit hc: HeaderCarrier): Unit = {
    val detail: Map[String, String] = Map(
      "nino" -> nino.nino,
      "numberOfCurrentTaxCodes" -> taxCodeChange.current.size.toString,
      "numberOfPreviousTaxCodes" -> taxCodeChange.previous.size.toString,
      "dataOfTaxCodeChange" -> taxCodeChange.latestTaxCodeChangeDate.toString,
      "primaryCurrentTaxCode" -> taxCodeChange.primaryCurrentTaxCode,
      "secondaryCurrentTaxCodes" -> taxCodeChange.secondaryCurrentTaxCodes.mkString(","),
      "primaryPreviousTaxCode" -> taxCodeChange.primaryPreviousTaxCode,
      "secondaryPreviousTaxCodes" -> taxCodeChange.secondaryPreviousTaxCodes.mkString(","),
      "primaryCurrentPayrollNumber" -> taxCodeChange.primaryCurrentPayrollNumber.getOrElse(""),
      "secondaryCurrentPayrollNumbers" -> taxCodeChange.secondaryCurrentPayrollNumbers.mkString(","),
      "primaryPreviousPayrollNumber" -> taxCodeChange.primaryPreviousPayrollNumber.getOrElse(""),
      "secondaryPreviousPayrollNumbers" -> taxCodeChange.secondaryPreviousPayrollNumbers.mkString(",")
    )

    auditor.sendDataEvent("TaxCodeChange", detail)
  }

  private def validForService(taxCodeRecords: Seq[TaxCodeRecord]): Boolean = {
    val calculationDates = taxCodeRecords.map(_.dateOfCalculation).distinct
    lazy val latestDate = calculationDates.min

    calculationDates.length >= 2 && TaxYearResolver.fallsInThisTaxYear(latestDate)
  }
}

@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino)(implicit hc: HeaderCarrier): Future[Boolean]

  def taxCodeChange(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeChange]

  def taxCodeMismatch(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeMismatch]

  def latestTaxCodes(nino:Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier):Future[Seq[TaxCodeRecordWithEndDate]]

}
