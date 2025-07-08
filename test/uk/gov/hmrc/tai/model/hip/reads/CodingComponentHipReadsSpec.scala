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

package uk.gov.hmrc.tai.model.hip.reads

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent.codingComponentWrites

import scala.io.Source
import scala.util.Random

class CodingComponentHipReadsSpec extends PlaySpec {
  import CodingComponentHipReadsSpec.*

  private val basePath = "test/resources/data/TaxAccount/CodingComponent/hip/"
  private def readFile(fileName: String): JsValue = {
    val jsonFilePath = basePath + fileName
    val bufferedSource = Source.fromFile(jsonFilePath)
    val source = bufferedSource.mkString("")
    bufferedSource.close()
    Json.parse(source)
  }

  "codingComponentReads" must {
    "return empty list" when {
      "no NpsComponents of interest are present in the list of income deductions, within the supplied nps tax account json" in {
        val payload = readFile("tc01.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) mustBe empty
      }
    }

    "return a single allowance coding component" when {
      "single type 11 NpsComponent (PersonalAllowanceStandard) is present, within income allowances" in {

        val payload = readFile("tc02.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) mustBe
          Seq(CodingComponent(PersonalAllowancePA, None, 10, "Loan Interest Amount", Some(100)))
      }
    }

    "return a single deduction coding component" when {
      "single type 35 NpsComponent is present (underpayment from previous year), within income deductions" in {
        val payload = readFile("tc03.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) mustBe
          Seq(CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Van Benefit"))
      }
    }

    "return multiple deduction coding components" when {
      "multiple NpsComponents of interest are present within the income deductions" in {
        val payload = readFile("tc04.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) mustBe
          Seq(
            CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Non-qualifying Relocation Expenses"),
            CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Van Benefit"),
            CodingComponent(OutstandingDebt, None, 10, "Educational Services")
          )
      }
    }

    "return multiple allowance coding components" when {
      "multiple NpsComponents of interest are present within income deductions" in {

        val payload = readFile("tc05.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) mustBe
          Seq(
            CodingComponent(EstimatedTaxYouOweThisYear, None, 10, "Non-qualifying Relocation Expenses", None),
            CodingComponent(UnderPaymentFromPreviousYear, None, 10, "Van Benefit", None),
            CodingComponent(OutstandingDebt, None, 10, "Educational Services", None),
            CodingComponent(PersonalAllowancePA, None, 10, "Loan Interest Amount", None),
            CodingComponent(PersonalAllowanceAgedPAA, None, 10, "Death, Sickness or Funeral Benefits", None),
            CodingComponent(PersonalAllowanceElderlyPAE, None, 10, "Married Couples Allowance (MAA)", None)
          )

      }
    }
//
    "return all allowances received" when {
      "multiple allowances are present" in {
        val payload = readFile("tc06.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe Seq(
          CodingComponent(PersonalPensionPayments, None, 100, "Personal Pension Payments", None),
          CodingComponent(GiftAidPayments, None, 100, "Maintenance Payments", None),
          CodingComponent(EnterpriseInvestmentScheme, None, 100, "Total gift aid Payments", None),
          CodingComponent(LoanInterestAmount, None, 100, "Employer Provided Services", None),
          CodingComponent(MaintenancePayments, None, 100, "Balancing Charge", None),
          CodingComponent(PersonalAllowancePA, None, 100, "Loan Interest Amount", None),
          CodingComponent(PersonalAllowanceAgedPAA, None, 100, "Death, Sickness or Funeral Benefits", None),
          CodingComponent(PersonalAllowanceElderlyPAE, None, 100, "Married Couples Allowance (MAA)", None),
          CodingComponent(MarriedCouplesAllowanceMAE, None, 100, "Gifts of Shares to Charity", None),
          CodingComponent(MarriedCouplesAllowanceMCCP, None, 100, "Retirement Annuity Payments", None),
          CodingComponent(SurplusMarriedCouplesAllowanceToWifeWAE, None, 100, "Commission", None),
          CodingComponent(MarriedCouplesAllowanceToWifeWMA, None, 100, "Other Income (Earned)", None),
          CodingComponent(ConcessionRelief, None, 100, "Benefit in Kind", None),
          CodingComponent(DoubleTaxationRelief, None, 100, "Car Fuel Benefit", None),
          CodingComponent(ForeignPensionAllowance, None, 100, "Medical Insurance", None),
          CodingComponent(EarlyYearsAdjustment, None, 100, "Car Benefit", None),
          CodingComponent(MarriageAllowanceReceived, None, 100, "Telephone", None)
        )
      }

    }
//
    "return all deductions" when {
      "multiple deductions are present" in {
        val payload = readFile("tc07.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe Seq(
          CodingComponent(MarriedCouplesAllowanceToWifeMAW, None, 100, "Maintenance Payments", None),
          CodingComponent(BalancingCharge, None, 100, "BPA Received from Spouse/Civil Partner", None),
          CodingComponent(UnderpaymentRestriction, None, 100, "Benefit in Kind", None),
          CodingComponent(GiftAidAdjustment, None, 100, "Medical Insurance", None),
          CodingComponent(UnderPaymentFromPreviousYear, None, 100, "Van Benefit", None),
          CodingComponent(HigherPersonalAllowanceRestriction, None, 100, "Beneficial Loan", None),
          CodingComponent(AdjustmentToRateBand, None, 100, "Asset Transfer", None),
          CodingComponent(OutstandingDebt, None, 100, "Educational Services", None),
          CodingComponent(ChildBenefit, None, 100, "Entertaining", None),
          CodingComponent(MarriageAllowanceTransferred, None, 100, "Expenses", None),
          CodingComponent(DividendTax, None, 100, "Mileage", None),
          CodingComponent(EstimatedTaxYouOweThisYear, None, 100, "Non-qualifying Relocation Expenses", None)
        )
      }
    }

    "return non-tax-code incomes from income sources" when {
      "there are non-tax-code incomes present" in {
        val payload = readFile("tc08.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe Seq(
          CodingComponent(StatePension, None, 100, "Gift Aid Payments", None),
          CodingComponent(PublicServicesPension, None, 100, "Gift Aid treated as paid in previous tax year", None),
          CodingComponent(ForcesPension, None, 100, "One off Gift Aid Payments", None),
          CodingComponent(OccupationalPension, None, 100, "Gift Aid after end of tax year", None),
          CodingComponent(IncapacityBenefit, None, 100, "Personal Pension Payments", None),
          CodingComponent(OtherEarnings, None, 100, "Gifts of Shares to Charity", None),
          CodingComponent(JobSeekersAllowance, None, 100, "Retirement Annuity Payments", None),
          CodingComponent(PartTimeEarnings, None, 100, "Non-Coded Income", None),
          CodingComponent(Tips, None, 100, "Commission", None),
          CodingComponent(Commission, None, 100, "Other Income (Earned)", None),
          CodingComponent(OtherIncomeEarned, None, 100, "Other Income (Not Earned)", None),
          CodingComponent(UntaxedInterestIncome, None, 100, "Part Time Earnings", None),
          CodingComponent(OtherIncomeNotEarned, None, 100, "Tips", None),
          CodingComponent(Profit, None, 100, "Other Earnings", None),
          CodingComponent(PersonalPensionAnnuity, None, 100, "Casual Earnings", None),
          CodingComponent(Profit, None, 100, "New Estimated Pay", None),
          CodingComponent(BankOrBuildingSocietyInterest, None, 100, "Telephone", None),
          CodingComponent(EmploymentAndSupportAllowance, None, 100, "Accommodation", None)
        )
      }
    }

  }

  "totalLiabilityReads" must {
    "return empty list" when {
      "there is no total liability present in tax account" in {
        val payload = readFile("tc09.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe empty
      }
      "total liability is null in tax account" in {
        val payload = readFile("tc10.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe empty
      }
      "No Benefits or Allowances were found within the provided iabd summaries" in {
        val payload = readFile("tc11.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)
        result mustBe Seq.empty[CodingComponent]
      }
      "an empty iabd summary array is present within the tax account json" in {
        val payload = readFile("tc12.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)
        result mustBe Seq.empty[CodingComponent]
      }
      "processing a solitary nps iabd json fragment that does not declare a type" in {
        val payload = readFile("tc13.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe Seq
          .empty[CodingComponent]
      }

    }

    "return correct response" when {
      "processing test scenario 25 payload from HIP E2E testing, incl flat rate expenses and med insurance" in {
        val payload = readFile("tc34.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe Seq(
          CodingComponent(PersonalAllowancePA, None, 12570, "Loan Interest Amount", Some(12570)),
          CodingComponent(EarlyYearsAdjustment, None, 250, "Car Benefit", Some(250)),
          CodingComponent(StatePension, None, 10700, "Gift Aid Payments", Some(10700)),
          CodingComponent(FlatRateJobExpenses, None, 1100, "Flat Rate Job Expenses", None),
          CodingComponent(MedicalInsurance, Some(1), 500, "Medical Insurance", None)
        )
      }
    }

    "generate Benefit instances of the appropriate TaxComponentType" when {
      "processing nps iabd summaries that correspond to known benefit types" in {
        val payload = readFile("tc14.json")
        val benefitComponents =
          payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)
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
        val benInKindComponentSeq =
          benInKindJson.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)
        benInKindComponentSeq.size mustBe 1
        benInKindComponentSeq.head.componentType mustBe BenefitInKind
      }
    }

    "generate Benefit instances with correctly populated content" in {
      val payload1 = readFile("tc15.json")
      payload1.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe
        Seq(CodingComponent(CarBenefit, Some(13), 120.22, "Car Benefit"))

      val payload2 = readFile("tc16.json")

      payload2.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe
        Seq(CodingComponent(MedicalInsurance, None, 0, "Medical Insurance"))
    }

    "extract individual benefits in kind for a single employment" when {

      "benefits in kind total amount matches the sum of individual benefit in kind amounts" in {
        val payload = readFile("tc17.json")

        val result = payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        )

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation"),
          CodingComponent(Assets, Some(1), 1, "Assets"),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer")
        )
      }
      "a benefits in kind total amount is not present within iabd summaries" in {
        val payload = readFile("tc18.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
          CodingComponent(Assets, Some(1), 1, "Assets", None),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None)
        )

      }
      "a benefits in kind total amount is present, but it has an amount value of zero" in {
        val payload = readFile("tc19.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
          CodingComponent(Assets, Some(1), 1, "Assets", None),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None)
        )
      }
    }

    "extract only the benefit in kind total for a single employment" when {

      "benefits in kind total amount does not match the sum of individual benefit in kind amounts" in {
        val payload = readFile("tc20.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(CodingComponent(BenefitInKind, Some(1), 4, "Benefit in Kind", None))
      }
    }

    "exclude any 'benefits from employment' from the amount reconciliation process" in {
      val payload = readFile("tc21.json")

      val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

      result mustBe Seq(
        CodingComponent(CarFuelBenefit, Some(1), 1, "Car Fuel Benefit", None),
        CodingComponent(MedicalInsurance, Some(1), 1, "Medical Insurance", None),
        CodingComponent(BenefitInKind, Some(1), 5, "Benefit in Kind", None)
      )

    }

    "extract the correct benefits in kind across multiple employments" when {

      "total and individual amounts match for all employments" in {
        val payload = readFile("tc22.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
          CodingComponent(Assets, Some(1), 1, "Assets", None),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None),
          CodingComponent(
            EmployerProvidedProfessionalSubscription,
            Some(2),
            2,
            "Employer Provided Professional Subscription",
            None
          ),
          CodingComponent(
            IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
            Some(2),
            2,
            "Income Tax Paid but not deducted from Director's Remuneration",
            None
          ),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "Travel and Subsistence", None)
        )

      }
      "total and individual amounts match for only one employment" in {
        val payload = readFile("tc23.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
          CodingComponent(Assets, Some(1), 1, "Assets", None),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None),
          CodingComponent(BenefitInKind, Some(2), 25, "Benefit in Kind", None)
        )

      }
      "total and individual amounts match for one employment, and total is not present for the other" in {
        val payload = readFile("tc24.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
          CodingComponent(Assets, Some(1), 1, "Assets", None),
          CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None),
          CodingComponent(
            EmployerProvidedProfessionalSubscription,
            Some(2),
            2,
            "Employer Provided Professional Subscription",
            None
          ),
          CodingComponent(
            IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
            Some(2),
            2,
            "Income Tax Paid but not deducted from Director's Remuneration",
            None
          ),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "Travel and Subsistence", None)
        )

      }
      "total and individual amounts do not match for one employment, and total is not present for the other" in {
        val payload = readFile("tc25.json")
        val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

        result mustBe Seq(
          CodingComponent(BenefitInKind, Some(1), 30, "Benefit in Kind", None),
          CodingComponent(
            EmployerProvidedProfessionalSubscription,
            Some(2),
            2,
            "Employer Provided Professional Subscription",
            None
          ),
          CodingComponent(
            IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
            Some(2),
            2,
            "Income Tax Paid but not deducted from Director's Remuneration",
            None
          ),
          CodingComponent(TravelAndSubsistence, Some(2), 2, "Travel and Subsistence", None)
        )

      }
    }

    "Ignore a benefit in kind that does not have an employmentId" in {
      val payload = readFile("tc26.json")
      val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

      result mustBe Seq(
        CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
        CodingComponent(Assets, Some(1), 1, "Assets", None),
        CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None)
      )

    }

    "return all benefits from employment, regardless of presence of an employmentId" in {
      val payload = readFile("tc27.json")
      val result = payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

      result mustBe Seq(
        CodingComponent(ServiceBenefit, None, 1, "Service Benefit", None),
        CodingComponent(TaxableExpensesBenefit, None, 1, "Taxable Expenses Benefit", None),
        CodingComponent(Accommodation, Some(1), 1, "Accommodation", None),
        CodingComponent(Assets, Some(1), 1, "Assets", None),
        CodingComponent(AssetTransfer, Some(1), 1, "Asset Transfer", None)
      )

    }

    "include all benefit components detailed within total liability iabd summaries across multiple locations" in {
      val expectedCodingComponents = Seq(
        CodingComponent(CarFuelBenefit, Some(1), 1, "Car Fuel Benefit", None),
        CodingComponent(MedicalInsurance, Some(1), 1, "Medical Insurance", None),
        CodingComponent(Mileage, Some(1), 1, "Mileage", None),
        CodingComponent(
          EmployerProvidedProfessionalSubscription,
          Some(1),
          1,
          "Employer Provided Professional Subscription",
          None
        ),
        CodingComponent(
          IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
          Some(1),
          1,
          "Income Tax Paid but not deducted from Director's Remuneration",
          None
        ),
        CodingComponent(NonQualifyingRelocationExpenses, Some(2), 2, "Non-qualifying Relocation Expenses", None),
        CodingComponent(PersonalIncidentalExpenses, Some(2), 2, "Personal Incidental Expenses", None),
        CodingComponent(NurseryPlaces, Some(2), 2, "Nursery Places", None),
        CodingComponent(QualifyingRelocationExpenses, Some(2), 2, "Qualifying Relocation Expenses", None)
      )

      val payload = readFile("tc28.json")
      payload.as[Seq[CodingComponent]](
        CodingComponentHipReads.codingComponentReads
      ) must contain allElementsOf expectedCodingComponents
    }

    "return Allowance instances of the appropriate TaxComponentType" when {

      "processing nps iabd summaries that correspond to known allowance types" in {
        val payload = readFile("tc29.json")
        val allowanceComponents =
          payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads)

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
      val payload = readFile("tc30.json")
      payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe
        Seq(CodingComponent(JobExpenses, Some(13), 120.22, "Job Expenses", None))

      taxAccountJsonWithIabds(
        Seq(
          Json.obj(
            "type"               -> "test (058)",
            "estimatesPaySource" -> 1
          )
        )
      ).as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe
        Seq(CodingComponent(HotelAndMealExpenses, None, 0, "test", None))

    }

    "include all allowance components detailed within total liability iabd summaries across multiple locations" in {
      val expectedCodingComponents =
        Seq(
          CodingComponent(CommunityInvestmentTaxCredit, Some(1), 1, "Community Investment Tax Credit", None),
          CodingComponent(GiftsSharesCharity, Some(1), 1, "Gifts of Shares to Charity", None),
          CodingComponent(RetirementAnnuityPayments, Some(1), 1, "Retirement Annuity Payments", None)
        )

      val payload = readFile("tc31.json")
      payload.as[Seq[CodingComponent]](
        CodingComponentHipReads.codingComponentReads
      ) must contain allElementsOf expectedCodingComponents
    }

    "codingComponentReads" must {
      "merge the coding components coming from incomeSourceReads and totalLiabilityReads into a new list" in {
        val payload = readFile("tc32.json")
        payload.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads) mustBe
          Seq(
            CodingComponent(PersonalAllowancePA, None, 10, "Loan Interest Amount", None),
            CodingComponent(CommunityInvestmentTaxCredit, Some(1), 1, "Community Investment Tax Credit", None),
            CodingComponent(GiftsSharesCharity, Some(1), 1, "Gifts of Shares to Charity", None),
            CodingComponent(RetirementAnnuityPayments, Some(1), 1, "Retirement Annuity Payments", None)
          )

      }

      "include all benefit components detailed within total liability iabd summaries across multiple locations & both iabd summary lists" in {
        val expectedCodingComponents = Seq(
          CodingComponent(CarFuelBenefit, Some(1), 1, "Car Fuel Benefit", None),
          CodingComponent(MedicalInsurance, Some(1), 1, "Medical Insurance", None),
          CodingComponent(Mileage, Some(1), 1, "Mileage", None),
          CodingComponent(
            EmployerProvidedProfessionalSubscription,
            Some(1),
            1,
            "Employer Provided Professional Subscription",
            None
          ),
          CodingComponent(
            IncomeTaxPaidButNotDeductedFromDirectorsRemuneration,
            Some(1),
            1,
            "Income Tax Paid but not deducted from Director's Remuneration",
            None
          ),
          CodingComponent(NonQualifyingRelocationExpenses, Some(2), 2, "Non-qualifying Relocation Expenses", None),
          CodingComponent(PersonalIncidentalExpenses, Some(2), 2, "Personal Incidental Expenses", None),
          CodingComponent(NurseryPlaces, Some(2), 2, "Nursery Places", None),
          CodingComponent(QualifyingRelocationExpenses, Some(2), 2, "Qualifying Relocation Expenses", None)
        )

        val payload = readFile("tc33.json")
        payload.as[Seq[CodingComponent]](
          CodingComponentHipReads.codingComponentReads
        ) must contain allElementsOf expectedCodingComponents
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
}

object CodingComponentHipReadsSpec {
  private val nino: Nino = new Generator(new Random).nextNino

  private def npsIabdSummary(iadbType: Int): JsObject =
    npsIabdSummaries(1, iadbType).head

  private def npsIabdSummaries(noOfIabds: Int, iabdType: Int): Seq[JsObject] =
    for {
      _ <- 1 to noOfIabds
    } yield Json.obj(
      "amount"                   -> 1,
      "type"                     -> s"desc ($iabdType)",
      "employmentSequenceNumber" -> 1,
      "estimatesPaySource"       -> 1
    )

  private def taxAccountJsonWithIabds(
    incomeIabdSummaries: Seq[JsObject],
    allowReliefIabdSummaries: Seq[JsObject] = Seq.empty[JsObject]
  ): JsObject =
    Json.obj(
      "taxAccountId" -> "id",
      "nino"         -> nino.nino,
      "totalLiabilityDetails" -> Json.obj(
        "nonSavings" -> Json.obj(
          "totalIncomeDetails" -> Json.obj(
            "summaryIABDEstimatedPayDetailsList" -> JsArray(incomeIabdSummaries)
          ),
          "allowReliefDeducts" -> Json.obj(
            "summaryIABDEstimatedPayDetailsList" -> JsArray(allowReliefIabdSummaries)
          )
        )
      )
    )
}
