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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent.{codingComponentHipToggleOffReads, codingComponentWrites}

import scala.util.Random

class CodingComponentSpec extends PlaySpec {
  import CodingComponentSpec._
  "codingComponentReads" must {
    "return empty list" when {
      "no NpsComponents of interest are present in the list of income deductions, within the supplied nps tax account json" in {
        val noNpsComponentOfInterestNpsJson = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "incomeSources" -> JsArray(
            Seq(
              Json.obj(
                "employmentId"   -> 1,
                "employmentType" -> 1,
                "taxCode"        -> "1150L",
                "deductions" -> JsArray(
                  Seq(
                    Json.obj(
                      "npsDescription" -> "Something we aren't interested in",
                      "amount"         -> 10,
                      "type"           -> 888
                    )
                  )
                )
              )
            )
          )
        )

        noNpsComponentOfInterestNpsJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe empty
      }
    }

    "return a single allowance coding component" when {
      "single type 11 NpsComponent (PersonalAllowanceStandard) is present, within income allowances" in {
        val personalAllowanceStandardNpsJson = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "incomeSources" -> JsArray(
            Seq(
              Json.obj(
                "employmentId"   -> 1,
                "employmentType" -> 1,
                "taxCode"        -> "1150L",
                "allowances" -> JsArray(
                  Seq(
                    Json.obj(
                      "npsDescription" -> "personal allowance",
                      "amount"         -> 10,
                      "type"           -> 11,
                      "sourceAmount"   -> 100
                    )
                  )
                )
              )
            )
          )
        )

        personalAllowanceStandardNpsJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
          Seq(CodingComponent(PersonalAllowancePA, None, 10, "personal allowance", Some(100)))
      }
    }

    "return a single deduction coding component" when {
      "single type 35 NpsComponent is present (underpayment from previous year), within income deductions" in {

        val underPaymentFromPreviousYearNpsJson = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino,
          "incomeSources" -> JsArray(
            Seq(
              Json.obj(
                "employmentId"   -> 1,
                "employmentType" -> 1,
                "taxCode"        -> "1150L",
                "deductions" -> JsArray(
                  Seq(
                    Json.obj(
                      "npsDescription" -> "Underpayment form previous year",
                      "amount"         -> 10,
                      "type"           -> 35,
                      "sourceAmount"   -> JsNull
                    )
                  )
                )
              )
            )
          )
        )

        underPaymentFromPreviousYearNpsJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
          Seq(CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Underpayment form previous year"))
      }
    }

    "return multiple deduction coding components" when {
      "multiple NpsComponents of interest are present within the income deductions" in {
        combinedNpsDeductionJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
          Seq(
            CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Estimated Tax You Owe This Year"),
            CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Underpayment form previous year"),
            CodingComponent(OutstandingDebt, None, 10, "Outstanding Debt Restriction")
          )
      }
    }

    "return multiple allowance coding components" when {
      "multiple NpsComponents of interest are present within income deductions" in {
        combinedNpsAllowanceJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
          Seq(
            CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Estimated Tax You Owe This Year"),
            CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Underpayment form previous year"),
            CodingComponent(OutstandingDebt, None, 10, "Outstanding Debt Restriction"),
            CodingComponent(PersonalAllowancePA, None, 10, "personal allowance"),
            CodingComponent(PersonalAllowanceAgedPAA, None, 10, "personal allowance"),
            CodingComponent(PersonalAllowanceElderlyPAE, None, 10, "personal allowance")
          )
      }
    }

    "return all allowances received" when {
      "multiple allowances are present" in {
        val json = npsIncomeSourceJson(
          allowances = npsComponents(
            Seq(5, 6, 7, 8, 10, 11, 12, 13, 17, 18, 20, 21, 28, 29, 30, 31, 32),
            amount = 100
          )
        )

        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe Seq(
          CodingComponent(PersonalPensionPayments, None, 100, "nps-desc"),
          CodingComponent(GiftAidPayments, None, 100, "nps-desc"),
          CodingComponent(EnterpriseInvestmentScheme, None, 100, "nps-desc"),
          CodingComponent(LoanInterestAmount, None, 100, "nps-desc"),
          CodingComponent(MaintenancePayments, None, 100, "nps-desc"),
          CodingComponent(PersonalAllowancePA, None, 100, "nps-desc"),
          CodingComponent(PersonalAllowanceAgedPAA, None, 100, "nps-desc"),
          CodingComponent(PersonalAllowanceElderlyPAE, None, 100, "nps-desc"),
          CodingComponent(MarriedCouplesAllowanceMAE, None, 100, "nps-desc"),
          CodingComponent(MarriedCouplesAllowanceMCCP, None, 100, "nps-desc"),
          CodingComponent(SurplusMarriedCouplesAllowanceToWifeWAE, None, 100, "nps-desc"),
          CodingComponent(MarriedCouplesAllowanceToWifeWMA, None, 100, "nps-desc"),
          CodingComponent(ConcessionRelief, None, 100, "nps-desc"),
          CodingComponent(DoubleTaxationRelief, None, 100, "nps-desc"),
          CodingComponent(ForeignPensionAllowance, None, 100, "nps-desc"),
          CodingComponent(EarlyYearsAdjustment, None, 100, "nps-desc"),
          CodingComponent(MarriageAllowanceReceived, None, 100, "nps-desc")
        )
      }

    }

    "return all deductions" when {
      "multiple deductions are present" in {
        val json = npsIncomeSourceJson(
          deductions = npsComponents(Seq(6, 15, 28, 30, 35, 37, 40, 41, 42, 43, 44, 45), amount = 100)
        )

        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe Seq(
          CodingComponent(MarriedCouplesAllowanceToWifeMAW, None, 100, "nps-desc"),
          CodingComponent(BalancingCharge, None, 100, "nps-desc"),
          CodingComponent(UnderpaymentRestriction, None, 100, "nps-desc"),
          CodingComponent(GiftAidAdjustment, None, 100, "nps-desc"),
          CodingComponent(UnderPaymentFromPreviousYear, None, 100, "nps-desc"),
          CodingComponent(HigherPersonalAllowanceRestriction, None, 100, "nps-desc"),
          CodingComponent(AdjustmentToRateBand, None, 100, "nps-desc"),
          CodingComponent(OutstandingDebt, None, 100, "nps-desc"),
          CodingComponent(ChildBenefit, None, 100, "nps-desc"),
          CodingComponent(MarriageAllowanceTransferred, None, 100, "nps-desc"),
          CodingComponent(DividendTax, None, 100, "nps-desc"),
          CodingComponent(EstimatedTaxYouOweThisYear, None, 100, "nps-desc")
        )
      }
    }

    "return non-tax-code incomes from income sources" when {
      "there are non-tax-code incomes present" in {
        val json = npsIncomeSourceJson(
          deductions =
            npsComponents(Seq(1, 2, 3, 4, 5, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 32, 38), amount = 100)
        )

        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe Seq(
          CodingComponent(StatePension, None, 100, "nps-desc"),
          CodingComponent(PublicServicesPension, None, 100, "nps-desc"),
          CodingComponent(ForcesPension, None, 100, "nps-desc"),
          CodingComponent(OccupationalPension, None, 100, "nps-desc"),
          CodingComponent(IncapacityBenefit, None, 100, "nps-desc"),
          CodingComponent(OtherEarnings, None, 100, "nps-desc"),
          CodingComponent(JobSeekersAllowance, None, 100, "nps-desc"),
          CodingComponent(PartTimeEarnings, None, 100, "nps-desc"),
          CodingComponent(Tips, None, 100, "nps-desc"),
          CodingComponent(Commission, None, 100, "nps-desc"),
          CodingComponent(OtherIncomeEarned, None, 100, "nps-desc"),
          CodingComponent(UntaxedInterestIncome, None, 100, "nps-desc"),
          CodingComponent(OtherIncomeNotEarned, None, 100, "nps-desc"),
          CodingComponent(Profit, None, 100, "nps-desc"),
          CodingComponent(PersonalPensionAnnuity, None, 100, "nps-desc"),
          CodingComponent(Profit, None, 100, "nps-desc"),
          CodingComponent(BankOrBuildingSocietyInterest, None, 100, "nps-desc"),
          CodingComponent(EmploymentAndSupportAllowance, None, 100, "nps-desc")
        )
      }
    }

  }

  "totalLiabilityReads" must {
    "return empty list" when {
      "there is no total liability present in tax account" in {
        val json = Json.obj(
          "taxAccountId" -> "id",
          "nino"         -> nino.nino
        )
        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe empty
      }
      "total liability is null in tax account" in {
        val json = Json.obj(
          "taxAccountId"   -> "id",
          "nino"           -> nino.nino,
          "totalLiability" -> JsNull
        )
        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe empty
      }
      "No Benefits or Allowances were found within the provided iabd summaries" in {
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(999, 888, 777), 1))
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)
        result mustBe Seq.empty[CodingComponent]
      }
      "an empty iabd summary array is present within the tax account json" in {
        val json = taxAccountJsonWithIabds()
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)
        result mustBe Seq.empty[CodingComponent]
      }
      "processing a solitary nps iabd json fragment that does not declare a type" in {
        val json = taxAccountJsonWithIabds(
          Seq(
            Json.obj(
              "amount"             -> 120.22,
              "npsDescription"     -> "Something with no type declared",
              "employmentId"       -> 13,
              "estimatesPaySource" -> 1
            )
          )
        )
        json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe Seq.empty[CodingComponent]
      }
    }

    "generate Benefit instances of the appropriate TaxComponentType" when {

      "processing nps iabd summaries that correspond to known benefit types" in {
        val npsBenefitTypesMinusBenInKind = Seq(8, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
          46, 47, 48, 49, 50, 51, 52, 53, 54, 117)
        val jsIabdSeq = npsBenefitTypesMinusBenInKind.map(npsIabdSummary)
        val json = taxAccountJsonWithIabds(jsIabdSeq)
        val benefitComponents = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        benefitComponents.size mustBe 28
        benefitComponents.map(_.componentType) must contain allElementsOf Seq(
          EmployerProvidedServices,
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
          QualifyingRelocationExpenses,
          EmployerProvidedProfessionalSubscription,
          IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
          TravelAndSubsistence,
          VouchersAndCreditCards,
          NonCashBenefit
        )

        val jsBenInKindIabdSeq = Seq(npsIabdSummary(28))
        val benInKindJson = taxAccountJsonWithIabds(jsBenInKindIabdSeq)

        val benInKindComponentSeq = benInKindJson.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)
        benInKindComponentSeq.size mustBe 1
        benInKindComponentSeq(0).componentType mustBe BenefitInKind
      }
    }

    "generate Benefit instances with correctly populated content" in {

      taxAccountJsonWithIabds(
        Seq(
          Json.obj(
            "amount"             -> 120.22,
            "type"               -> 31,
            "npsDescription"     -> "An example car benefit",
            "employmentId"       -> 13,
            "estimatesPaySource" -> 1
          )
        )
      ).as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
        Seq(CodingComponent(CarBenefit, Some(13), 120.22, "An example car benefit"))

      taxAccountJsonWithIabds(
        Seq(
          Json.obj(
            "type"               -> 30,
            "estimatesPaySource" -> 1
          )
        )
      ).as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
        Seq(CodingComponent(MedicalInsurance, None, 0, ""))
    }

    "extract individual benefits in kind for a single employment" when {

      "benefits in kind total amount matches the sum of individual benefit in kind amounts" in {
        val benInKindIabds = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3)
        val result = taxAccountJsonWithIabds(benInKindIabds).as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc")
        )
      }
      "a benefits in kind total amount is not present within iabd summaries" in {
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(38, 39, 40), 1))
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc")
        )
      }
      "a benefits in kind total amount is present, but it has an amount value of zero" in {
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 0))
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc")
        )
      }
    }

    "extract only the benefit in kind total for a single employment" when {

      "benefits in kind total amount does not match the sum of individual benefit in kind amounts" in {
        val json = taxAccountJsonWithIabds(npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 4))
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(CodingComponent(BenefitInKind, Some(1), 4, "desc"))
      }
    }

    "exclude any 'benefits from employment' from the amount reconciliation process" in {
      val benInKindIabds = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 5)
      val benFromEmployIabds = npsIabdSummaries(1, Seq(29, 30), 1)
      val json = taxAccountJsonWithIabds(benInKindIabds ++ benFromEmployIabds)

      val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

      result mustBe Seq(
        CodingComponent(CarFuelBenefit, Some(1), 1, "desc"),
        CodingComponent(MedicalInsurance, Some(1), 1, "desc"),
        CodingComponent(BenefitInKind, Some(1), 5, "desc")
      )
    }

    "extract the correct benefits in kind across multiple employments" when {

      "total and individual amounts match for all employments" in {
        val json1 = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3)
        val json2 = npsIabdSummaries(2, Seq(51, 52, 53), 2) ++ npsIabdSummaries(2, Seq(28), 6)
        val json = taxAccountJsonWithIabds(json1 ++ json2)
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc"),
          CodingComponent(EmployerProvidedProfessionalSubscription, Some(2), 2, "desc"),
          CodingComponent(IncomeTaxPaidButNotDeductedFromDirectorsRemuneration, Some(2), 2, "desc"),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "desc")
        )
      }
      "total and individual amounts match for only one employment" in {
        val json1 = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3)
        val json2 = npsIabdSummaries(2, Seq(51, 52, 53), 2) ++ npsIabdSummaries(2, Seq(28), 25)
        val json = taxAccountJsonWithIabds(json1 ++ json2)
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc"),
          CodingComponent(BenefitInKind, Some(2), 25, "desc")
        )
      }
      "total and individual amounts match for one employment, and total is not present for the other" in {
        val json1 = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3)
        val json2 = npsIabdSummaries(2, Seq(51, 52, 53), 2)
        val json = taxAccountJsonWithIabds(json1 ++ json2)
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "desc"),
          CodingComponent(Assets, Some(1), 1, "desc"),
          CodingComponent(AssetTransfer, Some(1), 1, "desc"),
          CodingComponent(EmployerProvidedProfessionalSubscription, Some(2), 2, "desc"),
          CodingComponent(IncomeTaxPaidButNotDeductedFromDirectorsRemuneration, Some(2), 2, "desc"),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "desc")
        )
      }
      "total and individual amounts do not match for one employment, and total is not present for the other" in {
        val json1 = npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 30)
        val json2 = npsIabdSummaries(2, Seq(51, 52, 53), 2)
        val json = taxAccountJsonWithIabds(json1 ++ json2)
        val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        result mustBe Seq(
          CodingComponent(BenefitInKind, Some(1), 30, "desc"),
          CodingComponent(EmployerProvidedProfessionalSubscription, Some(2), 2, "desc"),
          CodingComponent(IncomeTaxPaidButNotDeductedFromDirectorsRemuneration, Some(2), 2, "desc"),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "desc")
        )
      }
    }

    "Ignore a benefit in kind that does not have an employmentId" in {

      val benInKindIabdWithoutEmpId = Json.obj(
        "amount"             -> 1,
        "type"               -> 38,
        "npsDescription"     -> "desc",
        "estimatesPaySource" -> 1
      )
      val json = taxAccountJsonWithIabds(
        npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3) :+ benInKindIabdWithoutEmpId
      )
      val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

      result mustBe Seq(
        CodingComponent(Accommodation, Some(1), 1, "desc"),
        CodingComponent(Assets, Some(1), 1, "desc"),
        CodingComponent(AssetTransfer, Some(1), 1, "desc")
      )
    }

    "return all benefits from employment, regardless of presence of an employmentId" in {

      val bensFromEmploymentWithoutEmpId = Seq(
        Json.obj(
          "amount"             -> 1,
          "type"               -> 33,
          "npsDescription"     -> "desc",
          "estimatesPaySource" -> 1
        ),
        Json.obj(
          "amount"             -> 1,
          "type"               -> 34,
          "npsDescription"     -> "desc",
          "estimatesPaySource" -> 1
        )
      )

      val json = taxAccountJsonWithIabds(
        bensFromEmploymentWithoutEmpId ++ npsIabdSummaries(1, Seq(38, 39, 40), 1) ++ npsIabdSummaries(1, Seq(28), 3)
      )
      val result = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

      result mustBe Seq(
        CodingComponent(ServiceBenefit, None, 1, "desc"),
        CodingComponent(TaxableExpensesBenefit, None, 1, "desc"),
        CodingComponent(Accommodation, Some(1), 1, "desc"),
        CodingComponent(Assets, Some(1), 1, "desc"),
        CodingComponent(AssetTransfer, Some(1), 1, "desc")
      )
    }

    "include all benefit components detailed within total liability iabd summaries across multiple locations" in {

      val incomeIabdSummaries = npsIabdSummaries(1, Seq(29, 30, 44), 1) ++ npsIabdSummaries(2, Seq(45, 49), 2)
      val allowReliefIabdSummaries = npsIabdSummaries(1, Seq(51, 52), 1) ++ npsIabdSummaries(2, Seq(46, 50), 2)
      val json = taxAccountJsonWithIabds(incomeIabdSummaries, allowReliefIabdSummaries)

      val expectedCodingComponents = Seq(
        CodingComponent(CarFuelBenefit, Some(1), 1, "desc"),
        CodingComponent(MedicalInsurance, Some(1), 1, "desc"),
        CodingComponent(Mileage, Some(1), 1, "desc"),
        CodingComponent(EmployerProvidedProfessionalSubscription, Some(1), 1, "desc"),
        CodingComponent(IncomeTaxPaidButNotDeductedFromDirectorsRemuneration, Some(1), 1, "desc"),
        CodingComponent(NonQualifyingRelocationExpenses, Some(2), 2, "desc"),
        CodingComponent(PersonalIncidentalExpenses, Some(2), 2, "desc"),
        CodingComponent(NurseryPlaces, Some(2), 2, "desc"),
        CodingComponent(QualifyingRelocationExpenses, Some(2), 2, "desc")
      )

      json.as[Seq[CodingComponent]](
        codingComponentHipToggleOffReads
      ) must contain allElementsOf expectedCodingComponents
    }

    "return Allowance instances of the appropriate TaxComponentType" when {

      "processing nps iabd summaries that correspond to known allowance types" in {
        val exhaustiveNpsAllowanceTypes = Seq(14, 15, 16, 17, 18, 55, 56, 57, 58, 59, 60, 61, 90, 102)
        val json = taxAccountJsonWithIabds(exhaustiveNpsAllowanceTypes.map(npsIabdSummary))
        val allowanceComponents = json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads)

        allowanceComponents.size mustBe 14
        allowanceComponents.map(_.componentType) mustBe
          Seq(
            BlindPersonsAllowance,
            BpaReceivedFromSpouseOrCivilPartner,
            CommunityInvestmentTaxCredit,
            GiftsSharesCharity,
            RetirementAnnuityPayments,
            JobExpenses,
            FlatRateJobExpenses,
            ProfessionalSubscriptions,
            HotelAndMealExpenses,
            OtherExpenses,
            VehicleExpenses,
            MileageAllowanceRelief,
            VentureCapitalTrust,
            LossRelief
          )
      }
    }

    "generate Allowance instances with correctly populated content" in {

      taxAccountJsonWithIabds(
        Seq(
          Json.obj(
            "amount"             -> 120.22,
            "type"               -> 55,
            "npsDescription"     -> "An example job expenses allowance",
            "employmentId"       -> 13,
            "estimatesPaySource" -> 1
          )
        )
      ).as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
        Seq(CodingComponent(JobExpenses, Some(13), 120.22, "An example job expenses allowance"))

      taxAccountJsonWithIabds(
        Seq(
          Json.obj(
            "type"               -> 58,
            "estimatesPaySource" -> 1
          )
        )
      ).as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
        Seq(CodingComponent(HotelAndMealExpenses, None, 0, ""))
    }

    "include all allowance components detailed within total liability iabd summaries across multiple locations" in {
      val incomeIabdSummaries = npsIabdSummaries(1, Seq(16, 17, 18), 1)
      val allowReliefIabdSummaries = npsIabdSummaries(1, Seq(98, 99), 1)
      val json = taxAccountJsonWithIabds(incomeIabdSummaries, allowReliefIabdSummaries)

      val expectedCodingComponents =
        Seq(
          CodingComponent(CommunityInvestmentTaxCredit, Some(1), 1, "desc"),
          CodingComponent(GiftsSharesCharity, Some(1), 1, "desc"),
          CodingComponent(RetirementAnnuityPayments, Some(1), 1, "desc")
        )

      json.as[Seq[CodingComponent]](
        codingComponentHipToggleOffReads
      ) must contain allElementsOf expectedCodingComponents
    }
  }

  "codingComponentReads" must {
    "merge the coding components coming from incomeSourceReads and totalLiabilityReads into a new list" in {
      val incomeIabdSummaries = npsIabdSummaries(1, Seq(16, 17, 18), 1)
      val allowReliefIabdSummaries = npsIabdSummaries(1, Seq(98, 99), 1)
      val json = Json.obj(
        "taxAccountId" -> "id",
        "nino"         -> nino.nino,
        "incomeSources" -> JsArray(
          Seq(
            Json.obj(
              "employmentId"   -> 1,
              "employmentType" -> 1,
              "taxCode"        -> "1150L",
              "allowances" -> JsArray(
                Seq(
                  Json.obj(
                    "npsDescription" -> "personal allowance",
                    "amount"         -> 10,
                    "type"           -> 11
                  )
                )
              )
            )
          )
        ),
        "totalLiability" -> Json.obj(
          "nonSavings" -> Json.obj(
            "totalIncome" -> Json.obj(
              "iabdSummaries" -> JsArray(incomeIabdSummaries)
            ),
            "allowReliefDeducts" -> Json.obj(
              "iabdSummaries" -> JsArray(allowReliefIabdSummaries)
            )
          )
        )
      )

      json.as[Seq[CodingComponent]](codingComponentHipToggleOffReads) mustBe
        Seq(
          CodingComponent(PersonalAllowancePA, None, 10, "personal allowance"),
          CodingComponent(CommunityInvestmentTaxCredit, Some(1), 1, "desc"),
          CodingComponent(GiftsSharesCharity, Some(1), 1, "desc"),
          CodingComponent(RetirementAnnuityPayments, Some(1), 1, "desc")
        )
    }
  }

  "codingComponentWrites" must {
    "write tax component correctly to json" when {
      "only mandatory fields are provided and codingComponent is Allowance" in {
        Json.toJson(CodingComponent(GiftAidPayments, None, 1232, "Some Desc"))(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "GiftAidPayments",
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Allowance"
          )
      }
      "all the fields are provided and codingComponent is Allowance" in {
        Json.toJson(
          CodingComponent(GiftAidPayments, Some(111), 1232, "Some Desc", Some(12500))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "GiftAidPayments",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Allowance",
            "inputAmount"   -> 12500
          )
      }
      "all the fields are provided and codingComponent is Benefit" in {
        Json.toJson(
          CodingComponent(AssetTransfer, Some(111), 1232, "Some Desc", Some(BigDecimal("13200.01")))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "AssetTransfer",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Benefit",
            "inputAmount"   -> BigDecimal("13200.01")
          )
      }
      "all the fields are provided and codingComponent is Deduction" in {
        Json.toJson(
          CodingComponent(BalancingCharge, Some(111), 1232, "Some Desc", Some(12500))
        )(codingComponentWrites) mustBe
          Json.obj(
            "componentType" -> "BalancingCharge",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "Deduction",
            "inputAmount"   -> 12500
          )
      }
      "all the fields are provided and codingComponent is NonTaxCodeIncomeType" in {
        Json.toJson(CodingComponent(NonCodedIncome, Some(111), 1232, "Some Desc", Some(12500)))(
          codingComponentWrites
        ) mustBe
          Json.obj(
            "componentType" -> "NonCodedIncome",
            "employmentId"  -> 111,
            "amount"        -> 1232,
            "description"   -> "Some Desc",
            "iabdCategory"  -> "NonTaxCodeIncome",
            "inputAmount"   -> 12500
          )
      }
    }
    "throw a runtime exception" when {
      "the component type is not as expected" in {
        val ex = the[RuntimeException] thrownBy
          Json.toJson(CodingComponent(EmploymentIncome, Some(111), 1232, "Some Desc"))(codingComponentWrites)

        ex.getMessage mustBe "Unrecognised coding Component type"
      }
    }
  }

}

