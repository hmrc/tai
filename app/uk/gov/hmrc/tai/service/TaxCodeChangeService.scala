/*
 * Copyright 2023 HM Revenue & Customs
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

import java.time.LocalDate
import play.api.Logging
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeSummary}
import uk.gov.hmrc.tai.model.domain.income.{BasisOperation, TaxCodeIncome, Week1Month1BasisOperation}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeMismatch, TaxCodeRecord}
import uk.gov.hmrc.tai.repositories.TaxCodeChangeRepository
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering
import uk.gov.hmrc.tai.util.{TaiConstants, TaxCodeHistoryConstants}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class TaxCodeChangeServiceImpl @Inject()(
  taxCodeChangeRepository: TaxCodeChangeRepository,
  auditor: Auditor,
  incomeService: IncomeService)(
  implicit ec: ExecutionContext
) extends TaxCodeChangeService with TaxCodeHistoryConstants with Logging {

  def hasTaxCodeChanged(nino: Nino)(implicit hc: HeaderCarrier, request: Request[_]): Future[Boolean] =
    taxCodeHistory(nino, TaxYear())
      .flatMap { taxCodeHistory =>
        if (validForService(taxCodeHistory.applicableTaxCodeRecords)) {
          logger.debug("change is valid for service")
          taxCodeMismatch(nino).map(_.mismatch)
        } else {
          logger.debug("change is not valid for service")
          Future.successful(false)
        }
      }
      .recover {
        case NonFatal(e) =>
          logger.warn(s"Could not evaluate tax code history with message ${e.getMessage}", e)
          false
      }

  def taxCodeChange(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeChange] =
    taxCodeHistory(nino, TaxYear()) map { taxCodeHistory =>
      val taxCodeRecordList = taxCodeHistory.taxCodeRecord

      if (validForService(taxCodeHistory.applicableTaxCodeRecords)) {

        val recordsGroupedByDate: Map[LocalDate, Seq[TaxCodeRecord]] =
          taxCodeHistory.applicableTaxCodeRecords.groupBy(_.dateOfCalculation)
        val currentDate :: previousDate :: _ = recordsGroupedByDate.keys.toList.sorted
        val currentRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(currentDate)
        val previousRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(previousDate)
        val previousEndDate = currentRecords.head.dateOfCalculation.minusDays(1)

        val currentTaxCodeChanges = currentRecords.map(
          currentRecord => TaxCodeSummary(currentRecord, TaxYear().end)
        )

        val previousTaxCodeChanges = previousRecords.map(
          taxCodeRecord =>
            TaxCodeSummary(
              taxCodeRecord.copy(dateOfCalculation = previousStartDate(taxCodeRecord.dateOfCalculation)),
              previousEndDate
          )
        )

        val taxCodeChange = TaxCodeChange(currentTaxCodeChanges, previousTaxCodeChanges)

        auditTaxCodeChange(nino, taxCodeChange)

        taxCodeChange

      } else if (taxCodeRecordList.size == 1) {
        logger.warn(s"Only one tax code record returned for $nino")

        TaxCodeChange(Seq(TaxCodeSummary(taxCodeRecordList.head, TaxYear().end)), Seq())
      } else if (taxCodeRecordList.size == 0) {
        logger.warn(s"Zero tax code records returned for $nino")
        TaxCodeChange(Seq.empty[TaxCodeSummary], Seq.empty[TaxCodeSummary])
      } else {
        logger.warn(s"Returned list of tax codes is not valid for service: $nino")
        TaxCodeChange(Seq.empty[TaxCodeSummary], Seq.empty[TaxCodeSummary])
      }
    }

  def taxCodeMismatch(nino: Nino)(implicit hc: HeaderCarrier, request: Request[_]): Future[TaxCodeMismatch] = {
    val futureMismatch = for {
      unconfirmedTaxCodes: Seq[TaxCodeIncome] <- incomeService.taxCodeIncomes(nino, TaxYear())
      confirmedTaxCodes: TaxCodeChange        <- taxCodeChange(nino)
    } yield {
      val unconfirmedTaxCodeList: Seq[String] =
        unconfirmedTaxCodes.map(income => sanitizeCode(income.taxCode, income.basisOperation))

      val confirmedTaxCodeList: Seq[String] =
        confirmedTaxCodes.current.map(income => sanitizeCode(income.taxCode, BasisOperation(income.basisOfOperation)))

      logger.debug(s"Unconfirmed tax codes \n $unconfirmedTaxCodeList")
      logger.debug(s"Confirmed tax codes \n $confirmedTaxCodeList")

      val taxCodeMismatch = TaxCodeMismatch(unconfirmedTaxCodeList, confirmedTaxCodeList)

      logger.debug(s"taxCodeMismatch? $taxCodeMismatch")

      taxCodeMismatch
    }

    futureMismatch.onFailure {
      case NonFatal(exception) =>
        logger.warn(s"Failed to compare tax codes for $nino with exception:${exception.getMessage}", exception)
    }

    futureMismatch
  }

  private def sanitizeCode(code: String, basis: BasisOperation): String =
    if (basis == Week1Month1BasisOperation) {
      code + TaiConstants.EmergencyTaxCode
    } else {
      code
    }

  def latestTaxCodes(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeSummary]] =
    taxCodeHistory(nino, taxYear).map { taxCodeHistory =>
      val groupedTaxCodeRecords: Map[String, Seq[TaxCodeRecord]] = taxCodeHistory.taxCodeRecord.groupBy(_.employerName)

      groupedTaxCodeRecords.values.flatMap { taxCodeRecords =>
        val sortedTaxCodeRecords = taxCodeRecords.sortBy(_.dateOfCalculation)
        val latestTaxCodeRecords =
          sortedTaxCodeRecords.filter(_.dateOfCalculation.isEqual(sortedTaxCodeRecords.head.dateOfCalculation))
        latestTaxCodeRecords.map(TaxCodeSummary(_, taxYear.end))
      }.toSeq

    }

  private def taxCodeHistory(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] =
    taxCodeChangeRepository.taxCodeHistory(nino, taxYear)

  private def previousStartDate(date: LocalDate): LocalDate = {
    val startOfCurrentTaxYear = TaxYear().start

    if (date isBefore startOfCurrentTaxYear) {
      startOfCurrentTaxYear
    } else {
      date
    }
  }

  private def auditTaxCodeChange(nino: Nino, taxCodeChange: TaxCodeChange)(implicit hc: HeaderCarrier): Unit = {
    val detail: Map[String, String] = Map(
      "nino"                            -> nino.nino,
      "numberOfCurrentTaxCodes"         -> taxCodeChange.current.size.toString,
      "numberOfPreviousTaxCodes"        -> taxCodeChange.previous.size.toString,
      "dataOfTaxCodeChange"             -> taxCodeChange.latestTaxCodeChangeDate.toString,
      "primaryCurrentTaxCode"           -> taxCodeChange.primaryCurrentTaxCode.getOrElse(""),
      "secondaryCurrentTaxCodes"        -> taxCodeChange.secondaryCurrentTaxCodes.mkString(","),
      "primaryPreviousTaxCode"          -> taxCodeChange.primaryPreviousTaxCode.getOrElse(""),
      "secondaryPreviousTaxCodes"       -> taxCodeChange.secondaryPreviousTaxCodes.mkString(","),
      "primaryCurrentPayrollNumber"     -> taxCodeChange.primaryCurrentPayrollNumber.getOrElse(""),
      "secondaryCurrentPayrollNumbers"  -> taxCodeChange.secondaryCurrentPayrollNumbers.mkString(","),
      "primaryPreviousPayrollNumber"    -> taxCodeChange.primaryPreviousPayrollNumber.getOrElse(""),
      "secondaryPreviousPayrollNumbers" -> taxCodeChange.secondaryPreviousPayrollNumbers.mkString(",")
    )

    auditor.sendDataEvent("TaxCodeChange", detail)
  }

  private def validForService(taxCodeRecords: Seq[TaxCodeRecord]): Boolean = {
    val calculationDates = taxCodeRecords.map(_.dateOfCalculation).distinct
    logger.debug(s"calculation dates $calculationDates")
    lazy val latestDate = calculationDates.min
    calculationDates.length >= 2 && TaxYear().withinTaxYear(latestDate)
  }

}

@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino)(implicit hc: HeaderCarrier, request: Request[_]): Future[Boolean]

  def taxCodeChange(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeChange]

  def taxCodeMismatch(nino: Nino)(implicit hc: HeaderCarrier, request: Request[_]): Future[TaxCodeMismatch]

  def latestTaxCodes(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[TaxCodeSummary]]

}
