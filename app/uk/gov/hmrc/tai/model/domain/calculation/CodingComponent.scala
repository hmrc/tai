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

package uk.gov.hmrc.tai.model.domain.calculation

import play.api.libs.json.{JsArray, JsPath, JsResult, JsSuccess, JsValue, Reads, Writes}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.tai.model.domain.NpsIabdSummary.iabdsFromTotalLiabilityReads
import uk.gov.hmrc.tai.model.domain.TaxComponentType.taxComponentTypeWrites
import uk.gov.hmrc.tai.model.domain._

case class CodingComponent(
  componentType: TaxComponentType,
  employmentId: Option[Int],
  amount: BigDecimal,
  description: String,
  inputAmount: Option[BigDecimal] = None
)

object CodingComponent {
  val codingComponentWrites: Writes[CodingComponent] = (
    (JsPath \ "componentType").write[TaxComponentType](taxComponentTypeWrites) and
      (JsPath \ "employmentId").writeNullable[Int] and
      (JsPath \ "amount").write[BigDecimal] and
      (JsPath \ "description").write[String] and
      (JsPath \ "iabdCategory").write[String] and
      (JsPath \ "inputAmount").writeNullable[BigDecimal]
  )(unapplyCodingComponentForApiJson _)

  val codingComponentReads: Reads[Seq[CodingComponent]] = (json: JsValue) => {

    val taxComponentsFromIncomeSources = json.as[Seq[CodingComponent]](incomeSourceReads)
    val taxComponentsFromLiabilities = json.as[Seq[CodingComponent]](totalLiabilityReads)
    val taxComponents = taxComponentsFromIncomeSources ++ taxComponentsFromLiabilities

    JsSuccess(taxComponents)
  }

  private type CodingComponentFactory = (Int, BigDecimal, String, Option[BigDecimal]) => Option[CodingComponent]

  val incomeSourceReads: Reads[Seq[CodingComponent]] = (json: JsValue) => {
    val codingComponents: Seq[CodingComponent] = (json \ "incomeSources").validate[JsArray] match {
      case JsSuccess(incomesJsArray, _) => incomesJsArray.value.flatMap(codingComponentsFromJson).toSeq
      case _                            => Seq.empty[CodingComponent]
    }
    JsSuccess(codingComponents)
  }

  private def codingComponentsFromJson(incomeJsVal: JsValue): Seq[CodingComponent] = {

    def allowanceFactory: (Int, BigDecimal, String, Option[BigDecimal]) => Option[CodingComponent] =
      (typeKey: Int, amount: BigDecimal, description: String, inputAmount: Option[BigDecimal]) =>
        npsComponentAllowanceMap.get(typeKey).map(CodingComponent(_, None, amount, description, inputAmount))

    def deductionFactory: (Int, BigDecimal, String, Option[BigDecimal]) => Option[CodingComponent] =
      (typeKey: Int, amount: BigDecimal, description: String, inputAmount: Option[BigDecimal]) =>
        npsComponentDeductionMap.get(typeKey).map(CodingComponent(_, None, amount, description, inputAmount))

    def nonTaxCodeIncomeFactory: (Int, BigDecimal, String, Option[BigDecimal]) => Option[CodingComponent] =
      (typeKey: Int, amount: BigDecimal, description: String, inputAmount: Option[BigDecimal]) =>
        npsComponentNonTaxCodeIncomeMap.get(typeKey).map(CodingComponent(_, None, amount, description, inputAmount))

    codingComponentsFromIncomeSources(incomeJsVal, "deductions", deductionFactory) ++
      codingComponentsFromIncomeSources(incomeJsVal, "allowances", allowanceFactory) ++
      codingComponentsFromIncomeSources(incomeJsVal, "deductions", nonTaxCodeIncomeFactory)
  }

  private def codingComponentsFromIncomeSources(
    incomeSourceJson: JsValue,
    incomeSourceJsonElement: String,
    codingComponentFactory: CodingComponentFactory
  ): Seq[CodingComponent] =
    (incomeSourceJson \ incomeSourceJsonElement).validate[JsArray] match {
      case JsSuccess(componentJsArray, _) =>
        componentJsArray.value.toSeq.flatMap { componentJsVal =>
          taxComponentFromNpsComponent(componentJsVal, codingComponentFactory)
        }
      case _ => Seq.empty[CodingComponent]
    }

