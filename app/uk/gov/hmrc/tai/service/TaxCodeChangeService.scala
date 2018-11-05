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
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeChangeRecord}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.time.TaxYearResolver
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering

import scala.concurrent.Future

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
      case exception: Exception =>
        Logger.debug("Could not evaluate tax code history")
        false
    }

  }

  def taxCodeChange(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeChange] = {
    taxCodeHistory(nino, TaxYear()) map { taxCodeHistory =>

      val taxCodeRecordList = taxCodeHistory.taxCodeRecord

      if (validForService(taxCodeHistory.operatedTaxCodeRecords)) {

        val recordsGroupedByDate: Map[LocalDate, Seq[TaxCodeRecord]] = taxCodeHistory.operatedTaxCodeRecords.groupBy(_.dateOfCalculation)
        val currentDate :: previousDate :: _ = recordsGroupedByDate.keys.toList.sorted
        val currentRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(currentDate)
        val previousRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(previousDate)
        val previousEndDate = currentRecords.head.dateOfCalculation.minusDays(1)

        val currentTaxCodeChanges = currentRecords.map(currentRecord => TaxCodeChangeRecord(currentRecord.taxCode,
          currentRecord.basisOfOperation,
          currentRecord.dateOfCalculation,
          TaxYearResolver.endOfCurrentTaxYear,
          currentRecord.employerName,
          currentRecord.payrollNumber,
          currentRecord.pensionIndicator,
          currentRecord.isPrimary))

        val previousTaxCodeChanges = previousRecords.map(previousRecord => TaxCodeChangeRecord(previousRecord.taxCode,
          previousRecord.basisOfOperation,
          previousStartDate(previousRecord.dateOfCalculation),
          previousEndDate,
          previousRecord.employerName,
          previousRecord.payrollNumber,
          previousRecord.pensionIndicator,
          previousRecord.isPrimary))

        val taxCodeChange = TaxCodeChange(currentTaxCodeChanges, previousTaxCodeChanges)

        auditTaxCodeChange(nino, taxCodeChange)

        taxCodeChange

      } else if(taxCodeRecordList.size == 1) {

        val taxCodeRecord = taxCodeRecordList.head
        val taxCodeChangeRecord = TaxCodeChangeRecord(taxCodeRecord.taxCode, taxCodeRecord.basisOfOperation,
          taxCodeRecord.dateOfCalculation, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName, taxCodeRecord.payrollNumber,
          taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary)

        TaxCodeChange(Seq(taxCodeChangeRecord),Seq())

      } else {
        TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])
      }
    }
  }

  def taxCodeMismatch(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeMismatch] = {
    (for {
      unconfirmedTaxCodes <- incomeService.taxCodeIncomes(nino, TaxYear())
      confirmedTaxCodes <- taxCodeChange(nino)
    } yield {
      val unconfirmedTaxCodeList = unconfirmedTaxCodes.map(taxCodeIncome => taxCodeIncome.taxCode).sorted
      val confirmedTaxCodeList = confirmedTaxCodes.current.map(_.taxCode).sorted
      val mismatchOfTaxCodes = confirmedTaxCodeList.nonEmpty && unconfirmedTaxCodeList != confirmedTaxCodeList

      TaxCodeMismatch(mismatchOfTaxCodes, unconfirmedTaxCodeList , confirmedTaxCodeList)
    }) recover {
      case exception =>
        Logger.warn(s"Failed to Match for $nino with exception:${exception.getMessage}")
        throw new BadRequestException(exception.getMessage)
    }
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

}
