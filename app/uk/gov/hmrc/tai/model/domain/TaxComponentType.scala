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

package uk.gov.hmrc.tai.model.domain

import play.api.libs.json._

sealed trait TaxComponentType

sealed trait AllowanceComponentType extends TaxComponentType
sealed trait BenefitComponentType extends TaxComponentType
sealed trait DeductionComponentType extends TaxComponentType
sealed trait IncomeComponentType extends TaxComponentType

sealed trait TaxCodeIncomeComponentType extends IncomeComponentType

object TaxCodeIncomeComponentType {
  def apply(value: String): TaxCodeIncomeComponentType = value match {
    case "EmploymentIncome"         => EmploymentIncome
    case "PensionIncome"            => PensionIncome
    case "JobSeekerAllowanceIncome" => JobSeekerAllowanceIncome
    case "OtherIncome"              => OtherIncome
    case _                          => throw new IllegalArgumentException("Invalid Tax component type")
  }
}

sealed trait NonTaxCodeIncomeComponentType extends IncomeComponentType

//Coding components
case object GiftAidPayments extends AllowanceComponentType
case object PersonalPensionPayments extends AllowanceComponentType
case object MaintenancePayments extends AllowanceComponentType
case object LoanInterestAmount extends AllowanceComponentType
case object BlindPersonsAllowance extends AllowanceComponentType
case object BpaReceivedFromSpouseOrCivilPartner extends AllowanceComponentType
case object CommunityInvestmentTaxCredit extends AllowanceComponentType
case object GiftsSharesCharity extends AllowanceComponentType
case object RetirementAnnuityPayments extends AllowanceComponentType
case object JobExpenses extends AllowanceComponentType
case object FlatRateJobExpenses extends AllowanceComponentType
case object ProfessionalSubscriptions extends AllowanceComponentType
case object HotelAndMealExpenses extends AllowanceComponentType
case object OtherExpenses extends AllowanceComponentType
case object VehicleExpenses extends AllowanceComponentType
case object MileageAllowanceRelief extends AllowanceComponentType
case object DoubleTaxationRelief extends AllowanceComponentType
case object ConcessionRelief extends AllowanceComponentType
case object EnterpriseInvestmentScheme extends AllowanceComponentType
case object EarlyYearsAdjustment extends AllowanceComponentType
case object LossRelief extends AllowanceComponentType
case object ForeignPensionAllowance extends AllowanceComponentType
case object MarriedCouplesAllowanceMAE extends AllowanceComponentType
case object MarriedCouplesAllowanceMCCP extends AllowanceComponentType
case object VentureCapitalTrust extends AllowanceComponentType
case object SurplusMarriedCouplesAllowanceToWifeWAE extends AllowanceComponentType
case object MarriedCouplesAllowanceToWifeWMA extends AllowanceComponentType
case object PersonalAllowancePA extends AllowanceComponentType
case object PersonalAllowanceAgedPAA extends AllowanceComponentType
case object PersonalAllowanceElderlyPAE extends AllowanceComponentType
case object MarriageAllowanceReceived extends AllowanceComponentType

case object MarriedCouplesAllowanceToWifeMAW extends DeductionComponentType
case object BalancingCharge extends DeductionComponentType
case object GiftAidAdjustment extends DeductionComponentType
case object ChildBenefit extends DeductionComponentType
case object MarriageAllowanceTransferred extends DeductionComponentType
case object DividendTax extends DeductionComponentType
case object UnderPaymentFromPreviousYear extends DeductionComponentType
case object OutstandingDebt extends DeductionComponentType
case object EstimatedTaxYouOweThisYear extends DeductionComponentType
case object UnderpaymentRestriction extends DeductionComponentType
case object HigherPersonalAllowanceRestriction extends DeductionComponentType
case object AdjustmentToRateBand extends DeductionComponentType

