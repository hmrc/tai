/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.util

object TaiConstants {
  val DAYS_IN_YEAR = 365
  val npsDateFormat = "yyyy-MM-dd"
  val contentType: String = "application/json"
  val EmergencyTaxCode = "X"
  val LondonEuropeTimezone = "Europe/London"
}

object IFormConstants {
  val DateFormat = "d MMMM yyyy"
  val Yes = "Yes"
  val No = "No"
  val AddEmploymentAuditTxnName = "AddEmploymentRequest"
  val IncorrectEmploymentAuditTxnName = "IncorrectEmploymentRequest"
  val UpdatePreviousYearIncomeAuditTxnName = "UpdatePreviousYearIncomeRequest"
  val IncorrectEmploymentSubmissionKey = "IncorrectEmployment"
  val UpdatePreviousYearIncomeSubmissionKey = "UpdatePreviousYearIncome"
  val RemoveCompanyBenefitSubmissionKey = "RemoveCompanyBenefit"
  val RemoveCompanyBenefitAuditTxnName = "RemoveCompanyBenefitRequest"
  val AddPensionProviderSubmissionKey = "AddPensionProvider"
  val AddPensionProviderAuditTxnName = "AddPensionProviderRequest"
  val IncorrectPensionProviderSubmissionKey = "IncorrectPensionProviderRequest"
}

trait NpsExceptions {
  val CodingCalculationCYPlusOne = "Cannot perform a Coding Calculation for CY+1"
}

object HodsSource {
  val NpsSource = 0
}

trait IabdTypeConstants {
  val NewEstimatedPay = 27
}

trait TaxCodeHistoryConstants {
  val Primary = "PRIMARY"
  val Secondary = "SECONDARY"
  val Cumulative = "Cumulative"
  val Week1Month1 = "Week 1 Month 1"
}
