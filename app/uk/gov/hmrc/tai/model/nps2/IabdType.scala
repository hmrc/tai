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

  val set: Seq[IabdType] = Seq(
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

  lazy val hipMapping: Map[Int, String] = Map[Int, String](
    1   -> "Gift-Aid-Payments-(001)",
    2   -> "Gift-Aid-treated-as-paid-in-previous-tax-year-(002)",
    3   -> "One-off-Gift-Aid-Payments-(003)",
    4   -> "Gift-Aid-after-end-of-tax-year-(004)",
    5   -> "Personal-Pension-Payments-(005)",
    6   -> "Maintenance-Payments-(006)",
    7   -> "Total-gift-aid-Payments-(007)",
    8   -> "Employer-Provided-Services-(008)",
    9   -> "Widows-and-Orphans-(009)",
    10  -> "Balancing-Charge-(010)",
    11  -> "Loan-Interest-Amount-(011)",
    12  -> "Death,-Sickness-or-Funeral-Benefits-(012)",
    13  -> "Married-Couples-Allowance-(MAA)-(013)",
    14  -> "Blind-Persons-Allowance-(014)",
    15  -> "BPA-Received-from-Spouse/Civil-Partner-(015)",
    16  -> "Community-Investment-Tax-Credit-(016)",
    17  -> "Gifts-of-Shares-to-Charity-(017)",
    18  -> "Retirement-Annuity-Payments-(018)",
    19  -> "Non-Coded-Income-(019)",
    20  -> "Commission-(020)",
    21  -> "Other-Income-(Earned)-(021)",
    22  -> "Other-Income-(Not-Earned)-(022)",
    23  -> "Part-Time-Earnings-(023)",
    24  -> "Tips-(024)",
    25  -> "Other-Earnings-(025)",
    26  -> "Casual-Earnings-(026)",
    27  -> "New-Estimated-Pay-(027)",
    28  -> "Benefit-in-Kind-(028)",
    29  -> "Car-Fuel-Benefit-(029)",
    30  -> "Medical-Insurance-(030)",
    31  -> "Car-Benefit-(031)",
    32  -> "Telephone-(032)",
    33  -> "Service-Benefit-(033)",
    34  -> "Taxable-Expenses-Benefit-(034)",
    35  -> "Van-Benefit-(035)",
    36  -> "Van-Fuel-Benefit-(036)",
    37  -> "Beneficial-Loan-(037)",
    38  -> "Accommodation-(038)",
    39  -> "Assets-(039)",
    40  -> "Asset-Transfer-(040)",
    41  -> "Educational-Services-(041)",
    42  -> "Entertaining-(042)",
    43  -> "Expenses-(043)",
    44  -> "Mileage-(044)",
    45  -> "Non-qualifying-Relocation-Expenses-(045)",
    46  -> "Nursery-Places-(046)",
    47  -> "Other-Items-(047)",
    48  -> "Payments-on-Employee's-Behalf-(048)",
    49  -> "Personal-Incidental-Expenses-(049)",
    50  -> "Qualifying-Relocation-Expenses-(050)",
    51  -> "Employer-Provided-Professional-Subscription-(051)",
    52  -> "Income-Tax-Paid-but-not-deducted-from-Director's-Remuneration-(052)",
    53  -> "Travel-and-Subsistence-(053)",
    54  -> "Vouchers-and-Credit-Cards-(054)",
    55  -> "Job-Expenses-(055)",
    56  -> "Flat-Rate-Job-Expenses-(056)",
    57  -> "Professional-Subscriptions-(057)",
    58  -> "Hotel-and-Meal-Expenses-(058)",
    59  -> "Other-Expenses-(059)",
    60  -> "Vehicle-Expenses-(060)",
    61  -> "Mileage-Allowance-Relief-(061)",
    62  -> "Foreign-Dividend-Income-(062)",
    63  -> "Foreign-Property-Income-(063)",
    64  -> "Foreign-Interest-&-Other-Savings-(064)",
    65  -> "Foreign-Pensions-&-Other-Income-(065)",
    66  -> "State-Pension-(066)",
    67  -> "Occupational-Pension-(067)",
    68  -> "Public-Services-Pension-(068)",
    69  -> "Forces-Pension-(069)",
    70  -> "Personal-Pension-Annuity-(070)",
    71  -> "Lump-Sum-Deferral-(071)",
    72  -> "Profit-(072)",
    73  -> "Loss-(073)",
    74  -> "Loss-Brought-Forward-from-earlier-tax-year-(074)",
    75  -> "Taxed-Interest-(075)",
    76  -> "UK-Dividend-(076)",
    77  -> "Unit-Trust-(077)",
    78  -> "Stock-Dividend-(078)",
    79  -> "National-Savings-(079)",
    80  -> "Savings-Bond-(080)",
    81  -> "Purchased-Life-Annuities-(081)",
    82  -> "Untaxed-Interest-(082)",
    83  -> "Incapacity-Benefit-(083)",
    84  -> "Job-Seekers-Allowance-(084)",
    85  -> "Other-Benefit-(085)",
    86  -> "Trusts,-Settlements-&-Estates-at-Trust-Rate-(086)",
    87  -> "Trusts,-Settlements-&-Estates-at-Basic-Rate-(087)",
    88  -> "Trusts,-Settlements-&-Estates-at-Lower-Rate-(088)",
    89  -> "Trusts,-Settlements-&-Estates-at-Non-payable-Dividend-Rate-(089)",
    90  -> "Venture-Capital-Trust-(090)",
    91  -> "BPA-Transferred-to-Spouse/Civil-Partner-(091)",
    93  -> "Trade-Union-Subscriptions-(093)",
    94  -> "Chargeable-Event-Gain-(094)",
    95  -> "Gift-Aid-Adjustment-(095)",
    96  -> "Widows-and-Orphans-Adjustment-(096)",
    97  -> "Married-Couples-Allowance-to-Wife-(MAW)-(097)",
    98  -> "Double-Taxation-Relief-(098)",
    99  -> "Concession-Relief-(099)",
    100 -> "Enterprise-Investment-Scheme-(100)",
    101 -> "Early-Years-Adjustment-(101)",
    102 -> "Loss-relief-(102)",
    103 -> "Estimated-Income-(103)",
    104 -> "Foreign-Pension-Allowance-(104)",
    105 -> "Allowances-Allocated-Elsewhere-(105)",
    106 -> "Allowances-Allocated-Here-(106)",
    107 -> "Estimated-NIB-(107)",
    108 -> "Estimated-IB-(108)",
    109 -> "Married-Couples-Allowance-(MAE)-(109)",
    110 -> "Married-Couples-Allowance-(MCCP)-(110)",
    111 -> "Surplus-Married-Couples-Allowance-(MAT)-(111)",
    112 -> "Surplus-Married-Couples-Allowance-to-Wife-(WAA)-(112)",
    113 -> "Surplus-Married-Couples-Allowance-to-Wife-(WAE)-(113)",
    114 -> "Married-Couples-Allowance-to-Wife-(WMA)-(114)",
    115 -> "Friendly-Society-Subscriptions-(115)",
    116 -> "Higher-Rate-Adjustment-(116)",
    117 -> "Non-Cash-Benefit-(117)",
    118 -> "Personal-Allowance-(PA)-(118)",
    119 -> "Personal-Allowance-Aged-(PAA)-(119)",
    120 -> "Personal-Allowance-Elderly-(PAE)-(120)",
    121 -> "Starting-Rate-Adjustment-(LRA)-(121)",
    122 -> "Starting-Rate-Band-Adjustment-(ELR)-(122)",
    123 -> "Employment-and-Support-Allowance-(123)",
    124 -> "Child-Benefit-(124)",
    125 -> "Bereavement-Allowance-(125)",
    126 -> "PA-transferred-to-spouse/civil-partner-(126)",
    127 -> "PA-received-from-spouse/civil-partner-(127)",
    128 -> "Personal-Savings-Allowance-(128)",
    129 -> "Dividend-Tax-(129)",
    130 -> "Relief-At-Source-(RAS)-(130)"
  )

  def apply(i: Int): IabdType = set.find(_.code == i).getOrElse(Unknown(i))

}
