/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.*
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.nps2.NpsFormatter
import uk.gov.hmrc.tai.model.rti.PayFrequency
import uk.gov.hmrc.tai.model.tai.AnnualAccount

import java.time.LocalDate

class TaxModelSpec extends PlaySpec with NpsFormatter {

  "TaxBand JSON serialization" must {
    "serialize and deserialize correctly" in {
      val taxBand = TaxBand(Some(5000), Some(1000), Some(0), Some(10000), Some(10))
      val json = Json.toJson(taxBand)

      json.as[TaxBand] mustBe taxBand
    }
  }

  "EditableDetails JSON serialization" must {
    "serialize and deserialize correctly" in {
      val details = EditableDetails(isEditable = false, payRollingBiks = true)
      val json = Json.toJson(details)

      json.as[EditableDetails] mustBe details
    }
  }

  "Employments JSON serialization" must {
    "serialize and deserialize correctly" in {
      val employment = Employments(Some(1), Some("Company A"), Some("1250L"), Some(BasisOperation.Week1Month1))
      val json = Json.toJson(employment)

      json.as[Employments] mustBe employment
    }
  }

  "IncomeExplanation JSON serialization" must {
    "serialize and deserialize correctly" in {
      val incomeExplanation = IncomeExplanation(
        employerName = "Company A",
        incomeId = 123,
        payToDate = 20000,
        calcAmount = Some(3000),
        payFrequency = Some(PayFrequency.Monthly)
      )

      val json = Json.toJson(incomeExplanation)
      json.as[IncomeExplanation] mustBe incomeExplanation
    }
  }

  "Tax JSON serialization" must {
    "serialize and deserialize correctly" in {
      val tax = Tax(
        totalIncome = Some(50000),
        totalTaxableIncome = Some(40000),
        totalTax = Some(5000),
        taxBands = Some(List(TaxBand(Some(20000), Some(2000), Some(0), Some(20000), Some(10))))
      )

      val json = Json.toJson(tax)
      json.as[Tax] mustBe tax
    }
  }

  "TaxComponent JSON serialization" must {
    "serialize and deserialize correctly" in {
      val component = TaxComponent(20, 1, "Personal Allowance", List())
      val json = Json.toJson(component)

      json.as[TaxComponent] mustBe component
    }
  }

  "TaxCode JSON serialization" must {
    "serialize and deserialize correctly" in {
      val taxCode = TaxCode(Some("1250L"), Some(20))
      val json = Json.toJson(taxCode)

      json.as[TaxCode] mustBe taxCode
    }
  }

  "TaxCodeDescription JSON serialization" must {
    "serialize and deserialize correctly" in {
      val description = TaxCodeDescription("1250L", "Standard Tax Code", List(TaxCode(Some("1250L"), Some(20))))
      val json = Json.toJson(description)

      json.as[TaxCodeDescription] mustBe description
    }
  }

  "TaxCodeDetails JSON serialization" must {
    "serialize and deserialize correctly" in {
      val details = TaxCodeDetails(
        employment = Some(List(Employments(Some(1), Some("Company A"), Some("1250L")))),
        taxCode = Some(List(TaxCode(Some("1250L"), Some(20)))),
        deductions = Some(List(TaxCodeComponent(Some("Deduction"), Some(1000), Some(2)))),
        allowances = Some(List(TaxCodeComponent(Some("Allowance"), Some(5000), Some(3))))
      )

      val json = Json.toJson(details)
      json.as[TaxCodeDetails] mustBe details
    }
  }

  "TotalLiability JSON serialization" must {
    "serialize and deserialize correctly" in {
      val liability = TotalLiability(
        totalTax = 5000,
        childBenefitAmount = 200,
        childBenefitTaxDue = 50
      )

      val json = Json.toJson(liability)
      json.as[TotalLiability] mustBe liability
    }
  }