case object BenefitInKind extends BenefitComponentType
case object CarFuelBenefit extends BenefitComponentType
case object MedicalInsurance extends BenefitComponentType
case object CarBenefit extends BenefitComponentType
case object Telephone extends BenefitComponentType
case object ServiceBenefit extends BenefitComponentType
case object TaxableExpensesBenefit extends BenefitComponentType
case object VanBenefit extends BenefitComponentType
case object VanFuelBenefit extends BenefitComponentType
case object BeneficialLoan extends BenefitComponentType
case object Accommodation extends BenefitComponentType
case object Assets extends BenefitComponentType
case object AssetTransfer extends BenefitComponentType
case object EducationalServices extends BenefitComponentType
case object Entertaining extends BenefitComponentType
case object Expenses extends BenefitComponentType
case object Mileage extends BenefitComponentType
case object NonQualifyingRelocationExpenses extends BenefitComponentType
case object NurseryPlaces extends BenefitComponentType
case object OtherItems extends BenefitComponentType
case object PaymentsOnEmployeesBehalf extends BenefitComponentType
case object PersonalIncidentalExpenses extends BenefitComponentType
case object QualifyingRelocationExpenses extends BenefitComponentType
case object EmployerProvidedProfessionalSubscription extends BenefitComponentType
case object IncomeTaxPaidButNotDeductedFromDirectorsRemuneration extends BenefitComponentType
case object TravelAndSubsistence extends BenefitComponentType
case object VouchersAndCreditCards extends BenefitComponentType
case object NonCashBenefit extends BenefitComponentType
case object EmployerProvidedServices extends BenefitComponentType

case object Commission extends NonTaxCodeIncomeComponentType
case object OtherIncomeEarned extends NonTaxCodeIncomeComponentType
case object OtherIncomeNotEarned extends NonTaxCodeIncomeComponentType
case object PartTimeEarnings extends NonTaxCodeIncomeComponentType
case object Tips extends NonTaxCodeIncomeComponentType
case object OtherEarnings extends NonTaxCodeIncomeComponentType
case object StatePension extends NonTaxCodeIncomeComponentType
case object OccupationalPension extends NonTaxCodeIncomeComponentType
case object PublicServicesPension extends NonTaxCodeIncomeComponentType
case object ForcesPension extends NonTaxCodeIncomeComponentType
case object PersonalPensionAnnuity extends NonTaxCodeIncomeComponentType
case object Profit extends NonTaxCodeIncomeComponentType
case object BankOrBuildingSocietyInterest extends NonTaxCodeIncomeComponentType
case object UntaxedInterestIncome extends NonTaxCodeIncomeComponentType
case object IncapacityBenefit extends NonTaxCodeIncomeComponentType
case object JobSeekersAllowance extends NonTaxCodeIncomeComponentType
case object EmploymentAndSupportAllowance extends NonTaxCodeIncomeComponentType
case object ForeignDividendIncome extends NonTaxCodeIncomeComponentType
case object ForeignPropertyIncome extends NonTaxCodeIncomeComponentType
case object ForeignInterestAndOtherSavings extends NonTaxCodeIncomeComponentType
case object ForeignPensionsAndOtherIncome extends NonTaxCodeIncomeComponentType
case object NonCodedIncome extends NonTaxCodeIncomeComponentType
case object CasualEarnings extends NonTaxCodeIncomeComponentType
case object UkDividend extends NonTaxCodeIncomeComponentType
case object UnitTrust extends NonTaxCodeIncomeComponentType
case object StockDividend extends NonTaxCodeIncomeComponentType
case object UntaxedInterestIABD extends NonTaxCodeIncomeComponentType
case object NationalSavings extends NonTaxCodeIncomeComponentType
case object SavingsBond extends NonTaxCodeIncomeComponentType
case object PurchasedLifeAnnuities extends NonTaxCodeIncomeComponentType

//Tax-code Incomes
case object EmploymentIncome extends TaxCodeIncomeComponentType
case object PensionIncome extends TaxCodeIncomeComponentType
case object JobSeekerAllowanceIncome extends TaxCodeIncomeComponentType
case object OtherIncome extends TaxCodeIncomeComponentType

object TaxComponentType {
  implicit val formatTaxComponentType: Format[TaxComponentType] = new Format[TaxComponentType] {
    override def reads(json: JsValue): JsSuccess[TaxComponentType] = ???
    override def writes(taxComponentType: TaxComponentType) = JsString(taxComponentType.toString)
  }
}

object BenefitComponentType {
  implicit val formatBenefitTaxComponentType: Format[BenefitComponentType] = new Format[BenefitComponentType] {
    override def reads(json: JsValue): JsSuccess[BenefitComponentType] = ???
    override def writes(benefitComponentType: BenefitComponentType) = JsString(benefitComponentType.toString)
  }
}

object NonTaxCodeIncomeComponentType {
  implicit val formatNonTaxCodeIncomeComponentType: Format[NonTaxCodeIncomeComponentType] = new Format[NonTaxCodeIncomeComponentType] {
    override def reads(json: JsValue): JsSuccess[NonTaxCodeIncomeComponentType] = ???
    override def writes(nonTaxCodeIncomeComponentType: NonTaxCodeIncomeComponentType) =
      JsString(nonTaxCodeIncomeComponentType.toString)
  }
}
