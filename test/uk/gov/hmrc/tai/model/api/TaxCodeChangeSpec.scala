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

package uk.gov.hmrc.tai.model.api

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaxCodeHistoryConstants

class TaxCodeChangeSpec extends PlaySpec with TaxCodeHistoryConstants {

  "TaxCodeChange" must {
    "return the latest tax code change date" in {
      val taxCodeChange = createTaxCodeChange()
      taxCodeChange.latestTaxCodeChangeDate mustEqual currentTaxCodeChangeRecordPrimary.startDate
    }

    "primaryPreviousRecord" must {
      "return Some(TaxCodeSummary) when primary previous tax code is found" in {
        val taxCodeChange = createTaxCodeChange()
        taxCodeChange.primaryPreviousRecord mustBe Some(previousTaxCodeChangeRecordPrimary)
      }

      "return None when no primary previous tax code is found" in {
        val taxCodeChange = createTaxCodeChange(previousTaxCodeChangeRecords = Seq())
        taxCodeChange.primaryPreviousRecord mustBe None
      }
    }

    "return the primary current tax code" in {
      val taxCodeChange = createTaxCodeChange()
      taxCodeChange.primaryCurrentTaxCode mustBe Some(currentTaxCodeChangeRecordPrimary.taxCode)
    }

    "returns None when no primary current tax code is found" in {
      val taxCodeChange = createTaxCodeChange(Seq(currentTaxCodeChangeRecordSecondary))
      taxCodeChange.primaryCurrentTaxCode mustBe None
    }

    "return the secondary current tax code" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedCurrentTaxCodes = Seq(currentTaxCodeChangeRecordSecondary.taxCode)
      taxCodeChange.secondaryCurrentTaxCodes mustBe expectedCurrentTaxCodes
    }

    "return None when no primary previous tax code is found" in {
      val taxCodeChange = createTaxCodeChange(previousTaxCodeChangeRecords = Seq.empty)
      taxCodeChange.primaryPreviousTaxCode mustBe None
    }

    "return the primary previous tax code" in {
      val taxCodeChange = createTaxCodeChange()
      taxCodeChange.primaryPreviousTaxCode mustBe Some(previousTaxCodeChangeRecordPrimary.taxCode)
    }

    "return the secondary previous tax code" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedPreviousTaxCodes = Seq("D0")
      taxCodeChange.secondaryPreviousTaxCodes mustEqual expectedPreviousTaxCodes
    }

    "return the primary current payroll number" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedPayrollNumber = Some(payrollNumberCurr)
      taxCodeChange.primaryCurrentPayrollNumber mustEqual expectedPayrollNumber
    }

    "return the secondary current payroll numbers" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedPayrollNumbers = Seq(payrollNumberCurr)
      taxCodeChange.secondaryCurrentPayrollNumbers mustEqual expectedPayrollNumbers
    }

    "return the primary previous payroll number" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedPayrollNumber = Some(payrollNumberPrev)
      taxCodeChange.primaryPreviousPayrollNumber mustEqual expectedPayrollNumber
    }

    "return an empty sequence when no secondary tax code records exists" in {
      val taxCodeChange = createTaxCodeChange(Seq(currentTaxCodeChangeRecordPrimary))
      taxCodeChange.secondaryCurrentTaxCodes mustEqual Seq.empty
    }

    "return the secondary previous payroll numbers" in {
      val taxCodeChange = createTaxCodeChange()
      val expectedPayrollNumbers = Seq(payrollNumberPrev)
      taxCodeChange.secondaryPreviousPayrollNumbers mustEqual expectedPayrollNumbers
    }
  }

  def createTaxCodeChange(
    currentTaxCodeChangeRecords: Seq[TaxCodeSummary] =
      Seq(currentTaxCodeChangeRecordPrimary, currentTaxCodeChangeRecordSecondary),
    previousTaxCodeChangeRecords: Seq[TaxCodeSummary] =
      Seq(previousTaxCodeChangeRecordPrimary, previousTaxCodeChangeRecordSecondary)): TaxCodeChange =
    TaxCodeChange(currentTaxCodeChangeRecords, previousTaxCodeChangeRecords)

  val currentStartDate = TaxYear().start.plusDays(2)
  val currentEndDate = TaxYear().end
  val previousStartDate = TaxYear().start.plusDays(1)
  val previousEndDate = currentStartDate.minusDays(1)
  val payrollNumberPrev = "11111"
  val payrollNumberCurr = "22222"

  val previousTaxCodeChangeRecordPrimary = TaxCodeSummary(
    1,
    "1185L",
    Cumulative,
    previousStartDate,
    previousEndDate,
    "Employer 1",
    Some(payrollNumberPrev),
    pensionIndicator = false,
    primary = true)

  val previousTaxCodeChangeRecordSecondary = TaxCodeSummary(
    2,
    "D0",
    Cumulative,
    previousStartDate,
    previousEndDate,
    "Employer 1",
    Some(payrollNumberPrev),
    pensionIndicator = false,
    primary = false)

  val currentTaxCodeChangeRecordPrimary = TaxCodeSummary(
    3,
    "1000L",
    Cumulative,
    currentStartDate,
    currentEndDate,
    "Employer 1",
    Some(payrollNumberCurr),
    pensionIndicator = false,
    primary = true)

  val currentTaxCodeChangeRecordSecondary = TaxCodeSummary(
    4,
    "1001L",
    Cumulative,
    currentStartDate.minusDays(1),
    currentEndDate,
    "Employer 2",
    Some(payrollNumberCurr),
    pensionIndicator = false,
    primary = false)

}
