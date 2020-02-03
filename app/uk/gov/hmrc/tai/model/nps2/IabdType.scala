/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.nps2

sealed trait IabdType {
  def code: Int
}

object IabdType {
  object GiftAidPayments extends IabdType { val code = 1 }
  object GiftAidTreatedAsPaidInPreviousTaxYear extends IabdType { val code = 2 }
  object OneOffGiftAidPayments extends IabdType { val code = 3 }
  object GiftAidAfterEndOfTaxYear extends IabdType { val code = 4 }
  object PersonalPensionPayments extends IabdType { val code = 5 }
  object MaintenancePayments extends IabdType { val code = 6 }
  object TotalGiftAidPayments extends IabdType { val code = 7 }
  object EmployerProvidedServices extends IabdType { val code = 8 }
  object BalancingCharge extends IabdType { val code = 10 }
  object LoanInterestAmount extends IabdType { val code = 11 }
  object DeathSicknessOrFuneralBenefits extends IabdType { val code = 12 }
  object MarriedCouplesAllowanceMAA extends IabdType { val code = 13 }
  object BlindPersonsAllowance extends IabdType { val code = 14 }
  object BpaReceivedFromSpouseOrCivilPartner extends IabdType { val code = 15 }
  object CommunityInvestmentTaxCredit extends IabdType { val code = 16 }
  object GiftsSharesCharity extends IabdType { val code = 17 }
  object RetirementAnnuityPayments extends IabdType { val code = 18 }
  object NonCodedIncome extends IabdType { val code = 19 }
  object Commission extends IabdType { val code = 20 }
  object OtherIncomeEarned extends IabdType { val code = 21 }
  object OtherIncomeNotEarned extends IabdType { val code = 22 }
  object PartTimeEarnings extends IabdType { val code = 23 }
  object Tips extends IabdType { val code = 24 }
  object OtherEarnings extends IabdType { val code = 25 }
  object CasualEarnings extends IabdType { val code = 26 }
  object NewEstimatedPay extends IabdType { val code = 27 }
  object BenefitInKind extends IabdType { val code = 28 }
  object CarFuelBenefit extends IabdType { val code = 29 }
  object MedicalInsurance extends IabdType { val code = 30 }
  object CarBenefit extends IabdType { val code = 31 }
  object Telephone extends IabdType { val code = 32 }
  object ServiceBenefit extends IabdType { val code = 33 }
  object TaxableExpensesBenefit extends IabdType { val code = 34 }
  object VanBenefit extends IabdType { val code = 35 }
  object VanFuelBenefit extends IabdType { val code = 36 }
  object BeneficialLoan extends IabdType { val code = 37 }
  object Accommodation extends IabdType { val code = 38 }
  object Assets extends IabdType { val code = 39 }
  object AssetTransfer extends IabdType { val code = 40 }
  object EducationalServices extends IabdType { val code = 41 }
  object Entertaining extends IabdType { val code = 42 }
  object Expenses extends IabdType { val code = 43 }
  object Mileage extends IabdType { val code = 44 }
  object NonQualifyingRelocationExpenses extends IabdType { val code = 45 }
  object NurseryPlaces extends IabdType { val code = 46 }
  object OtherItems extends IabdType { val code = 47 }
  object PaymentsOnEmployeesBehalf extends IabdType { val code = 48 }
  object PersonalIncidentalExpenses extends IabdType { val code = 49 }
  object QualfyingRelocationExpenses extends IabdType { val code = 50 }
  object EmployerProvidedProfessionalSubscription extends IabdType {
    val code = 51
  }
  object IncomeTaxPaidButNotDeductedFromDirectorsRemuneration extends IabdType {
    val code = 52
  }
  object TravelAndSubsistence extends IabdType { val code = 53 }
  object VouchersAndCreditCards extends IabdType { val code = 54 }
  object JobExpenses extends IabdType { val code = 55 }
  object FlatRateJobExpenses extends IabdType { val code = 56 }
  object ProfessionalSubscriptions extends IabdType { val code = 57 }
  object HotelAndMealExpenses extends IabdType { val code = 58 }
  object OtherExpenses extends IabdType { val code = 59 }
  object VehicleExpenses extends IabdType { val code = 60 }
  object MileageAllowanceRelief extends IabdType { val code = 61 }
  object ForeignDividendIncome extends IabdType { val code = 62 }
  object ForeignPropertyIncome extends IabdType { val code = 63 }
  object ForeignInterestAndOtherSavings extends IabdType { val code = 64 }
  object ForeignPensionsAndOtherIncome extends IabdType { val code = 65 }
  object StatePension extends IabdType { val code = 66 }
  object OccupationalPension extends IabdType { val code = 67 }
  object PublicServicesPension extends IabdType { val code = 68 }
  object ForcesPension extends IabdType { val code = 69 }
  object PersonalPensionAnnuity extends IabdType { val code = 70 }
  object LumpSumDeferral extends IabdType { val code = 71 }
  object Profit extends IabdType { val code = 72 }
  object Loss extends IabdType { val code = 73 }
  object LossBroughtForwardFromEarlierTaxYear extends IabdType { val code = 74 }
  object BankOrBuildingSocietyInterest extends IabdType { val code = 75 }
  object UkDividend extends IabdType { val code = 76 }
  object UnitTrust extends IabdType { val code = 77 }
  object StockDividend extends IabdType { val code = 78 }
  object NationalSavings extends IabdType { val code = 79 }
  object SavingsBond extends IabdType { val code = 80 }
  object PurchasedLifeAnnuities extends IabdType { val code = 81 }
  object UntaxedInterest extends IabdType { val code = 82 }
  object IncapacityBenefit extends IabdType { val code = 83 }
  object JobSeekersAllowance extends IabdType { val code = 84 }
  object OtherBenefit extends IabdType { val code = 85 }
  object TrustsSettlementsAndEstatesAtTrustRate extends IabdType {
    val code = 86
  }
  object TrustsSettlementsAndEstatesAtBasicRate extends IabdType {
    val code = 87
  }
  object TrustsSettlementsAndEstatesAtLowerRate extends IabdType {
    val code = 88
  }
  object TrustsSettlementsAndEstatesAtNonPayableDividendRate extends IabdType {
    val code = 89
  }
  object VentureCapitalTrust extends IabdType { val code = 90 }
  object BPATransferredSpouseCivilPartner extends IabdType { val code = 91 }
  object TradeUnionSubscriptions extends IabdType { val code = 93 }
  object ChargeableEventGain extends IabdType { val code = 94 }
  object GiftAidAdjustment extends IabdType { val code = 95 }
  object WidowsAndOrphansAdjustment extends IabdType { val code = 96 }
  object MarriedCouplesAllowanceToWifeMAW extends IabdType { val code = 97 }
  object DoubleTaxationRelief extends IabdType { val code = 98 }
  object ConcessionRelief extends IabdType { val code = 99 }
  object EnterpriseInvestmentScheme extends IabdType { val code = 100 }
  object EarlyYearsAdjustment extends IabdType { val code = 101 }
  object LossRelief extends IabdType { val code = 102 }
  object EstimatedIncome extends IabdType { val code = 103 }
  object ForeignPensionAllowance extends IabdType { val code = 104 }
  object AllowancesAllocatedElsewhere extends IabdType { val code = 105 }
  object AllowancesAllocatedHere extends IabdType { val code = 106 }
  object EstimatedNIB extends IabdType { val code = 107 }
  object EstimatedIB extends IabdType { val code = 108 }
  object MarriedCouplesAllowanceMAE extends IabdType { val code = 109 }
  object MarriedCouplesAllowanceMCCP extends IabdType { val code = 110 }
  object SurplusMarriedCouplesAllowanceMAT extends IabdType { val code = 111 }
  object SurplusMarriedCouplesAllowanceToWifeWAA extends IabdType {
    val code = 112
  }
  object SurplusMarriedCouplesAllowanceToWifeWAE extends IabdType {
    val code = 113
  }
  object MarriedCouplesAllowanceToWifeWMA extends IabdType { val code = 114 }
  object FriendlySocietySubscriptions extends IabdType { val code = 115 }
  object HigherRateAdjustment extends IabdType { val code = 116 }
  object NonCashBenefit extends IabdType { val code = 117 }
  object PersonalAllowancePA extends IabdType { val code = 118 }
  object PersonalAllowanceAgedPAA extends IabdType { val code = 119 }
  object PersonalAllowanceElderlyPAE extends IabdType { val code = 120 }
  object StartingRateAdjustmentLRA extends IabdType { val code = 121 }
  object StartingRateBandAdjustmentELR extends IabdType { val code = 122 }
  object EmploymentAndSupportAllowance extends IabdType { val code = 123 }
  object ChildBenefit extends IabdType { val code = 124 }
  object BereavementAllowance extends IabdType { val code = 125 }
  object MarriageAllowance extends IabdType { val code = 126 }
  object PersonalSavingsAllowance extends IabdType { val code = 128 }
  case class Unknown(code: Int) extends IabdType