  "CeasedEmploymentDetails JSON serialization" must {
    "serialize and deserialize correctly" in {
      val ceasedEmployment = CeasedEmploymentDetails(
        endDate = Some(LocalDate.of(2024, 3, 1)),
        isPension = Some(false),
        ceasedStatus = Some("Resigned"),
        employmentStatus = Some(1)
      )

      val json = Json.toJson(ceasedEmployment)
      json.as[CeasedEmploymentDetails] mustBe ceasedEmployment
    }
  }

  "MarriageAllowance JSON serialization" must {
    "serialize and deserialize correctly" in {
      val allowance = MarriageAllowance(1000, 500)
      val json = Json.toJson(allowance)

      json.as[MarriageAllowance] mustBe allowance
    }
  }

  "Adjustment JSON serialization" must {
    "serialize and deserialize correctly" in {
      val adjustment = Adjustment(200, 50)
      val json = Json.toJson(adjustment)

      json.as[Adjustment] mustBe adjustment
    }
  }

  "LiabilityReductions JSON serialization" must {
    "serialize and deserialize correctly" in {
      val reductions = LiabilityReductions(
        marriageAllowance = Some(MarriageAllowance(1000, 500)),
        enterpriseInvestmentSchemeRelief = Some(Adjustment(200, 50))
      )
      val json = Json.toJson(reductions)

      json.as[LiabilityReductions] mustBe reductions
    }
  }

  "LiabilityAdditions JSON serialization" must {
    "serialize and deserialize correctly" in {
      val additions = LiabilityAdditions(
        excessGiftAidTax = Some(Adjustment(200, 50)),
        excessWidowsAndOrphans = Some(Adjustment(100, 30))
      )
      val json = Json.toJson(additions)

      json.as[LiabilityAdditions] mustBe additions
    }
  }

  "GateKeeperRule JSON serialization" must {
    "serialize and deserialize correctly" in {
      val rule = GateKeeperRule(Some(1), Some(100), Some("Rule Description"))
      val json = Json.toJson(rule)

      json.as[GateKeeperRule] mustBe rule
    }
  }

  "GateKeeper JSON serialization" must {
    "serialize and deserialize correctly" in {
      val gateKeeper = GateKeeper(
        gateKeepered = true,
        gateKeeperResults = List(GateKeeperRule(Some(1), Some(100), Some("Rule Description")))
      )
      val json = Json.toJson(gateKeeper)

      json.as[GateKeeper] mustBe gateKeeper
    }
  }

  "TaxSummaryDetails JSON serialization" must {
    "serialize and deserialize correctly" in {
      val summary = TaxSummaryDetails(
        nino = "AA123456C",
        version = 1,
        totalLiability = Some(TotalLiability(totalTax = 5000, childBenefitAmount = 200, childBenefitTaxDue = 50))
      )

      val json = Json.toJson(summary)
      json.as[TaxSummaryDetails] mustBe summary
    }
  }

  "CYPlusOneChange JSON serialization" must {
    "serialize and deserialize correctly" in {
      val change = CYPlusOneChange(
        employmentsTaxCode = Some(List(Employments(Some(1), Some("Company A"), Some("1250L")))),
        scottishTaxCodes = Some(true),
        personalAllowance = Some(Change(BigDecimal(12500), BigDecimal(13000))),
        underPayment = Some(Change(BigDecimal(100), BigDecimal(50))),
        totalTax = Some(Change(BigDecimal(5000), BigDecimal(5200))),
        standardPA = Some(BigDecimal(12500)),
        employmentBenefits = Some(true),
        personalSavingsAllowance = Some(Change(BigDecimal(1000), BigDecimal(1200)))
      )

      val json = Json.toJson(change)
      json.as[CYPlusOneChange] mustBe change
    }
  }

