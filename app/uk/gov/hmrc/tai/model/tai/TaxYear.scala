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

package uk.gov.hmrc.tai.model.tai

import play.api.libs.json._
import uk.gov.hmrc.tai.util.TaiConstants.LondonEuropeTimezone

import java.time.{LocalDate, ZoneId}
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

  def start: LocalDate = LocalDate.of(year, startMonth, startDay)
  def end: LocalDate = LocalDate.of(year + 1, endMonth, endDay)
  def next: TaxYear = TaxYear(year + 1)
  def prev = TaxYear(year - 1)
  def startPrev: LocalDate = LocalDate.of(prev.year, endMonth, startDay)
  def endPrev: LocalDate = LocalDate.of(prev.year + 1, startMonth, endDay)
  def compare(that: TaxYear): Int = this.year compare that.year
  def twoDigitRange = s"${start.getYear % 100}-${end.getYear % 100}"
  def fourDigitRange = s"${start.getYear}-${end.getYear}"

  def withinTaxYear(currentDate: LocalDate): Boolean =
    (currentDate.isEqual(TaxYear().start) || currentDate.isAfter(TaxYear().start)) &&
      (currentDate.isBefore(TaxYear().end) || currentDate.isEqual(TaxYear().end))
}

object TaxYear {
  def apply(from: LocalDate = LocalDate.now(ZoneId.of(LondonEuropeTimezone))): TaxYear = {
    val naiveYear = TaxYear(from.getYear)
    if (from isBefore naiveYear.start) {
      naiveYear.prev
    } else { naiveYear }
  }

  def apply(from: String): TaxYear = {
    val YearRange = "([0-9]+)-([0-9]+)".r

    object Year {
      val SimpleYear: Regex = "([12][0-9])?([0-9]{2})".r
      def unapply(in: String): Option[Int] = in match {
        case SimpleYear(cenStr, yearStr) =>
          val year = yearStr.toInt
          val century = Option(cenStr).filter(_.nonEmpty) match {
            case None if year > 70 => 1900
            case None              => 2000
            case Some(x)           => x.toInt * 100
          }
          Some(century + year)
        case _ => None
      }
    }

    from match {
      case Year(year) => TaxYear(year)
      case YearRange(Year(fYear), Year(tYear)) if tYear == fYear + 1 =>
        TaxYear(fYear)
      case x => throw new IllegalArgumentException(s"Cannot parse $x")
    }
  }

//  implicit val formatTaxYear: Format[TaxYear] = new Format[TaxYear] {
//    override def reads(json: JsValue): JsSuccess[TaxYear] =
//      if (json.validate[Int].isSuccess) {
//        JsSuccess(TaxYear(json.as[Int]))
//      } else {
//        throw new IllegalArgumentException("Invalid tax year")
//      }
//
//    override def writes(taxYear: TaxYear): JsNumber = JsNumber(taxYear.year)
//  }

  implicit val formatTaxYear: Format[TaxYear] = new Format[TaxYear] {
    override def reads(j: JsValue): JsResult[TaxYear] = j match {
      case JsNumber(n) => JsSuccess(TaxYear(n.toInt))
      case x           => JsError(s"Expected JsNumber, found $x")
    }
    override def writes(v: TaxYear): JsValue = JsNumber(v.year)
  }
}