  private def taxComponentFromNpsComponent(
    npsComponentJson: JsValue,
    codingComponentFactory: CodingComponentFactory
  ): Option[CodingComponent] = {
    val description = (npsComponentJson \ "npsDescription").as[String]
    val amount = (npsComponentJson \ "amount").as[BigDecimal]
    val typeKey = (npsComponentJson \ "type").as[Int]
    val sourceAmount = (npsComponentJson \ "sourceAmount").asOpt[BigDecimal]
    codingComponentFactory(typeKey, amount, description, sourceAmount)
  }

  private val npsComponentDeductionMap: Map[Int, DeductionComponentType] = Map(
    6  -> MarriedCouplesAllowanceToWifeMAW,
    15 -> BalancingCharge,
    28 -> UnderpaymentRestriction,
    30 -> GiftAidAdjustment,
    35 -> UnderPaymentFromPreviousYear,
    37 -> HigherPersonalAllowanceRestriction,
    40 -> AdjustmentToRateBand,
    41 -> OutstandingDebt,
    42 -> ChildBenefit,
    43 -> MarriageAllowanceTransferred,
    44 -> DividendTax,
    45 -> EstimatedTaxYouOweThisYear,
    50 -> BRDifferenceTaxCharge
  )

  private val npsComponentNonTaxCodeIncomeMap: Map[Int, NonTaxCodeIncomeComponentType] = Map(
    1  -> StatePension,
    2  -> PublicServicesPension,
    3  -> ForcesPension,
    4  -> OccupationalPension,
    5  -> IncapacityBenefit,
    17 -> OtherEarnings,
    18 -> JobSeekersAllowance,
    19 -> PartTimeEarnings,
    20 -> Tips,
    21 -> Commission,
    22 -> OtherIncomeEarned,
    23 -> UntaxedInterestIncome,
    24 -> OtherIncomeNotEarned,
    25 -> Profit,
    26 -> PersonalPensionAnnuity,
    27 -> Profit,
    32 -> BankOrBuildingSocietyInterest,
    38 -> EmploymentAndSupportAllowance
  )

  private val npsComponentAllowanceMap: Map[Int, AllowanceComponentType] = Map(
    5  -> PersonalPensionPayments,
    6  -> GiftAidPayments,
    7  -> EnterpriseInvestmentScheme,
    8  -> LoanInterestAmount,
    10 -> MaintenancePayments,
    11 -> PersonalAllowancePA,
    12 -> PersonalAllowanceAgedPAA,
    13 -> PersonalAllowanceElderlyPAE,
    17 -> MarriedCouplesAllowanceMAE,
    18 -> MarriedCouplesAllowanceMCCP,
    20 -> SurplusMarriedCouplesAllowanceToWifeWAE,
    21 -> MarriedCouplesAllowanceToWifeWMA,
    28 -> ConcessionRelief,
    29 -> DoubleTaxationRelief,
    30 -> ForeignPensionAllowance,
    31 -> EarlyYearsAdjustment,
    32 -> MarriageAllowanceReceived,
    34 -> BRDifferenceTaxReduction
  )

  val totalLiabilityReads: Reads[Seq[CodingComponent]] = new Reads[Seq[CodingComponent]] {
    override def reads(json: JsValue): JsResult[Seq[CodingComponent]] = {
      val extractedIabds: Seq[NpsIabdSummary] = json.as[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads)
      val codingComponents = codingComponentsFromIabdSummaries(extractedIabds)
      val (benefits, otherCodingComponents) = codingComponents.partition {
        _.componentType match {
          case _: BenefitComponentType => true
          case _                       => false
        }
      }
      val reconciledBenefits = reconcileBenefits(benefits)
      JsSuccess(otherCodingComponents ++ reconciledBenefits)
    }
  }

  private def codingComponentsFromIabdSummaries(iabds: Seq[NpsIabdSummary]): Seq[CodingComponent] =
    iabds collect {
      case iabd if npsIabdSummariesLookup.isDefinedAt(iabd.componentType) =>
        CodingComponent(npsIabdSummariesLookup(iabd.componentType), iabd.employmentId, iabd.amount, iabd.description)
    }

