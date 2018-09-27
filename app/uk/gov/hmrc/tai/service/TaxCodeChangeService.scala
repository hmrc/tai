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
import play.api.libs.json.{Format, JsResultException, Json}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.TaxCodeRecord
import uk.gov.hmrc.tai.model.api.{TaxCodeChange, TaxCodeChangeRecord}
import uk.gov.hmrc.tai.model.des.{Iabd, IabdSummary, IncomeSource}
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


class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector) extends TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean] = {
    val fromYear = TaxYear()
    val toYear = fromYear

    taxCodeChangeConnector.taxCodeHistory(nino, fromYear, toYear) map { taxCodeHistory =>

      hasTaxCode(taxCodeHistory.operatedTaxCodeRecords)

    } recover {
      case exception: JsResultException =>
        Logger.warn(s"Failed to retrieve TaxCodeRecord for $nino with exception:${exception.getMessage}")
        false
      case ex => throw ex
    }
  }

  def taxCodeChange(nino: Nino): Future[TaxCodeChange] = {
    val fromYear = TaxYear()
    val toYear = fromYear

    taxCodeChangeConnector.taxCodeHistory(nino, fromYear, toYear) map { taxCodeHistory =>

      if (hasTaxCode(taxCodeHistory.operatedTaxCodeRecords)) {

        val recordsGroupedByDate: Map[LocalDate, Seq[TaxCodeRecord]] = taxCodeHistory.operatedTaxCodeRecords.groupBy(_.dateOfCalculation)

        val currentDate :: previousDate :: _ = recordsGroupedByDate.keys.toList.sorted

        val currentRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(currentDate)

        val previousRecords: Seq[TaxCodeRecord] = recordsGroupedByDate(previousDate)

        val previousEndDate = currentRecords.head.dateOfCalculation.minusDays(1)

        val currentTaxCodeChanges = currentRecords.map(currentRecord => TaxCodeChangeRecord(currentRecord.taxCode,
          currentRecord.taxCodeId,
          currentRecord.basisOfOperation,
          currentRecord.dateOfCalculation,
          TaxYearResolver.endOfCurrentTaxYear,
          currentRecord.employerName,
          currentRecord.payrollNumber,
          currentRecord.pensionIndicator,
          currentRecord.isPrimary))

        val previousTaxCodeChanges = previousRecords.map(previousRecord => TaxCodeChangeRecord(previousRecord.taxCode,
          previousRecord.taxCodeId,
          previousRecord.basisOfOperation,
          previousStartDate(previousRecord.dateOfCalculation),
          previousEndDate,
          previousRecord.employerName,
          previousRecord.payrollNumber,
          previousRecord.pensionIndicator,
          previousRecord.isPrimary))

        TaxCodeChange(currentTaxCodeChanges, previousTaxCodeChanges)

      } else {
        TaxCodeChange(Seq.empty[TaxCodeChangeRecord], Seq.empty[TaxCodeChangeRecord])
      }
    }
  }

  def iabdComparison(nino: Nino): Future[TaxCodeComparison] = {
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


  private def hasTaxCode(taxCodeRecords: Seq[TaxCodeRecord]): Boolean = {
    val calculationDates = taxCodeRecords.map(_.dateOfCalculation).distinct
    lazy val latestDate = calculationDates.min

    calculationDates.length >= 2 && TaxYearResolver.fallsInThisTaxYear(latestDate)
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

  def iabdComparison(nino: Nino): Future[TaxCodeComparison]

}