/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.rti

import java.time.LocalDate
import org.scalacheck._
import Gen._
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.temporal.ChronoUnit

/**
  * A ScalaCheck generator for RtiData records, some day this may be used to
  * perform property-based testing or for implementation of a smart-stub
  * (infinite amount of deterministicly random records for testing)
  */
object RtiGenerator {

  def moneyAmount(a: Enumeration): Gen[(a.Value, BigDecimal)] =
    for {
      f <- oneOf(a.values.toSeq)
      v <- choose[Double](1, 1000000)
    } yield (f, v)

  def dateBetween(
    from: LocalDate = LocalDate.of(2000, 1, 1),
    to: LocalDate = LocalDate.of(2016, 5, 1)
  ): Gen[LocalDate] = {
    val dist = ChronoUnit.DAYS.between(from, to)
    choose(0, dist).map(from.plusDays)
  }

  val year: Gen[RtiPayment] = for {
    freq          <- oneOf(PayFrequency.values.toSeq)
    paymentDate   <- dateBetween(LocalDate.of(2014, 4, 6), LocalDate.of(2016, 5, 1))
    receivedDate  <- dateBetween(paymentDate, LocalDate.of(2016, 5, 1))
    taxablePay    <- choose(1, 1000)
    taxablePayYTD <- choose(1, 1000)
    taxed         <- choose(1, 1000)
    taxedYTD      <- choose(1, 1000)
    payId         <- option(alphaNumStr(6, 12))
    typeId        <- option(oneOf(true, false))
  } yield
    RtiPayment(
      freq,
      paymentDate,
      receivedDate,
      taxablePay,
      taxablePayYTD,
      taxed,
      taxedYTD,
      payId,
      typeId.getOrElse(false)
    )

  def alphaNumStr(min: Int, max: Int) =
    choose(min, max)
      .flatMap {
        listOfN(_, oneOf(alphaUpperChar, numChar, numChar, numChar))
      }
      .map(_.mkString)

  val employment: Gen[RtiEmployment] = for {
    officeRefNo            <- choose(1, 1000).map(_.toString)
    payeRef                <- alphaNumStr(2, 8)
    accountOfficeReference <- alphaNumStr(4, 9)
    payments               <- choose(0, 10).flatMap(listOfN(_, year))
    currentPayId           <- option(alphaNumStr(6, 18))
    sequenceNumber         <- choose(1, 100)
  } yield
    RtiEmployment(
      officeRefNo,
      payeRef,
      accountOfficeReference,
      payments,
      Nil,
      currentPayId,
      sequenceNumber
    )

  val nino: Gen[String] = for {
    prefix <- alphaStr.map(_.take(2))
    body   <- choose(1, 1000000)
    suffix <- alphaChar
  } yield {
    f"$prefix$body%06d$suffix".toUpperCase
  }

  val rtiData: Gen[RtiData] = for {
    nino           <- nino
    relatedTaxYear <- choose(2012, 2016).map(TaxYear(_))
    requestId      <- choose(1, 100000000000000L)
    emps           <- choose(0, 10).flatMap(listOfN(_, employment))
  } yield
    RtiData(
      nino,
      relatedTaxYear,
      requestId.toString,
      emps
    )
}
