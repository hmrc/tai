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

package uk.gov.hmrc.tai.model

import play.api.libs.json.*
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation
import uk.gov.hmrc.tai.model.rti.PayFrequency
import uk.gov.hmrc.tai.model.tai.AnnualAccount

import java.time.LocalDate

case class TaxBand(
  income: Option[BigDecimal] = None,
  tax: Option[BigDecimal] = None,
  lowerBand: Option[BigDecimal] = None,
  upperBand: Option[BigDecimal] = None,
  rate: Option[BigDecimal] = None
)
object TaxBand {
  implicit val formats: OFormat[TaxBand] = Json.format[TaxBand]
}

case class EditableDetails(isEditable: Boolean = true, payRollingBiks: Boolean = false)
object EditableDetails {
  implicit val formats: OFormat[EditableDetails] = Json.format[EditableDetails]
}

case class Employments(
  id: Option[Int] = None,
  name: Option[String] = None,
  taxCode: Option[String],
  basisOperation: Option[BasisOperation] = None
)

object Employments {
  implicit val format: OFormat[Employments] = Json.format[Employments]
}

case class CYPlusOneChange(
  employmentsTaxCode: Option[List[Employments]] = None,
  scottishTaxCodes: Option[Boolean] = None,
  personalAllowance: Option[Change[BigDecimal, BigDecimal]] = None,
  underPayment: Option[Change[BigDecimal, BigDecimal]] = None,
  totalTax: Option[Change[BigDecimal, BigDecimal]] = None,
  standardPA: Option[BigDecimal] = None,
  employmentBenefits: Option[Boolean] = None,
  personalSavingsAllowance: Option[Change[BigDecimal, BigDecimal]] = None
)
object CYPlusOneChange {
  implicit val formats: OFormat[CYPlusOneChange] = Json.format[CYPlusOneChange]
}

case class IncomeExplanation(
  employerName: String,
  incomeId: Int = 0,
  hasDuplicateEmploymentNames: Boolean = false,
  worksNumber: Option[String] = None,
  paymentDate: Option[LocalDate] = None,
  notificationDate: Option[LocalDate] = None,
  updateActionDate: Option[LocalDate] = None,
  startDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  employmentStatus: Option[Int] = None,
  employmentType: Option[Int] = None,
  isPension: Boolean = false,
  iabdSource: Option[Int] = None,
  payToDate: BigDecimal = BigDecimal(0),
  calcAmount: Option[BigDecimal] = None,
  grossAmount: Option[BigDecimal] = None,
  payFrequency: Option[PayFrequency.Value] = None,
  cessationPay: Option[BigDecimal] = None,
  editableDetails: EditableDetails = EditableDetails()
)

object IncomeExplanation {
  implicit val formats: OFormat[IncomeExplanation] = Json.format[IncomeExplanation]
}

case class RtiCalc(
  employmentType: Int,
  paymentDate: Option[LocalDate] = None,
  payFrequency: Option[PayFrequency.Value] = None,
  employmentId: Int,
  employmentStatus: Int,
  employerName: String,
  totalPayToDate: BigDecimal,
  calculationResult: Option[BigDecimal]
)
object RtiCalc {
  implicit val format: OFormat[RtiCalc] = Json.format[RtiCalc]
}

case class IncomeData(incomeExplanations: List[IncomeExplanation])

object IncomeData {
  implicit val format: OFormat[IncomeData] = Json.format[IncomeData]
}

case class TaxCodeComponent(
  description: Option[String] = None,
  amount: Option[BigDecimal] = None,
  componentType: Option[Int]
)

object TaxCodeComponent {
  implicit val format: OFormat[TaxCodeComponent] = Json.format[TaxCodeComponent]
}

case class TaxCode(taxCode: Option[String], rate: Option[BigDecimal])

object TaxCode {
  implicit val format: OFormat[TaxCode] = Json.format[TaxCode]
}

case class TaxCodeDescription(taxCode: String, name: String, taxCodeDescriptors: List[TaxCode])

object TaxCodeDescription {
  implicit val format: OFormat[TaxCodeDescription] = Json.format[TaxCodeDescription]
}

case class TaxCodeDetails(
  employment: Option[List[Employments]],
  taxCode: Option[List[TaxCode]],
  taxCodeDescriptions: Option[List[TaxCodeDescription]] = None,
  deductions: Option[List[TaxCodeComponent]],
  allowances: Option[List[TaxCodeComponent]],
  splitAllowances: Option[Boolean] = None,
  total: BigDecimal = BigDecimal(0)
)

object TaxCodeDetails {
  implicit val formats: OFormat[TaxCodeDetails] = Json.format[TaxCodeDetails]
}

