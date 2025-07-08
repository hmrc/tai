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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.hip.reads.NpsIabdSummaryHipReads.iabdsFromTotalLiabilityReads
import uk.gov.hmrc.tai.util.JsonHelper.parseTypeOrException

object CodingComponentHipReads {

  private type CodingComponentFactory = (Int, BigDecimal, String, Option[BigDecimal]) => Option[CodingComponent]

  private val incomeSourceReads: Reads[Seq[CodingComponent]] =
    (__ \ "employmentDetailsList").readNullable[Seq[JsValue]].map { incomeDetailsOpt =>
      incomeDetailsOpt
        .getOrElse(Seq.empty)
        .flatMap(buildCodingComponentsFromIncomeJson)
    }

  val codingComponentReads: Reads[Seq[CodingComponent]] =
    incomeSourceReads.flatMap { incomeComponents =>
      totalLiabilityReads.map { liabilityComponents =>
        incomeComponents ++ liabilityComponents
      }
    }

  private def buildCodingComponentsFromIncomeJson(incomeJson: JsValue): Seq[CodingComponent] =
    Seq(
      extractComponents(incomeJson, "deductionsDetails", deductionFactory),
      extractComponents(incomeJson, "allowancesDetails", allowanceFactory),
      extractComponents(incomeJson, "deductionsDetails", nonTaxCodeIncomeFactory)
    ).flatten

  private def extractComponents(
    json: JsValue,
    path: String,
    factory: CodingComponentFactory
  ): Seq[CodingComponent] =
    (json \ path).asOpt[Seq[JsValue]].getOrElse(Seq.empty).flatMap(extractCodingComponent(_, factory))

  private def extractCodingComponent(
    json: JsValue,
    factory: CodingComponentFactory
  ): Option[CodingComponent] = {
    val (description, typeKey) = parseTypeOrException((json \ "type").as[String])
    val amount = (json \ "adjustedAmount").as[BigDecimal]
    val sourceAmount = (json \ "sourceAmount").asOpt[BigDecimal]
    factory(typeKey, amount, description, sourceAmount)
  }

  private val deductionFactory: CodingComponentFactory =
    (typeKey, amount, desc, sourceAmt) =>
      hipComponentDeductionMap.get(typeKey).map(CodingComponent(_, None, amount, desc, sourceAmt))

  private val allowanceFactory: CodingComponentFactory =
    (typeKey, amount, desc, sourceAmt) =>
      hipComponentAllowanceMap.get(typeKey).map(CodingComponent(_, None, amount, desc, sourceAmt))

  private val nonTaxCodeIncomeFactory: CodingComponentFactory =
    (typeKey, amount, desc, sourceAmt) =>
      hipComponentNonTaxCodeIncomeMap.get(typeKey).map(CodingComponent(_, None, amount, desc, sourceAmt))

  private val totalLiabilityReads: Reads[Seq[CodingComponent]] =
    __.read[Seq[NpsIabdSummary]](iabdsFromTotalLiabilityReads).map { iabds =>
      val components = iabds.collect {
        case iabd if hipIabdSummariesLookup.contains(iabd.componentType) =>
          CodingComponent(hipIabdSummariesLookup(iabd.componentType), iabd.employmentId, iabd.amount, iabd.description)
      }
      val (benefits, others) = components.partition(_.componentType.isInstanceOf[BenefitComponentType])
      others ++ reconcileBenefits(benefits)
    }

  private def reconcileBenefits(benefits: Seq[CodingComponent]): Seq[CodingComponent] = {
    val (employmentBenefits, inKindBenefits) =
      benefits.partition(b => benefitsFromEmploymentSet.contains(b.componentType))
    employmentBenefits ++ reconcileInKind(inKindBenefits)
  }

  private def reconcileInKind(benefits: Seq[CodingComponent]): Seq[CodingComponent] =
    benefits
      .flatMap(_.employmentId)
      .distinct
      .flatMap { empId =>
        val byEmp = benefits.filter(_.employmentId.contains(empId))
        val inKind = byEmp.find(_.componentType == BenefitInKind)
        val others = byEmp.filterNot(_.componentType == BenefitInKind)
        inKind match {
          case Some(bik) if bik.amount > 0 && bik.amount != others.map(_.amount).sum => Seq(bik)
          case _                                                                     => others
        }
      }

  private val hipComponentDeductionMap: Map[Int, DeductionComponentType] = Map(
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
    50 -> BRDifferenceTaxCharge,
    51 -> HICBCPaye
  )

  private val hipComponentNonTaxCodeIncomeMap: Map[Int, NonTaxCodeIncomeComponentType] = Map(
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

  private val hipComponentAllowanceMap: Map[Int, AllowanceComponentType] = Map(
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

  private val hipIabdBenefitTypesMap: Map[Int, BenefitComponentType] = Map(
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

  private val hipIabdAllowanceTypesMap: Map[Int, AllowanceComponentType] = Map(
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

  private val hipIabdSummariesLookup: Map[Int, TaxComponentType] =
    hipIabdBenefitTypesMap ++ hipIabdAllowanceTypesMap

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
}