  "RtiCalc JSON serialization" must {
    "serialize and deserialize correctly" in {
      val rtiCalc = RtiCalc(
        employmentType = 1,
        paymentDate = Some(LocalDate.of(2024, 3, 1)),
        payFrequency = Some(PayFrequency.Monthly),
        employmentId = 1001,
        employmentStatus = 1,
        employerName = "Company A",
        totalPayToDate = BigDecimal(50000),
        calculationResult = Some(BigDecimal(4500))
      )

      val json = Json.toJson(rtiCalc)
      json.as[RtiCalc] mustBe rtiCalc
    }
  }

  "IabdSummary JSON serialization" must {
    "serialize and deserialize correctly" in {
      val iabdSummary = IabdSummary(
        iabdType = 1,
        description = "Some Allowance",
        amount = BigDecimal(5000),
        employmentId = Some(123),
        estimatedPaySource = Some(1),
        employmentName = Some("Company A")
      )

      val json = Json.toJson(iabdSummary)
      json.as[IabdSummary] mustBe iabdSummary
    }
  }

  "TaxCodeIncomeSummary JSON serialization" must {
    "serialize and deserialize correctly" in {
      val summary = TaxCodeIncomeSummary(
        name = "Employment Income",
        taxCode = "1250L",
        employmentId = Some(1),
        employmentPayeRef = Some("123/ABC"),
        employmentType = Some(1),
        incomeType = Some(1),
        employmentStatus = Some(1),
        tax = Tax(
          totalIncome = Some(BigDecimal(50000)),
          totalTaxableIncome = Some(BigDecimal(40000)),
          totalTax = Some(BigDecimal(5000)),
          taxBands = Some(List(TaxBand(Some(20000), Some(2000), Some(0), Some(20000), Some(10))))
        ),
        worksNumber = Some("W123"),
        jobTitle = Some("Software Engineer"),
        startDate = Some(LocalDate.of(2023, 4, 1)),
        endDate = None,
        income = Some(BigDecimal(50000)),
        otherIncomeSourceIndicator = Some(false),
        isEditable = true,
        isLive = true,
        potentialUnderpayment = Some(BigDecimal(100)),
        basisOperation = Some(BasisOperation.Week1Month1)
      )

      val json = Json.toJson(summary)
      json.as[TaxCodeIncomeSummary] mustBe summary
    }
  }

  "TaxCodeIncomeTotal JSON serialization" must {
    "serialize and deserialize correctly" in {
      val incomeTotal = TaxCodeIncomeTotal(
        taxCodeIncomes = List(
          TaxCodeIncomeSummary(
            name = "Employment Income",
            taxCode = "1250L",
            employmentId = Some(1),
            tax = Tax(
              totalIncome = Some(BigDecimal(50000)),
              totalTaxableIncome = Some(BigDecimal(40000)),
              totalTax = Some(BigDecimal(5000)),
              taxBands = Some(List(TaxBand(Some(20000), Some(2000), Some(0), Some(20000), Some(10))))
            )
          )
        ),
        totalIncome = BigDecimal(50000),
        totalTax = BigDecimal(5000),
        totalTaxableIncome = BigDecimal(40000)
      )

      val json = Json.toJson(incomeTotal)
      json.as[TaxCodeIncomeTotal] mustBe incomeTotal
    }
  }

  "DecreasesTax JSON serialization" must {
    "serialize and deserialize correctly" in {
      val taxComponent = TaxComponent(BigDecimal(500), 1, "Blind Person", List())

      val decreasesTax = DecreasesTax(
        personalAllowance = Some(BigDecimal(12500)),
        personalAllowanceSourceAmount = Some(BigDecimal(12000)),
        blindPerson = Some(taxComponent),
        expenses = Some(taxComponent),
        giftRelated = Some(taxComponent),
        jobExpenses = Some(taxComponent),
        miscellaneous = Some(taxComponent),
        pensionContributions = Some(taxComponent),
        paTransferredAmount = Some(BigDecimal(250)),
        paReceivedAmount = Some(BigDecimal(300)),
        paTapered = true,
        personalSavingsAllowance = Some(taxComponent),
        total = BigDecimal(15000)
      )

      val json = Json.toJson(decreasesTax)
      json.as[DecreasesTax] mustBe decreasesTax
    }
  }