case class Tax(
  totalIncome: Option[BigDecimal] = None,
  totalTaxableIncome: Option[BigDecimal] = None,
  totalTax: Option[BigDecimal] = None,
  totalInYearAdjustment: Option[BigDecimal] = None,
  inYearAdjustmentIntoCY: Option[BigDecimal] = None,
  inYearAdjustmentIntoCYPlusOne: Option[BigDecimal] = None,
  inYearAdjustmentFromPreviousYear: Option[BigDecimal] = None,
  taxBands: Option[List[TaxBand]] = None,
  allowReliefDeducts: Option[BigDecimal] = None,
  actualTaxDueAssumingBasicRateAlreadyPaid: Option[BigDecimal] = None,
  actualTaxDueAssumingAllAtBasicRate: Option[BigDecimal] = None
)

object Tax {
  implicit val formats: OFormat[Tax] = Json.format[Tax]
}

case class IabdSummary(
  iabdType: Int,
  description: String,
  amount: BigDecimal,
  employmentId: Option[Int] = None,
  estimatedPaySource: Option[Int] = None,
  employmentName: Option[String] = None
)

object IabdSummary {
  implicit val formats: OFormat[IabdSummary] = Json.format[IabdSummary]
}

case class TaxComponent(amount: BigDecimal, componentType: Int, description: String, iabdSummaries: List[IabdSummary])

object TaxComponent {
  implicit val formats: OFormat[TaxComponent] = Json.format[TaxComponent]
}

case class TaxCodeIncomeSummary(
  name: String,
  taxCode: String,
  employmentId: Option[Int] = None,
  employmentPayeRef: Option[String] = None,
  employmentType: Option[Int] = None,
  incomeType: Option[Int] = None,
  employmentStatus: Option[Int] = None,
  tax: Tax,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  income: Option[BigDecimal] = None,
  otherIncomeSourceIndicator: Option[Boolean] = None,
  isEditable: Boolean = false,
  isLive: Boolean = false,
  isOccupationalPension: Boolean = false,
  isPrimary: Boolean = true,
  potentialUnderpayment: Option[BigDecimal] = None,
  basisOperation: Option[BasisOperation] = None
) {}

object TaxCodeIncomeSummary {
  implicit val formats: OFormat[TaxCodeIncomeSummary] = Json.format[TaxCodeIncomeSummary]
}

case class TaxCodeIncomeTotal(
  taxCodeIncomes: List[TaxCodeIncomeSummary],
  totalIncome: BigDecimal,
  totalTax: BigDecimal,
  totalTaxableIncome: BigDecimal
)

object TaxCodeIncomeTotal {
  implicit val formats: OFormat[TaxCodeIncomeTotal] = Json.format[TaxCodeIncomeTotal]
}

case class NoneTaxCodeIncomes(
  statePension: Option[BigDecimal] = None,
  statePensionLumpSum: Option[BigDecimal] = None,
  otherPensions: Option[TaxComponent] = None,
  otherIncome: Option[TaxComponent] = None,
  taxableStateBenefit: Option[TaxComponent] = None,
  untaxedInterest: Option[TaxComponent] = None,
  bankBsInterest: Option[TaxComponent] = None,
  dividends: Option[TaxComponent] = None,
  foreignInterest: Option[TaxComponent] = None,
  foreignDividends: Option[TaxComponent] = None,
  totalIncome: BigDecimal
) {}

object NoneTaxCodeIncomes {
  implicit val formats: OFormat[NoneTaxCodeIncomes] = Json.format[NoneTaxCodeIncomes]
}

case class TaxCodeIncomes(
  employments: Option[TaxCodeIncomeTotal] = None,
  occupationalPensions: Option[TaxCodeIncomeTotal] = None,
  taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal] = None,
  ceasedEmployments: Option[TaxCodeIncomeTotal] = None,
  hasDuplicateEmploymentNames: Boolean,
  totalIncome: BigDecimal,
  totalTaxableIncome: BigDecimal,
  totalTax: BigDecimal
) {}

object TaxCodeIncomes {
  implicit val formats: OFormat[TaxCodeIncomes] = Json.format[TaxCodeIncomes]
}

case class Incomes(taxCodeIncomes: TaxCodeIncomes, noneTaxCodeIncomes: NoneTaxCodeIncomes, total: BigDecimal)

object Incomes {
  implicit val formats: OFormat[Incomes] = Json.format[Incomes]
}

case class IncreasesTax(
  incomes: Option[Incomes] = None,
  benefitsFromEmployment: Option[TaxComponent] = None,
  total: BigDecimal
)

object IncreasesTax {
  implicit val formats: OFormat[IncreasesTax] = Json.format[IncreasesTax]
}

case class ExtensionRelief(sourceAmount: BigDecimal = BigDecimal(0), reliefAmount: BigDecimal = BigDecimal(0))

object ExtensionRelief {
  implicit val formats: OFormat[ExtensionRelief] = Json.format[ExtensionRelief]
}

case class ExtensionReliefs(giftAid: Option[ExtensionRelief] = None, personalPension: Option[ExtensionRelief] = None)

object ExtensionReliefs {
  implicit val formats: OFormat[ExtensionReliefs] = Json.format[ExtensionReliefs]
}

