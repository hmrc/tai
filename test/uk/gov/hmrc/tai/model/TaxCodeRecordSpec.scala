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

package uk.gov.hmrc.tai.model

import org.scalatest.MustMatchers
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

class TaxCodeRecordSpec extends PlaySpec with TaxCodeHistoryConstants with MustMatchers {

  "mostRecentTaxCodeRecord" should {

    "return the taxCodeRecord when given a sequence of one taxCodeRecord" in {

      val records = Seq(mostRecentTaxCodeRecord)

      TaxCodeRecord.mostRecent(records) mustEqual mostRecentTaxCodeRecord

    }

    "return the most recent taxCodeRecord when given a sequence of taxCodeRecords" in {

      val records = Seq(olderTaxCodeRecord, mostRecentTaxCodeRecord)

      TaxCodeRecord.mostRecent(records) mustEqual mostRecentTaxCodeRecord

    }
  }

  val cyMinus1 = TaxYear().prev

  val mostRecentStartDate = cyMinus1.start.plusDays(2)
  val mostRecentEndDate = cyMinus1.end
  val previousStartDate = cyMinus1.start
  val previousEndDate = mostRecentStartDate.minusDays(1)
  val previousStartDateInPrevYear = cyMinus1.start.minusDays(2)
  val payrollNumber = "12345"

  val olderTaxCodeRecord = TaxCodeRecord(
    TaxYear(),
    1,
    "1185L",
    Cumulative,
    "Employer 1",
    operatedTaxCode = true,
    previousStartDateInPrevYear,
    Some(payrollNumber),
    pensionIndicator = false,
    Primary
  )

  val mostRecentTaxCodeRecord = TaxCodeRecord(
    TaxYear(),
    2,
    "1000L",
    Cumulative,
    "Employer 1",
    operatedTaxCode = true,
    mostRecentStartDate,
    Some(payrollNumber),
    pensionIndicator = false,
    Primary
  )

  private val payrollNumber2 = "999"

  val recordEmployer2 = mostRecentTaxCodeRecord.copy(employerName = "Employer 2")

  val recordNoPayrollPrimary = mostRecentTaxCodeRecord.copy(payrollNumber = None)
  val recordNoPayrollSecondary = mostRecentTaxCodeRecord.copy(payrollNumber = None)
}