  "ExtensionRelief JSON serialization" must {
    "serialize and deserialize correctly" in {
      val extensionRelief = ExtensionRelief(sourceAmount = BigDecimal(5000), reliefAmount = BigDecimal(1500))

      val json = Json.toJson(extensionRelief)
      json.as[ExtensionRelief] mustBe extensionRelief
    }
  }

  "ExtensionReliefs JSON serialization" must {
    "serialize and deserialize correctly" in {
      val giftAidRelief = ExtensionRelief(sourceAmount = BigDecimal(3000), reliefAmount = BigDecimal(1000))
      val personalPensionRelief = ExtensionRelief(sourceAmount = BigDecimal(7000), reliefAmount = BigDecimal(2500))

      val extensionReliefs = ExtensionReliefs(
        giftAid = Some(giftAidRelief),
        personalPension = Some(personalPensionRelief)
      )

      val json = Json.toJson(extensionReliefs)
      json.as[ExtensionReliefs] mustBe extensionReliefs
    }

    "handle missing optional values correctly" in {
      val extensionReliefs = ExtensionReliefs(
        giftAid = None,
        personalPension = Some(ExtensionRelief(sourceAmount = BigDecimal(5000), reliefAmount = BigDecimal(2000)))
      )

      val json = Json.toJson(extensionReliefs)
      json.as[ExtensionReliefs] mustBe extensionReliefs
    }
  }

  "Incomes JSON serialization" must {
    "serialize and deserialize correctly" in {
      val taxCodeIncomeTotal = TaxCodeIncomeTotal(
        taxCodeIncomes = List(
          TaxCodeIncomeSummary(
            name = "Employment",
            taxCode = "1250L",
            tax = Tax(totalIncome = Some(BigDecimal(50000)), totalTax = Some(BigDecimal(5000)))
          )
        ),
        totalIncome = BigDecimal(50000),
        totalTax = BigDecimal(5000),
        totalTaxableIncome = BigDecimal(45000)
      )

      val noneTaxCodeIncomes = NoneTaxCodeIncomes(
        statePension = Some(BigDecimal(8000)),
        statePensionLumpSum = Some(BigDecimal(2000)),
        totalIncome = BigDecimal(10000)
      )

      val incomes = Incomes(
        taxCodeIncomes = TaxCodeIncomes(
          employments = Some(taxCodeIncomeTotal),
          occupationalPensions = None,
          taxableStateBenefitIncomes = None,
          ceasedEmployments = None,
          hasDuplicateEmploymentNames = false,
          totalIncome = BigDecimal(50000),
          totalTaxableIncome = BigDecimal(45000),
          totalTax = BigDecimal(5000)
        ),
        noneTaxCodeIncomes = noneTaxCodeIncomes,
        total = BigDecimal(60000)
      )

      val json = Json.toJson(incomes)
      json.as[Incomes] mustBe incomes
    }

    "handle missing optional values correctly" in {
      val incomes = Incomes(
        taxCodeIncomes = TaxCodeIncomes(
          employments = None,
          occupationalPensions = None,
          taxableStateBenefitIncomes = None,
          ceasedEmployments = None,
          hasDuplicateEmploymentNames = false,
          totalIncome = BigDecimal(0),
          totalTaxableIncome = BigDecimal(0),
          totalTax = BigDecimal(0)
        ),
        noneTaxCodeIncomes = NoneTaxCodeIncomes(
          statePension = None,
          statePensionLumpSum = None,
          totalIncome = BigDecimal(0)
        ),
        total = BigDecimal(0)
      )

      val json = Json.toJson(incomes)
      json.as[Incomes] mustBe incomes
    }
  }

