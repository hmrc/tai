/*
 * Copyright 2018 HM Revenue & Customs
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

import uk.gov.hmrc.tai.model.nps2.AllowanceType._
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.{DeductionType, IabdType, IabdUpdateSource}

object TaiConstants {

  val ApplicationName = "tai"

  val DAYS_IN_YEAR = 365

  val PrimaryEmployment = 1
  val SecondaryEmployment = 2

  val IADB_TYPE_OTHER_PENSIONS = List(
    Some(PersonalPensionPayments.code),Some(LumpSumDeferral.code), Some(PersonalPensionAnnuity.code), Some(ForcesPension.code),
    Some(PublicServicesPension.code), Some(OccupationalPension.code)
  )

  val IADB_TYPES_TO_NOT_GROUP= List(
    Some(StatePension.code), Some(IncapacityBenefit.code),
    Some(JobSeekersAllowance.code), Some(EmploymentAndSupportAllowance.code), Some(NewEstimatedPay.code),
    Some(PersonalAllowancePA.code), Some(PersonalAllowanceAgedPAA.code), Some(PersonalAllowanceElderlyPAE.code),
    Some(OtherBenefit.code),
    Some(Loss.code), Some(LossBroughtForwardFromEarlierTaxYear.code))

  val IABD_TYPE_OTHER_INCOME = List(
    Some(BalancingCharge.code), Some(NonCodedIncome.code), Some(Commission.code),
    Some(OtherIncomeEarned.code), Some(OtherIncomeNotEarned.code), Some(PartTimeEarnings.code),
    Some(Tips.code), Some(OtherEarnings.code), Some(CasualEarnings.code),
    Some(ForeignPropertyIncome.code), Some(ForeignPensionsAndOtherIncome.code), Some(Profit.code),
    Some(TrustsSettlementsAndEstatesAtTrustRate.code), Some(TrustsSettlementsAndEstatesAtBasicRate.code),
    Some(TrustsSettlementsAndEstatesAtLowerRate.code), Some(TrustsSettlementsAndEstatesAtNonPayableDividendRate.code), Some(ChargeableEventGain.code),
    Some(NationalSavings.code), Some(SavingsBond.code), Some(PurchasedLifeAnnuities.code),
    Some(UnitTrust.code), Some(StockDividend.code), Some(ForeignInterestAndOtherSavings.code),
    Some(ForeignDividendIncome.code), Some(MaintenancePayments.code), Some(DoubleTaxationRelief.code),
    Some(ConcessionRelief.code), Some(EnterpriseInvestmentScheme.code), Some(GiftAidAdjustment.code),
    Some(BereavementAllowance.code)
  )

  val IADB_TYPE_BENEFITS_IN_KIND_TOTAL = Some(BenefitInKind.code)

  val IADB_TYPE_BENEFITS_IN_KIND = List(
    Some(Accommodation.code), Some(Assets.code), Some(AssetTransfer.code), Some(EducationalServices.code),
    Some(EmployerProvidedProfessionalSubscription.code), Some(EmployerProvidedServices.code), Some(Entertaining.code), Some(Expenses.code),
    Some(IncomeTaxPaidButNotDeductedFromDirectorsRemuneration.code), Some(Mileage.code), Some(NonQualifyingRelocationExpenses.code), Some(NurseryPlaces.code),
    Some(OtherItems.code), Some(PaymentsOnEmployeesBehalf.code), Some(PersonalIncidentalExpenses.code), Some(QualfyingRelocationExpenses.code),
    Some(TravelAndSubsistence.code), Some(VouchersAndCreditCards.code)
  )

  val IABD_TYPE_BENEFITS_FROM_EMPLOYMENT = List(
    Some(CarFuelBenefit.code), Some(MedicalInsurance.code), Some(CarBenefit.code),
    Some(Telephone.code), Some(ServiceBenefit.code), Some(TaxableExpensesBenefit.code),
    Some(VanBenefit.code), Some(VanFuelBenefit.code), Some(BeneficialLoan.code),
    Some(NonCashBenefit.code))

  //val 92 -Earlier Year's Adjustment

  val IABD_TYPE_BLIND_PERSON = List(Some(IabdType.BlindPersonsAllowance.code), Some(BpaReceivedFromSpouseOrCivilPartner.code))
  val IABD_TYPE_JOB_EXPENSES = List(Some(IabdType.JobExpenses.code), Some(HotelAndMealExpenses.code), Some(OtherExpenses.code),
    Some(VehicleExpenses.code), Some(MileageAllowanceRelief.code))
  val IABD_TYPE_GIFT_RELATED = List(Some(GiftAidAdjustment.code), Some(GiftsSharesCharity.code), Some(ConcessionRelief.code))
  val IABD_TYPE_EXPENSES = List(Some(IabdType.FlatRateJobExpenses.code), Some(IabdType.ProfessionalSubscriptions.code), Some(EarlyYearsAdjustment.code))
  val IABD_TYPE_MISCELLANEOUS = List(Some(MaintenancePayments.code), Some(LoanInterestAmount.code), Some(TradeUnionSubscriptions.code),
    Some(CommunityInvestmentTaxCredit.code), Some(VentureCapitalTrust.code), Some(EnterpriseInvestmentScheme.code), Some(IabdType.LossRelief.code),
    Some(IabdType.DoubleTaxationRelief.code))
  val IABD_TYPE_PENSION_CONTRIBUTIONS = List(Some(PersonalPensionPayments.code), Some(RetirementAnnuityPayments.code),
    Some(IabdType.ForeignPensionAllowance.code))
  val IABD_TYPE_DIVIDENDS = List(Some(UkDividend.code), Some(UnitTrust.code), Some(StockDividend.code))
  val IABD_TYPE_BANK_INTEREST = List(Some(BankOrBuildingSocietyInterest.code), Some(PurchasedLifeAnnuities.code))
  val IABD_TYPE_UNTAXED_INTEREST = List(Some(UntaxedInterest.code), Some(SavingsBond.code), Some(NationalSavings.code))

  //Iabd types do not overwrite est pay - Manual Telephone 15, Letter 16, Email 17, Agent Contact 18, Other Form 24, Internet 39, Information letter 40
  val IABD_TYPES_DO_NOT_OVERWRITE = List(Some(IabdUpdateSource.ManualTelephone.code),
    Some(IabdUpdateSource.Letter.code), Some(IabdUpdateSource.Email.code),
    Some(IabdUpdateSource.AgentContact.code), Some(IabdUpdateSource.OtherForm.code),
    Some(IabdUpdateSource.Internet.code), Some(IabdUpdateSource.InformationLetter.code))

  val JSA_TAX_DISTRICT = 921
  val JSA_PAYE_NUMBER = "LDN"

  val JSA_STUDENTS_TAX_DISTRICT = 921
  val JSA_STUDENTS_PAYE_NUMBER = "LDS"

  val JSA_NEW_DWP_TAX_DISTRICT = 475
  val JSA_NEW_DWP_PAYE_NUMBER = "BB00987"

  val ESA_TAX_DISTRICT = 267
  val ESA_PAYE_NUMBER = "ESA500"

  val PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT = 406
  val PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER = "JA34863"

  val INCAPACITY_BENEFIT_TAX_DISTRICT = 892
  val INCAPACITY_BENEFIT_PAYE_NUMBER = "BA500"

  val PENSION_LUMP_SUM_TAX_DISTRICT = 267
  val PENSION_LUMP_SUM_PAYE_NUMBER = "LS500"

  val CHILDBENEFIT_LOWER_THRESHOLD = BigDecimal(50000)
  val CHILDBENEFIT_HIGHER_THRESHOLD = BigDecimal(60000)
  val CHILDBENEIT_PERC_STEP = BigDecimal(1)

  val GATEKEEPER_TYPE_EMPLOYMENT = 1
  val GATEKEEPER_TYPE_EMP_ID_ALL_EMPS_ENDED = 3

  val mciGateKeeperType = 6
  val mciGatekeeperId = 6
  val mciGatekeeperDescr = "Manual Correspondence Indicator"

  val ADDITIONAL_TAX_CODE_ALLOWANCE_TYPES = List(
    PersonalPensionRelief.id,
    GiftAidPayment.id,
    MarriedCouplesAllowance.id,
    MarriedCouplesAllowance2.id,
    MarriedCouplesAllowance3.id,
    MarriedCouplesAllowance4.id,
    MarriedCouplesAllowance5.id,
    EnterpriseInvestmentSchemeRelief.id,
    ConcessionalRelief.id,
    MaintenancePayment.id,
    DoubleTaxationReliefAllowance.id)

  val FILTERED_OUT_DEDUCTIONS= List(
    DeductionType.OtherEarningsOrPension.id,
    DeductionType.PersonalAllowanceTransferred.id)

  val EMP_BEN_DEDUCTIONS: List[Int] = List(
    DeductionType.EmployerBenefits.id,
    DeductionType.LoanFromYourEmployer.id,
    DeductionType.CarBenefit.id,
    DeductionType.CarFuel.id,
    DeductionType.MedicalInsurance.id,
    DeductionType.NonCashBenefits.id,
    DeductionType.ServiceBenefit.id,
    DeductionType.TaxableExpensesPayments.id,
    DeductionType.Telephone.id,
    DeductionType.VanBenefit.id,
    DeductionType.VanFuelBenefit.id)

  val STANDARD_DATE_FORMAT = "dd/MM/yyyy"
  val DEFAULT_CY_PLUS_ONE_ENABLED_DATE = "05/01"

  val CEASED_MINUS_ONE = "CY-1"
  val CEASED_MINUS_TWO = "CY-2"
  val CEASED_MINUS_THREE  = "CY-3"

  val DEFAULT_PRIMARY_PAY = 15000
  val DEFAULT_SECONDARY_PAY = 5000
  val contentType: String = "application/json"
}

trait MongoConstants {
  val CarBenefitKey = "CarBenefit"
  val TaxAccountBaseKey: String = "TaxAccountData"
  val IabdMongoKey = "IabdsData"
}

object IFormConstants {
  val DateFormat = "d MMMM yyyy"
  val Yes = "Yes"
  val No = "No"
  val AddEmploymentAuditTxnName = "AddEmploymentRequest"
  val IncorrectEmploymentAuditTxnName = "IncorrectEmploymentRequest"
  val UpdatePreviousYearIncomeAuditTxnName = "UpdatePreviousYearIncomeRequest"
  val RemoveBankAccountRequest = "RemoveBankAccountRequest"
  val UpdateBankAccountRequest = "UpdateBankAccountRequest"
  val IncorrectEmploymentSubmissionKey = "IncorrectEmployment"
  val UpdatePreviousYearIncomeSubmissionKey = "UpdatePreviousYearIncome"
  val RemoveCompanyBenefitSubmissionKey = "RemoveCompanyBenefit"
  val RemoveCompanyBenefitAuditTxnName = "RemoveCompanyBenefitRequest"
  val AddPensionProviderSubmissionKey = "AddPensionProvider"
  val AddPensionProviderAuditTxnName = "AddPensionProviderRequest"
}

trait RequestQueryFilter {
  val FilterQuery = "filter"
  val TaxFreeAmountFilter = "tax-free-amount"
}

trait NpsExceptions {
  val CodingCalculationCYPlusOne = "Cannot perform a Coding Calculation for CY+1"
  val CodingCalculationNoPrimary = "Cannot complete a Coding Calculation without a Primary Employment"
  val CodingCalculationNoEmpCY = "No Employments recorded for current tax year"
}

trait HodsSource {
  val DesSource = 39
  val NpsSource = 0
}

trait IabdTypeConstants {
  val NewEstimatedPay = 27
}