object CodingComponentSpec {
  private val nino: Nino = new Generator(new Random).nextNino
  private val combinedNpsDeductionJson = Json.obj(
    "taxAccountId" -> "id",
    "nino"         -> nino.nino,
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"   -> 1,
          "employmentType" -> 1,
          "taxCode"        -> "1150L",
          "deductions" -> JsArray(
            Seq(
              Json.obj(
                "npsDescription" -> "Estimated Tax You Owe This Year",
                "amount"         -> 10,
                "type"           -> 45
              ),
              Json.obj(
                "npsDescription" -> "Underpayment form previous year",
                "amount"         -> 10,
                "type"           -> 35
              ),
              Json.obj(
                "npsDescription" -> "Outstanding Debt Restriction",
                "amount"         -> 10,
                "type"           -> 41
              ),
              Json.obj(
                "npsDescription" -> "Something we aren't interested in",
                "amount"         -> 10,
                "type"           -> 888
              )
            )
          )
        )
      )
    )
  )

  private val combinedNpsAllowanceJson = Json.obj(
    "taxAccountId" -> "id",
    "nino"         -> nino.nino,
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"   -> 1,
          "employmentType" -> 1,
          "taxCode"        -> "1150L",
          "deductions" -> JsArray(
            Seq(
              Json.obj(
                "npsDescription" -> "Estimated Tax You Owe This Year",
                "amount"         -> 10,
                "type"           -> 45
              ),
              Json.obj(
                "npsDescription" -> "Underpayment form previous year",
                "amount"         -> 10,
                "type"           -> 35
              ),
              Json.obj(
                "npsDescription" -> "Outstanding Debt Restriction",
                "amount"         -> 10,
                "type"           -> 41
              ),
              Json.obj(
                "npsDescription" -> "Something we aren't interested in",
                "amount"         -> 10,
                "type"           -> 888
              )
            )
          ),
          "allowances" -> JsArray(
            Seq(
              Json.obj(
                "npsDescription" -> "personal allowance",
                "amount"         -> 10,
                "type"           -> 11
              ),
              Json.obj(
                "npsDescription" -> "personal allowance",
                "amount"         -> 10,
                "type"           -> 12
              ),
              Json.obj(
                "npsDescription" -> "personal allowance",
                "amount"         -> 10,
                "type"           -> 13
              ),
              Json.obj(
                "npsDescription" -> JsString("Job expenses"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(1)
              ),
              Json.obj(
                "npsDescription" -> JsString("Something we aren't interested in"),
                "amount"         -> JsNumber(10),
                "type"           -> JsNumber(888)
              )
            )
          )
        )
      )
    )
  )

  private def npsIncomeSourceJson(
    allowances: Seq[JsObject] = Seq.empty[JsObject],
    deductions: Seq[JsObject] = Seq.empty[JsObject]
  ) = Json.obj(
    "taxAccountId" -> "id",
    "nino"         -> nino.nino,
    "incomeSources" -> JsArray(
      Seq(
        Json.obj(
          "employmentId"   -> 1,
          "employmentType" -> 1,
          "taxCode"        -> "1150L",
          "deductions"     -> JsArray(deductions),
          "allowances"     -> JsArray(allowances)
        )
      )
    )
  )

  private def npsIabdSummaries(empId: Int, types: Seq[Int], amount: Int): Seq[JsObject] =
    types.map { tp =>
      Json.obj(
        "amount"             -> amount,
        "type"               -> tp,
        "npsDescription"     -> "desc",
        "employmentId"       -> empId,
        "estimatesPaySource" -> 1
      )
    }

  private def npsComponents(types: Seq[Int], amount: Int): Seq[JsObject] =
    types.map { tp =>
      Json.obj(
        "npsDescription" -> "nps-desc",
        "amount"         -> amount,
        "type"           -> tp
      )
    }

  private def npsIabdSummary(iadbType: Int): JsObject =
    npsIabdSummaries(1, iadbType).head

  private def npsIabdSummaries(noOfIabds: Int, iabdType: Int): Seq[JsObject] =
    for {
      _ <- 1 to noOfIabds
    } yield Json.obj(
      "amount"             -> 1,
      "type"               -> iabdType,
      "npsDescription"     -> "desc",
      "employmentId"       -> 1,
      "estimatesPaySource" -> 1
    )

  private def taxAccountJsonWithIabds(
    incomeIabdSummaries: Seq[JsObject] = Seq.empty[JsObject],
    allowReliefIabdSummaries: Seq[JsObject] = Seq.empty[JsObject]
  ): JsObject =
    Json.obj(
      "taxAccountId" -> "id",
      "nino"         -> nino.nino,
      "totalLiability" -> Json.obj(
        "nonSavings" -> Json.obj(
          "totalIncome" -> Json.obj(
            "iabdSummaries" -> JsArray(incomeIabdSummaries)
          ),
          "allowReliefDeducts" -> Json.obj(
            "iabdSummaries" -> JsArray(allowReliefIabdSummaries)
          )
        )
      )
    )
}