  "NoneTaxCodeIncomes JSON serialization" must {

    "serialize and deserialize correctly with full data" in {
      val taxComponent = TaxComponent(
        amount = BigDecimal(1000),
        componentType = 1,
        description = "Other Income",
        iabdSummaries = List(IabdSummary(1, "Summary", BigDecimal(500)))
      )

      val noneTaxCodeIncomes = NoneTaxCodeIncomes(
        statePension = Some(BigDecimal(8000)),
        statePensionLumpSum = Some(BigDecimal(2000)),
        otherPensions = Some(taxComponent),
        otherIncome = Some(taxComponent),
        taxableStateBenefit = Some(taxComponent),
        untaxedInterest = Some(taxComponent),
        bankBsInterest = Some(taxComponent),
        dividends = Some(taxComponent),
        foreignInterest = Some(taxComponent),
        foreignDividends = Some(taxComponent),
        totalIncome = BigDecimal(25000)
      )

      val json = Json.toJson(noneTaxCodeIncomes)
      json.as[NoneTaxCodeIncomes] mustBe noneTaxCodeIncomes
    }

    "serialize and deserialize correctly with missing optional fields" in {
      val noneTaxCodeIncomes = NoneTaxCodeIncomes(
        statePension = None,
        statePensionLumpSum = None,
        otherPensions = None,
        otherIncome = None,
        taxableStateBenefit = None,
        untaxedInterest = None,
        bankBsInterest = None,
        dividends = None,
        foreignInterest = None,
        foreignDividends = None,
        totalIncome = BigDecimal(0)
      )

      val json = Json.toJson(noneTaxCodeIncomes)
      json.as[NoneTaxCodeIncomes] mustBe noneTaxCodeIncomes
    }
  }

  "IncomeData JSON serialization" must {

    "serialize and deserialize correctly" in {
      val incomeExplanation = IncomeExplanation(
        employerName = "Company A",
        incomeId = 123,
        payToDate = BigDecimal(20000),
        calcAmount = Some(BigDecimal(3000)),
        payFrequency = None
      )

      val incomeData = IncomeData(List(incomeExplanation))

      val json = Json.toJson(incomeData)
      json.as[IncomeData] mustBe incomeData
    }

    "handle empty lists" in {
      val incomeData = IncomeData(Nil)

      val json = Json.toJson(incomeData)
      json.as[IncomeData] mustBe incomeData
    }
  }

  "IncreasesTax JSON serialization" must {

    "serialize and deserialize correctly with all fields" in {
      val increasesTax = IncreasesTax(
        incomes = Some(
          Incomes(
            taxCodeIncomes = TaxCodeIncomes(
              employments = None,
              occupationalPensions = None,
              taxableStateBenefitIncomes = None,
              ceasedEmployments = None,
              hasDuplicateEmploymentNames = false,
              totalIncome = BigDecimal(50000),
              totalTaxableIncome = BigDecimal(40000),
              totalTax = BigDecimal(5000)
            ),
            noneTaxCodeIncomes = NoneTaxCodeIncomes(
              statePension = Some(BigDecimal(1000)),
              statePensionLumpSum = None,
              otherPensions = None,
              otherIncome = None,
              taxableStateBenefit = None,
              untaxedInterest = None,
              bankBsInterest = None,
              dividends = None,
              foreignInterest = None,
              foreignDividends = None,
              totalIncome = BigDecimal(50000)
            ),
            total = BigDecimal(100000)
          )
        ),
        benefitsFromEmployment = Some(
          TaxComponent(
            amount = BigDecimal(5000),
            componentType = 1,
            description = "Employment Benefits",
            iabdSummaries = List()
          )
        ),
        total = BigDecimal(150000)
      )

      val json = Json.toJson(increasesTax)
      json.as[IncreasesTax] mustBe increasesTax
    }

    "serialize and deserialize correctly with missing optional fields" in {
      val increasesTax = IncreasesTax(
        incomes = None,
        benefitsFromEmployment = None,
        total = BigDecimal(120000)
      )

      val json = Json.toJson(increasesTax)
      json.as[IncreasesTax] mustBe increasesTax
    }
  }
}
