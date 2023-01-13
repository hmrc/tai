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

object DeductionType extends Enumeration {
  val StatePensionOrBenefits: DeductionType.Value = Value(1)
  val PublicServicesPension: DeductionType.Value = Value(2)
  val ForcesPension: DeductionType.Value = Value(3)
  val OtherPension: DeductionType.Value = Value(4)
  val TaxableIncapacityBenefit: DeductionType.Value = Value(5)
  val MarriedCouplesAllowanceToYourWife: DeductionType.Value = Value(6)
  val EmployerBenefits: DeductionType.Value = Value(7)
  val CarBenefit: DeductionType.Value = Value(8)
  val VanBenefit: DeductionType.Value = Value(9)
  val CarFuel: DeductionType.Value = Value(10)
  val ServiceBenefit: DeductionType.Value = Value(11)
  val LoanFromYourEmployer: DeductionType.Value = Value(12)
  val MedicalInsurance: DeductionType.Value = Value(13)
  val Telephone: DeductionType.Value = Value(14)
  val BalancingCharge: DeductionType.Value = Value(15)
  val TaxableExpensesPayments: DeductionType.Value = Value(16)
  val OtherEarnings: DeductionType.Value = Value(17)
  val JobseekersAllowance: DeductionType.Value = Value(18)
  val PartTimeEarnings: DeductionType.Value = Value(19)
  val Tips: DeductionType.Value = Value(20)
  val Commission: DeductionType.Value = Value(21)
  val OtherEarnings2: DeductionType.Value = Value(22)
  val InterestWithoutTaxTakenOffGrossInterest: DeductionType.Value = Value(23)
  val OtherIncomeNotEarnings: DeductionType.Value = Value(24)
  val PropertyIncome: DeductionType.Value = Value(25)
  val Annuity: DeductionType.Value = Value(26)
  val PropertyIncome2: DeductionType.Value = Value(27)
  val UnderpaymentRestriction: DeductionType.Value = Value(28)
  val OtherEarningsOrPension: DeductionType.Value = Value(29)
  val GiftAidAdjustment: DeductionType.Value = Value(30)
  val WidowsAndOrphansAdjustment: DeductionType.Value = Value(31)
  val SavingsIncomeTaxableAtHigherRate: DeductionType.Value = Value(32)
  val AdjustmentToBasicRateBand: DeductionType.Value = Value(33)
  val AdjustmentToLowerRateBand: DeductionType.Value = Value(34)
  val UnderpaymentAmount: DeductionType.Value = Value(35)
  val VanFuelBenefit: DeductionType.Value = Value(36)
  val HigherPersonalAllowanceRestriction: DeductionType.Value = Value(37)
  val EmploymentSupportAllowance: DeductionType.Value = Value(38)
  val NonCashBenefits: DeductionType.Value = Value(39)
  val AdjustmentToRateBand: DeductionType.Value = Value(40)
  val OutstandingDebtRestriction: DeductionType.Value = Value(41)
  val ChildBenefit: DeductionType.Value = Value(42)
  val PersonalAllowanceTransferred: DeductionType.Value = Value(43)
  val InYearAdjustment: DeductionType.Value = Value(45)
}
