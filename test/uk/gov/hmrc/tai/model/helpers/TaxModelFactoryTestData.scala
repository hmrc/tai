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

package uk.gov.hmrc.tai.model.helpers

import java.time.LocalDate
import uk.gov.hmrc.tai.model.TaxCode
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps2.{AllowanceType, DeductionType}
import uk.gov.hmrc.tai.model.{IabdSummary, TaxBand}
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.nps2.Income.Ceased
import uk.gov.hmrc.tai.util.TaiConstants

trait TaxModelFactoryTestData {

  val blindPersonNpsIabdSummary = NpsIabdSummary(
    amount = Some(1000),
    `type` = Some(IabdType.BlindPersonsAllowance.code),
    npsDescription = Some("blind person npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(3)
  )

  val flatRateExpensesNpsIabdSummary = NpsIabdSummary(
    amount = Some(800),
    `type` = Some(IabdType.FlatRateJobExpenses.code),
    npsDescription = Some("flat rate npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(2)
  )

  val giftAidNpsIabdSummary = NpsIabdSummary(
    amount = Some(300),
    `type` = Some(IabdType.GiftAidAdjustment.code),
    npsDescription = Some("gift aid npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(2)
  )

  val jobExpencesNpsIabdSummary = NpsIabdSummary(
    amount = Some(400),
    `type` = Some(IabdType.JobExpenses.code),
    npsDescription = Some("job expences npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(2)
  )

  val miscNpsIabdSummary = NpsIabdSummary(
    amount = Some(700),
    `type` = Some(IabdType.MaintenancePayments.code),
    npsDescription = Some("misc payments npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(2)
  )

  val personalPensionNpsIabdSummary = NpsIabdSummary(
    amount = Some(900),
    `type` = Some(IabdType.PersonalPensionPayments.code),
    npsDescription = Some("personal pension payments npsIabdSummary description"),
    employmentId = Some(123),
    estimatedPaySource = Some(2)
  )

  val genericNpsComponent = NpsComponent(
    amount = Some(1000),
    `type` = Some(AllowanceType.PersonalSavingsAllowance.id),
    iabdSummaries = Some(List(blindPersonNpsIabdSummary, flatRateExpensesNpsIabdSummary, giftAidNpsIabdSummary)),
    npsDescription = Some("nps component description"),
    sourceAmount = Some(200)
  )

  val genericNpsComponent2 = NpsComponent(
    amount = Some(1000),
    `type` = Some(AllowanceType.PersonalSavingsAllowance.id),
    iabdSummaries = Some(List(jobExpencesNpsIabdSummary, miscNpsIabdSummary, personalPensionNpsIabdSummary)),
    npsDescription = Some("nps component description"),
    sourceAmount = Some(200)
  )

  val taxBand = TaxBand(
    income = Some(10000),
    tax = Some(200),
    lowerBand = Some(5000),
    upperBand = Some(800),
    rate = Some(20)
  )

  val genericNpsTax = NpsTax(
    totalIncome = Some(genericNpsComponent),
    allowReliefDeducts = None,
    totalTaxableIncome = Some(100),
    totalTax = Some(300),
    taxBands = Some(List(taxBand))
  )

  val genericNpsTax2 = NpsTax(
    totalIncome = Some(genericNpsComponent2),
    allowReliefDeducts = None,
    totalTaxableIncome = Some(100),
    totalTax = Some(300),
    taxBands = Some(List(taxBand))
  )

  val blindPersonIabdSummary =
    IabdSummary(IabdType.BlindPersonsAllowance.code, "description", 400, Some(123), Some(1), Some("employee name"))
  val flatRateExpensesIabdSummary =
    IabdSummary(IabdType.FlatRateJobExpenses.code, "description", 200, Some(123), Some(1), Some("employee name"))

  val employment = Employments(
    id = Some(123),
    name = Some("employment name"),
    taxCode = Some("K950BR"),
    basisOperation = Some(BasisOperation.Cumulative)
  )

  val taxCode = TaxCode(
    taxCode = Some("K950BR"),
    rate = Some(20)
  )

  val taxComponent = TaxComponent(700, 1, "description", List(blindPersonIabdSummary))

  val taxCodeDescription = TaxCodeDescription("K950BR", "tax band 1", List(taxCode))

  val noTypeTaxCodeComponent = TaxCodeComponent(Some("no type tax code component description"), Some(200), None)

  val personalAllowanceRecievedTaxCodeComponent = TaxCodeComponent(
    Some("PersonalAllowanceReceived tax code component description"),
    Some(200),
    Some(AllowanceType.PersonalAllowanceReceived.id))

  val personalAllowanceTransferredTaxCodeComponent = TaxCodeComponent(
    Some("PersonalAllowanceTransferred tax code component description"),
    Some(50),
    Some(DeductionType.PersonalAllowanceTransferred.id))

  val taxCodeDetails = TaxCodeDetails(
    employment = Some(List(employment)),
    taxCode = Some(List(taxCode)),
    taxCodeDescriptions = Some(List(taxCodeDescription)),
    deductions = Some(List(personalAllowanceTransferredTaxCodeComponent)),
    allowances = Some(List(personalAllowanceRecievedTaxCodeComponent)),
    splitAllowances = Some(false),
    total = 1200
  )

  val npsBasicRateExtensions = NpsBasicRateExtensions(
    amount = Some(600),
    `type` = Some(1),
    iabdSummaries = Some(List(blindPersonNpsIabdSummary)),
    npsDescription = Some("npsBasicRateExtensions description"),
    sourceAmount = Some(50),
    personalPensionPayment = Some(400),
    giftAidPayments = Some(300),
    personalPensionPaymentRelief = Some(200),
    giftAidPaymentsRelief = Some(100)
  )

  val npsReliefsGivingBackTax = NpsReliefsGivingBackTax(
    amount = Some(100),
    `type` = Some(IabdType.BlindPersonsAllowance.code),
    iabdSummaries = Some(List(blindPersonNpsIabdSummary)),
    npsDescription = Some("npsReliefsGivingBackTax description"),
    sourceAmount = Some(20),
    marriedCouplesAllowance = Some(1000),
    enterpriseInvestmentSchemeRelief = Some(600),
    concessionalRelief = Some(40),
    maintenancePayments = Some(50),
    doubleTaxationRelief = Some(900)
  )

  val npsOtherTaxDue = NpsOtherTaxDue(
    amount = Some(1000),
    `type` = Some(1),
    iabdSummaries = Some(List(blindPersonNpsIabdSummary)),
    npsDescription = Some("npsOtherTaxDue description"),
    sourceAmount = Some(200),
    excessGiftAidTax = Some(900),
    excessWidowsAndOrphans = Some(100),
    pensionPaymentsAdjustment = Some(500),
    childBenefit = Some(700)
  )

  val npsAlreadyTaxedAtSource = NpsAlreadyTaxedAtSource(
    amount = Some(1900),
    `type` = Some(1),
    iabdSummaries = Some(List(blindPersonNpsIabdSummary)),
    npsDescription = Some("npsAlreadyTaxedAtSource description"),
    sourceAmount = Some(200),
    taxOnBankBSInterest = Some(400),
    taxCreditOnUKDividends = Some(100),
    taxCreditOnForeignInterest = Some(800),
    taxCreditOnForeignIncomeDividends = Some(50)
  )

  val totalLiab = NpsTotalLiability(
    nonSavings = Some(genericNpsTax),
    untaxedInterest = Some(genericNpsTax2),
    bankInterest = None,
    ukDividends = None,
    foreignInterest = None,
    foreignDividends = None,
    basicRateExtensions = Some(npsBasicRateExtensions),
    reliefsGivingBackTax = Some(npsReliefsGivingBackTax),
    otherTaxDue = Some(npsOtherTaxDue),
    alreadyTaxedAtSource = Some(npsAlreadyTaxedAtSource),
    totalLiability = Some(5000)
  )

  val incomeSource = NpsIncomeSource(
    name = Some("income source name"),
    taxCode = Some("K950BR"),
    employmentType = Some(TaiConstants.PrimaryEmployment),
    allowances = Some(List(genericNpsComponent)),
    deductions = None,
    payAndTax = Some(genericNpsTax),
    employmentId = Some(123),
    employmentStatus = Some(1),
    employmentTaxDistrictNumber = Some(321),
    employmentPayeRef = Some("321/0000"),
    pensionIndicator = Some(false),
    otherIncomeSourceIndicator = Some(false),
    jsaIndicator = Some(false),
    basisOperation = Some(BasisOperation.Cumulative)
  )

  private val currentYear: Int = 2017

  val npsEmployment = NpsEmployment(
    1,
    NpsDate(LocalDate.of(currentYear, 4, 23)),
    Some(NpsDate(LocalDate.of(currentYear, 3, 30))),
    "23",
    "123",
    None,
    1,
    Some(Ceased.code),
    Some("1002"),
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some(1000)
  )
}
