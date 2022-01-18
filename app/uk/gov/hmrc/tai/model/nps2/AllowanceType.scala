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

package uk.gov.hmrc.tai.model.nps2

object AllowanceType extends Enumeration {
  val JobExpenses: AllowanceType.Value = Value(1)
  val FlatRateJobExpenses: AllowanceType.Value = Value(2)
  val ProfessionalSubscriptions: AllowanceType.Value = Value(3)
  val PaymentsTowardsARetirementAnnuity: AllowanceType.Value = Value(4)
  val PersonalPensionRelief: AllowanceType.Value = Value(5)
  val GiftAidPayment: AllowanceType.Value = Value(6)
  val EnterpriseInvestmentSchemeRelief: AllowanceType.Value = Value(7)
  val LoanInterest: AllowanceType.Value = Value(8)
  val LossRelief: AllowanceType.Value = Value(9)
  val MaintenancePayment: AllowanceType.Value = Value(10)
  val PersonalAllowanceStandard: AllowanceType.Value = Value(11)
  val PersonalAllowanceAged: AllowanceType.Value = Value(12)
  val PersonalAllowanceElderly: AllowanceType.Value = Value(13)
  val MarriedCouplesAllowance: AllowanceType.Value = Value(15)
  val MarriedCouplesAllowance2: AllowanceType.Value = Value(16)
  val MarriedCouplesAllowance3: AllowanceType.Value = Value(17)
  val MarriedCouplesAllowance4: AllowanceType.Value = Value(18)
  val MarriedCouplesAllowanceFromHusband: AllowanceType.Value = Value(19)
  val MarriedCouplesAllowanceFromHusband2: AllowanceType.Value = Value(20)
  val MarriedCouplesAllowance5: AllowanceType.Value = Value(21)
  val BlindPersonsAllowance: AllowanceType.Value = Value(22)
  val BalanceOfTaxAllowances: AllowanceType.Value = Value(23)
  val DeathSicknessOrFuneralBenefits: AllowanceType.Value = Value(24)
  val DeathSicknessOrFuneralBenefits2: AllowanceType.Value = Value(25)
  val DeathSicknessOrFuneralBenefits3: AllowanceType.Value = Value(26)
  val StartingRateAdjustment: AllowanceType.Value = Value(27)
  val ConcessionalRelief: AllowanceType.Value = Value(28)
  val DoubleTaxationReliefAllowance: AllowanceType.Value = Value(29)
  val ForeignPensionAllowance: AllowanceType.Value = Value(30)
  val EarlierYearsAdjustment: AllowanceType.Value = Value(31)
  val PersonalAllowanceReceived: AllowanceType.Value = Value(32)
  val PersonalSavingsAllowance: AllowanceType.Value = Value(33)
}