  private def reconcileBenefits(benefits: Seq[CodingComponent]): Seq[CodingComponent] = {
    val (benefitsFromEmployment, benefitsInKind) =
      benefits.partition(ben => benefitsFromEmploymentSet.contains(ben.componentType))
    benefitsFromEmployment ++ reconciledBenefitsInKind(benefitsInKind)
  }

  private def reconciledBenefitsInKind(benefitsInKind: Seq[CodingComponent]): Seq[CodingComponent] = {
    val allEmpIds = benefitsInKind.flatMap(_.employmentId).distinct
    allEmpIds.flatMap { empId =>
      val employmentBenefits = benefitsInKind.filter(_.employmentId.contains(empId))
      reconcileBenefitsForEmployment(employmentBenefits)
    }
  }

  private def reconcileBenefitsForEmployment(benefits: Seq[CodingComponent]): Seq[CodingComponent] = {
    def isBenefitInKind(tc: CodingComponent) = tc.componentType == BenefitInKind
    val individuals = benefits.filterNot(isBenefitInKind)
    benefits.find(isBenefitInKind) match {
      case Some(total) if total.amount > 0 && total.amount != individuals.map(_.amount).sum => Seq(total)
      case _                                                                                => individuals
    }
  }

  private val npsIabdBenefitTypesMap: Map[Int, BenefitComponentType] = Map(
    8   -> EmployerProvidedServices,
    28  -> BenefitInKind,
    29  -> CarFuelBenefit,
    30  -> MedicalInsurance,
    31  -> CarBenefit,
    32  -> Telephone,
    33  -> ServiceBenefit,
    34  -> TaxableExpensesBenefit,
    35  -> VanBenefit,
    36  -> VanFuelBenefit,
    37  -> BeneficialLoan,
    38  -> Accommodation,
    39  -> Assets,
    40  -> AssetTransfer,
    41  -> EducationalServices,
    42  -> Entertaining,
    43  -> Expenses,
    44  -> Mileage,
    45  -> NonQualifyingRelocationExpenses,
    46  -> NurseryPlaces,
    47  -> OtherItems,
    48  -> PaymentsOnEmployeesBehalf,
    49  -> PersonalIncidentalExpenses,
    50  -> QualifyingRelocationExpenses,
    51  -> EmployerProvidedProfessionalSubscription,
    52  -> IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
    53  -> TravelAndSubsistence,
    54  -> VouchersAndCreditCards,
    117 -> NonCashBenefit
  )

  private val npsIabdAllowanceTypesMap: Map[Int, AllowanceComponentType] = Map(
    14  -> BlindPersonsAllowance,
    15  -> BpaReceivedFromSpouseOrCivilPartner,
    16  -> CommunityInvestmentTaxCredit,
    17  -> GiftsSharesCharity,
    18  -> RetirementAnnuityPayments,
    55  -> JobExpenses,
    56  -> FlatRateJobExpenses,
    57  -> ProfessionalSubscriptions,
    58  -> HotelAndMealExpenses,
    59  -> OtherExpenses,
    60  -> VehicleExpenses,
    61  -> MileageAllowanceRelief,
    90  -> VentureCapitalTrust,
    102 -> LossRelief
  )

  private val npsIabdSummariesLookup: Map[Int, TaxComponentType] =
    npsIabdBenefitTypesMap ++ npsIabdAllowanceTypesMap

  private val benefitsFromEmploymentSet: Set[TaxComponentType] = Set(
    CarFuelBenefit,
    MedicalInsurance,
    CarBenefit,
    Telephone,
    ServiceBenefit,
    TaxableExpensesBenefit,
    VanBenefit,
    VanFuelBenefit,
    BeneficialLoan,
    NonCashBenefit
  )

  private def unapplyCodingComponentForApiJson(
    t: CodingComponent
  ): (TaxComponentType, Option[Int], BigDecimal, String, String, Option[BigDecimal]) = {
    val iabdCategory = t.componentType match {
      case _: AllowanceComponentType        => "Allowance"
      case _: BenefitComponentType          => "Benefit"
      case _: DeductionComponentType        => "Deduction"
      case _: NonTaxCodeIncomeComponentType => "NonTaxCodeIncome"
      case _                                => throw new RuntimeException("Unrecognised coding Component type")
    }
    (t.componentType, t.employmentId, t.amount, t.description, iabdCategory, t.inputAmount)
  }
}
