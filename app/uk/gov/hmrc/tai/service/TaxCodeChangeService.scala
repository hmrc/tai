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
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.des.{Iabd, IabdSummary, IncomeSource}
import uk.gov.hmrc.tai.model.{TaxCodeHistory, TaxCodeMismatch, TaxCodeRecord}
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeRecordWithEndDate}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.time.TaxYearResolver
import uk.gov.hmrc.tai.util.DateTimeHelper.dateTimeOrdering

import scala.concurrent.Future

case class TaxCodeComponent(allowances: Seq[IabdSummary], deductions: Seq[IabdSummary]) {

  def merge(that: TaxCodeComponent): TaxCodeComponent = {
    TaxCodeComponent(this.allowances ++ that.allowances, this.deductions ++ that.deductions)
  }
}

object TaxCodeComponent {
  implicit val format: Format[TaxCodeComponent] = Json.format[TaxCodeComponent]
}

case class TaxCodeComparison(previous: TaxCodeComponent, current: TaxCodeComponent)

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
      } else {
        Logger.warn(s"Returned list of tax codes is not valid for service: $nino")
        TaxCodeChange(Seq.empty[TaxCodeRecordWithEndDate], Seq.empty[TaxCodeRecordWithEndDate])
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
      val mismatch = unconfirmedTaxCodeList != confirmedTaxCodeList

      TaxCodeMismatch(mismatch, unconfirmedTaxCodeList , confirmedTaxCodeList)
    }) recover {
      case exception =>
        Logger.warn(s"Failed to compare tax codes for $nino with exception:${exception.getMessage}")
        throw new BadRequestException(exception.getMessage)
    }
  }

  private def addEndDate(date: LocalDate, taxCodeRecord: TaxCodeRecord): TaxCodeRecordWithEndDate ={
    TaxCodeRecordWithEndDate(
      taxCodeRecord.taxCode, taxCodeRecord.taxCodeId, taxCodeRecord.basisOfOperation, taxCodeRecord.dateOfCalculation, date,
      taxCodeRecord.employerName, taxCodeRecord.payrollNumber, taxCodeRecord.pensionIndicator, taxCodeRecord.isPrimary
    )
  }

  private def taxCodeHistory(nino: Nino, taxYear: TaxYear): Future[TaxCodeHistory] = {
    taxCodeChangeConnector.taxCodeHistory(nino, taxYear, taxYear.next)
  }

  def iabdComparison(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeComparison] = {
    taxCodeChange(nino).flatMap(getTaxCodeComparison(nino) _ compose getTaxCodeIds)
  }

  private def getTaxCodeIds(taxCodeChangeObj: TaxCodeChange): (Seq[Int], Seq[Int]) = {
    val previousTaxCodeIds = taxCodeChangeObj.previous.map(_.taxCodeId)
    val currentTaxCodeIds = taxCodeChangeObj.current.map(_.taxCodeId)

    (previousTaxCodeIds, currentTaxCodeIds)
  }

  private def makeCallForIabds(nino: Nino)(taxAccountId: Int): Future[TaxCodeComponent] = {

    def getIABDSummary(incomeSources: Seq[IncomeSource])(f: IncomeSource => Seq[Iabd]) = incomeSources.flatMap(f(_).flatMap(_.iabdSummaries))

    taxCodeChangeConnector.taxAccountHistory(nino, taxAccountId) map { taxAccountDetails => {
      val incomeSources = taxAccountDetails.map(_.incomeSources).getOrElse(throw new RuntimeException("test message"))
      TaxCodeComponent(getIABDSummary(incomeSources)(_.allowances), getIABDSummary(incomeSources)(_.deductions))
    }}
  }

  private def getTaxCodeComparison(nino: Nino)(taxCodeIds: (Seq[Int], Seq[Int])): Future[TaxCodeComparison] = {

    def callsForTaxCodeIds(ids: Seq[Int])(callFn: Int => Future[TaxCodeComponent]): Future[TaxCodeComponent] = {
      Future.sequence(ids.map(callFn)).map(_.reduce(_ merge _))
    }

    val prev: Future[TaxCodeComponent] = callsForTaxCodeIds(taxCodeIds._1)(makeCallForIabds(nino))
    val curr: Future[TaxCodeComponent] = callsForTaxCodeIds(taxCodeIds._2)(makeCallForIabds(nino))

    prev zip curr map TaxCodeComparison.tupled
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

  def iabdComparison(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxCodeComparison]

}
