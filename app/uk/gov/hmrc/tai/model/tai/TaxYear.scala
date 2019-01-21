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

package uk.gov.hmrc.tai.model.tai

import com.github.nscala_time.time.Imports._
import org.joda.time.LocalDate

import scala.util.matching.Regex

object TaxYearDates {
  val startDay = 6
  val startMonth = 4
  val endDay = 5
  val endMonth = 4
}

case class TaxYear(year: Int) extends Ordered[TaxYear] {
  import TaxYearDates._

  require(year.toString.length == 4, "Invalid year")

  def start: LocalDate = new LocalDate(year, startMonth, startDay)
  def end: LocalDate = new LocalDate(year + 1, endMonth , endDay)
  def next: TaxYear = TaxYear(year + 1)
  def prev = TaxYear(year - 1)
  def startPrev: LocalDate = new LocalDate(prev.year, endMonth, startDay)
  def endPrev: LocalDate = new LocalDate(prev.year + 1, startMonth, endDay)
  def compare(that: TaxYear): Int = this.year compare that.year
  def twoDigitRange = s"${start.year.get % 100}-${end.year.get % 100}"
  def fourDigitRange = s"${start.year.get}-${end.year.get}"

  def withinTaxYear(currentDate: LocalDate): Boolean = {
    (currentDate.isEqual(TaxYear().start) || currentDate.isAfter(TaxYear().start)) &&
      (currentDate.isBefore(TaxYear().end) || currentDate.isEqual(TaxYear().end))
  }

  @deprecated("Does not check if date is post tax year, use withinTaxYear instead")
  def fallsInThisTaxYear(currentDate: LocalDate): Boolean = {
    val earliestDateForCurrentTaxYear = start
    earliestDateForCurrentTaxYear.isBefore(currentDate) || earliestDateForCurrentTaxYear.isEqual(currentDate)
  }

  def taxYearFor(dateToResolve: LocalDate): Int = {
    val year = dateToResolve.year.get

    if (dateToResolve.isBefore(new LocalDate(year, 4, 6)))
      year - 1
    else
      year
  }

}

object TaxYear {
  def apply(from: LocalDate = new LocalDate): TaxYear = {
    val naiveYear = TaxYear(from.year.get)
    if (from < naiveYear.start) {
      naiveYear.prev
    }
    else {
      naiveYear
    }
  }

  def apply(from: String): TaxYear = {
    val YearRange = "([0-9]+)-([0-9]+)".r

    object Year {
      val SimpleYear: Regex = "([12][0-9])?([0-9]{2})".r
      def unapply(in: String): Option[Int] = in match {
        case SimpleYear(cenStr, yearStr) => {
          val year = yearStr.toInt
          val century = Option(cenStr).filter(_.nonEmpty) match {
            case None if year > 70 => 1900
            case None => 2000
            case Some(x) => x.toInt * 100
          }
          Some(century + year)
        }
        case _ => None
      }
    }

    from match {
      case Year(year) => TaxYear(year)
      case YearRange(Year(fYear),Year(tYear)) if tYear == fYear + 1 =>
        TaxYear(fYear)
      case x => throw new IllegalArgumentException(s"Cannot parse $x")
    }
  }
}