case class DecreasesTax(
  personalAllowance: Option[BigDecimal] = None,
  personalAllowanceSourceAmount: Option[BigDecimal] = None,
  blindPerson: Option[TaxComponent] = None,
  expenses: Option[TaxComponent] = None,
  giftRelated: Option[TaxComponent] = None,
  jobExpenses: Option[TaxComponent] = None,
  miscellaneous: Option[TaxComponent] = None,
  pensionContributions: Option[TaxComponent] = None,
  paTransferredAmount: Option[BigDecimal] = None,
  paReceivedAmount: Option[BigDecimal] = None,
  paTapered: Boolean = false,
  personalSavingsAllowance: Option[TaxComponent] = None,
  total: BigDecimal
)
object DecreasesTax {
  implicit val formats: OFormat[DecreasesTax] = Json.format[DecreasesTax]
}

case class MarriageAllowance(
  marriageAllowance: BigDecimal = BigDecimal(0),
  marriageAllowanceRelief: BigDecimal = BigDecimal(0)
)
object MarriageAllowance {
  implicit val formats: OFormat[MarriageAllowance] = Json.format[MarriageAllowance]
}
case class Adjustment(codingAmount: BigDecimal = BigDecimal(0), amountInTermsOfTax: BigDecimal = BigDecimal(0))
object Adjustment {
  implicit val formats: OFormat[Adjustment] = Json.format[Adjustment]
}

case class LiabilityReductions(
  marriageAllowance: Option[MarriageAllowance] = None,
  enterpriseInvestmentSchemeRelief: Option[Adjustment] = None,
  concessionalRelief: Option[Adjustment] = None,
  maintenancePayments: Option[Adjustment] = None,
  doubleTaxationRelief: Option[Adjustment] = None
)
object LiabilityReductions {
  implicit val formats: OFormat[LiabilityReductions] = Json.format[LiabilityReductions]
}

case class LiabilityAdditions(
  excessGiftAidTax: Option[Adjustment] = None,
  excessWidowsAndOrphans: Option[Adjustment] = None,
  pensionPaymentsAdjustment: Option[Adjustment] = None
)
object LiabilityAdditions {
  implicit val formats: OFormat[LiabilityAdditions] = Json.format[LiabilityAdditions]
}

case class TotalLiability(
  nonCodedIncome: Option[Tax] = None,
  totalTax: BigDecimal,
  underpaymentPreviousYear: BigDecimal = BigDecimal(0),
  inYearAdjustment: Option[BigDecimal] = None,
  outstandingDebt: BigDecimal = BigDecimal(0),
  childBenefitAmount: BigDecimal = BigDecimal(0),
  childBenefitTaxDue: BigDecimal = BigDecimal(0),
  taxOnBankBSInterest: Option[BigDecimal] = None,
  taxCreditOnUKDividends: Option[BigDecimal] = None,
  taxCreditOnForeignInterest: Option[BigDecimal] = None,
  taxCreditOnForeignIncomeDividends: Option[BigDecimal] = None,
  liabilityReductions: Option[LiabilityReductions] = None,
  liabilityAdditions: Option[LiabilityAdditions] = None
)

object TotalLiability {
  implicit val formats: OFormat[TotalLiability] = Json.format[TotalLiability]
}

case class GateKeeperRule(gateKeeperType: Option[Int], id: Option[Int], description: Option[String])

object GateKeeperRule {
  implicit val formats: OFormat[GateKeeperRule] = Json.format[GateKeeperRule]
}

case class GateKeeper(gateKeepered: Boolean = false, gateKeeperResults: List[GateKeeperRule])

object GateKeeper {
  implicit val formats: OFormat[GateKeeper] = Json.format[GateKeeper]
}

case class CeasedEmploymentDetails(
  endDate: Option[LocalDate],
  isPension: Option[Boolean],
  ceasedStatus: Option[String],
  employmentStatus: Option[Int]
)

object CeasedEmploymentDetails {
  implicit val formats: OFormat[CeasedEmploymentDetails] = Json.format[CeasedEmploymentDetails]
}

case class TaxSummaryDetails(
  nino: String,
  version: Int,
  accounts: Seq[AnnualAccount] = Nil,
  increasesTax: Option[IncreasesTax] = None,
  decreasesTax: Option[DecreasesTax] = None,
  totalLiability: Option[TotalLiability] = None,
  adjustedNetIncome: BigDecimal = BigDecimal(0),
  extensionReliefs: Option[ExtensionReliefs] = None,
  gateKeeper: Option[GateKeeper] = None,
  taxCodeDetails: Option[TaxCodeDetails] = None,
  incomeData: Option[IncomeData] = None,
  cyPlusOneChange: Option[CYPlusOneChange] = None,
  cyPlusOneSummary: Option[TaxSummaryDetails] = None,
  ceasedEmploymentDetail: Option[CeasedEmploymentDetails] = None
)