  val set = Seq(
    GiftAidPayments,
    GiftAidTreatedAsPaidInPreviousTaxYear,
    OneOffGiftAidPayments,
    GiftAidAfterEndOfTaxYear,
    PersonalPensionPayments,
    EmployerProvidedServices,
    BalancingCharge,
    LoanInterestAmount,
    DeathSicknessOrFuneralBenefits,
    MarriedCouplesAllowanceMAA,
    BlindPersonsAllowance,
    BpaReceivedFromSpouseOrCivilPartner,
    RetirementAnnuityPayments,
    NonCodedIncome,
    Commission,
    OtherIncomeEarned,
    OtherIncomeNotEarned,
    PartTimeEarnings,
    Tips,
    OtherEarnings,
    NewEstimatedPay,
    BenefitInKind,
    CarFuelBenefit,
    MedicalInsurance,
    CarBenefit,
    Telephone,
    ServiceBenefit,
    TaxableExpensesBenefit,
    VanBenefit,
    VanFuelBenefit,
    BeneficialLoan,
    Accommodation,
    Assets,
    AssetTransfer,
    EducationalServices,
    Entertaining,
    Expenses,
    Mileage,
    NonQualifyingRelocationExpenses,
    NurseryPlaces,
    OtherItems,
    PaymentsOnEmployeesBehalf,
    PersonalIncidentalExpenses,
    QualfyingRelocationExpenses,
    EmployerProvidedProfessionalSubscription,
    IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
    TravelAndSubsistence,
    VouchersAndCreditCards,
    JobExpenses,
    FlatRateJobExpenses,
    ProfessionalSubscriptions,
    HotelAndMealExpenses,
    OtherExpenses,
    VehicleExpenses,
    MileageAllowanceRelief,
    ForeignDividendIncome,
    ForeignPropertyIncome,
    ForeignInterestAndOtherSavings,
    ForeignPensionsAndOtherIncome,
    StatePension,
    OccupationalPension,
    PublicServicesPension,
    ForcesPension,
    PersonalPensionAnnuity,
    Profit,
    LossBroughtForwardFromEarlierTaxYear,
    BankOrBuildingSocietyInterest,
    UkDividend,
    UnitTrust,
    StockDividend,
    NationalSavings,
    SavingsBond,
    PurchasedLifeAnnuities,
    UntaxedInterest,
    IncapacityBenefit,
    JobSeekersAllowance,
    TrustsSettlementsAndEstatesAtTrustRate,
    TrustsSettlementsAndEstatesAtBasicRate,
    TrustsSettlementsAndEstatesAtLowerRate,
    TrustsSettlementsAndEstatesAtNonPayableDividendRate,
    TradeUnionSubscriptions,
    GiftAidAdjustment,
    WidowsAndOrphansAdjustment,
    MarriedCouplesAllowanceToWifeMAW,
    EarlyYearsAdjustment,
    LossRelief,
    ForeignPensionAllowance,
    MarriedCouplesAllowanceMAE,
    MarriedCouplesAllowanceMCCP,
    SurplusMarriedCouplesAllowanceToWifeWAA,
    SurplusMarriedCouplesAllowanceToWifeWAE,
    MarriedCouplesAllowanceToWifeWMA,
    FriendlySocietySubscriptions,
    NonCashBenefit,
    PersonalAllowancePA,
    PersonalAllowanceAgedPAA,
    PersonalAllowanceElderlyPAE,
    EmploymentAndSupportAllowance
  )

  def apply(i: Int): IabdType = set.find { _.code == i }.getOrElse { Unknown(i) }

}
