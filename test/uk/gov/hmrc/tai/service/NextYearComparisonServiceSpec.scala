/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import data.NpsData
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.nps2.{TaxAccount, TaxDetail, TaxObject}
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live, PotentiallyCeased}

class NextYearComparisonServiceSpec extends UnitSpec {

  "stripCeasedFromNps" should {

    val totalIncome = BigDecimal(123.32)

    val npsIncomeSource = List(
      NpsIncomeSource(
        payAndTax = Some(NpsTax(totalIncome = Some(NpsComponent(Some(totalIncome))))),
        employmentStatus = Some(Live.code),
        taxCode = Some("LiveTC")),
      NpsIncomeSource(
        payAndTax = Some(NpsTax(totalIncome = Some(NpsComponent(Some(totalIncome))))),
        employmentStatus = Some(Ceased.code),
        taxCode = Some("CeasedTaxCode")),
      NpsIncomeSource(
        payAndTax = Some(NpsTax(totalIncome = Some(NpsComponent(Some(totalIncome))))),
        employmentStatus = Some(PotentiallyCeased.code),
        taxCode = Some("PotentiallyCeasedTaxCode")
      )
    )

    "set ceased employments TaxCode to 'Not applicable' and set their PayAndTax total income amount to zero. " +
      "Also totals correct non-savings total income amount with single iabd." in {

      val estPayAmount = BigDecimal(15000)

      val npsIabdSummaries =
        List(NpsIabdSummary(amount = Some(estPayAmount), `type` = Some(IabdType.NewEstimatedPay.code)))
      val nonSavings = NpsTax(
        totalIncome = Some(NpsComponent(amount = Some(BigDecimal(100.00)), iabdSummaries = Some(npsIabdSummaries))))
      val npsTotalLiability =
        NpsTotalLiability(nonSavings = Some(nonSavings), totalLiability = Some(BigDecimal(11111.12)))

      val npsTaxAccount =
        NpsTaxAccount(None, None, totalLiability = Some(npsTotalLiability), incomeSources = Some(npsIncomeSource))

      val result = (new NextYearComparisonService).stripCeasedFromNps(npsTaxAccount)

      result.incomeSources foreach { incomeSources =>
        incomeSources.size shouldBe 3

        incomeSources.head.taxCode shouldBe Some("LiveTC")
        incomeSources.head.payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(totalIncome)
          }
        }

        incomeSources(1).taxCode shouldBe Some("Not applicable")
        incomeSources(1).payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(BigDecimal(0))
          }
        }

        incomeSources(2).taxCode shouldBe Some("Not applicable")
        incomeSources(2).payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(BigDecimal(0))
          }
        }

      }

      result.totalLiability foreach { tl =>
        tl.nonSavings foreach { ns =>
          ns.totalIncome foreach { ti =>
            ti.amount shouldBe Some(estPayAmount)
          }
        }
      }

    }

    "set ceased employments TaxCode to 'Not applicable' and set their PayAndTax total income amount to zero." +
      "Also totals correct non-savings total income amount with multiple totalled iabd amount." in {

      val estPayAmount = BigDecimal(15000)
      val nonCodedIncomeAmount = BigDecimal(20000)

      val npsIabdSummaries = List(
        NpsIabdSummary(amount = Some(estPayAmount), `type` = Some(IabdType.NewEstimatedPay.code)),
        NpsIabdSummary(amount = Some(nonCodedIncomeAmount), `type` = Some(IabdType.NonCodedIncome.code))
      )

      val nonSavings = NpsTax(
        totalIncome = Some(NpsComponent(amount = Some(BigDecimal(100.00)), iabdSummaries = Some(npsIabdSummaries))))
      val npsTotalLiability =
        NpsTotalLiability(nonSavings = Some(nonSavings), totalLiability = Some(BigDecimal(11111.12)))

      val npsTaxAccount =
        NpsTaxAccount(None, None, totalLiability = Some(npsTotalLiability), incomeSources = Some(npsIncomeSource))

      val result = (new NextYearComparisonService).stripCeasedFromNps(npsTaxAccount)

      result.incomeSources foreach { incomeSources =>
        incomeSources.size shouldBe 3

        incomeSources.head.taxCode shouldBe Some("LiveTC")
        incomeSources.head.payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(totalIncome)
          }
        }

        incomeSources(1).taxCode shouldBe Some("Not applicable")
        incomeSources(1).payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(BigDecimal(0))
          }
        }

        incomeSources(2).taxCode shouldBe Some("Not applicable")
        incomeSources(2).payAndTax foreach { pat =>
          pat.totalIncome foreach { ti =>
            ti.amount shouldBe Some(BigDecimal(0))
          }
        }

      }

      result.totalLiability foreach { tl =>
        tl.nonSavings foreach { ns =>
          ns.totalIncome foreach { ti =>
            ti.amount shouldBe Some(estPayAmount + nonCodedIncomeAmount)
          }
        }
      }

    }

  }

  "proccessTaxSummaryWithCYPlusOne " should {

    "return an empty change object when there are no differences between cy and cy+1." in {

      val currentYearTaxAccount = TaxSummaryDetails("", 2016)
      val nextYearYearTaxAccount = TaxSummaryDetails("", 2016)

      val result =
        (new NextYearComparisonService).proccessTaxSummaryWithCYPlusOne(currentYearTaxAccount, nextYearYearTaxAccount)

      result.cyPlusOneChange.isDefined shouldBe false

    }

    "Contain the change object for CY+1 in the tax summary model when a change is present " in {

      val current = NpsData.getNpsBankInterestAllHigherRateTaxAccount().toTaxSummary(1, Nil, Nil)
      val nextYear = NpsData.getNpsBasicRateExtnTaxAccount().toTaxSummary(1, Nil, Nil)

      val result = (new NextYearComparisonService).proccessTaxSummaryWithCYPlusOne(current, nextYear)

      result.cyPlusOneChange.isDefined shouldBe true

    }
  }

  "cyPlusOneEmploymentTaxCodes " should {

    "return the change object with the list of cy+1 employments tax code " in {
      val employments = List(
        Employments(Some(1), Some("Employer 1"), taxCode = Some("1500L")),
        Employments(Some(1), Some("Employer 2"), taxCode = Some("15T")))

      val nextYear =
        TaxSummaryDetails("", 1, taxCodeDetails = Some(TaxCodeDetails(Some(employments), None, None, None, None)))

      val result = (new NextYearComparisonService).cyPlusOneEmploymentTaxCodes(nextYear, CYPlusOneChange())

      result.employmentsTaxCode shouldBe Some(employments)
    }

    "return an empty change object when there is no cy+1 employments tax code " in {

      val nextYear = TaxSummaryDetails("", 1, taxCodeDetails = Some(TaxCodeDetails(None, None, None, None, None)))

      val result = (new NextYearComparisonService).cyPlusOneEmploymentTaxCodes(nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.employmentsTaxCode shouldBe None
    }
  }

  "cyPlusOneScottishTaxCodes " should {

    "return the change object with true when the cy+1 tax code is scottish " in {
      val employments = List(
        Employments(Some(1), Some("Employer 1"), taxCode = Some("SK1234")),
        Employments(Some(1), Some("Employer 2"), taxCode = Some("160L")))

      val nextYear =
        TaxSummaryDetails("", 1, taxCodeDetails = Some(TaxCodeDetails(Some(employments), None, None, None, None)))

      val result = (new NextYearComparisonService).cyPlusOneScottishTaxCodes(nextYear, CYPlusOneChange())

      result.scottishTaxCodes shouldBe Some(true)
    }

    "return an empty change object when there is no cy+1 scottish tax code " in {

      val employments = List(
        Employments(Some(1), Some("Employer 1"), taxCode = Some("160L")),
        Employments(Some(1), Some("Employer 2"), taxCode = Some("160L")))

      val nextYear =
        TaxSummaryDetails("", 1, taxCodeDetails = Some(TaxCodeDetails(Some(employments), None, None, None, None)))

      val result = (new NextYearComparisonService).cyPlusOneScottishTaxCodes(nextYear, CYPlusOneChange())

      result.scottishTaxCodes shouldBe Some(false)
    }

    "return an empty change object when there is no employments " in {

      val nextYear = TaxSummaryDetails("", 1, taxCodeDetails = Some(TaxCodeDetails(None, None, None, None, None)))

      val result = (new NextYearComparisonService).cyPlusOneScottishTaxCodes(nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.scottishTaxCodes shouldBe None
    }
  }

  "cyPlusOnePersonalAllowance " should {

    "return an empty change object when the personal allowance amounts are the same for cy and cy+1. " in {

      val currentYearAmount = 100
      val nextYearAmount = 100

      val current = TaxSummaryDetails(
        "",
        1,
        decreasesTax =
          Some(DecreasesTax(personalAllowance = Some(BigDecimal(currentYearAmount)), total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        decreasesTax = Some(DecreasesTax(personalAllowance = Some(BigDecimal(nextYearAmount)), total = BigDecimal(0))))

      val result = (new NextYearComparisonService).cyPlusOnePersonalAllowance(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.personalAllowance shouldBe None

    }

    "Contain personal allowance change in cy+1 " in {

      val current = NpsData.getNpsBankInterestAndDividendsTaxAccount().toTaxSummary(1, Nil)
      val nextYear = NpsData.getNpsGiftAidTaxAccount().toTaxSummary(1, Nil)

      val result = (new NextYearComparisonService).cyPlusOnePersonalAllowance(current, nextYear, CYPlusOneChange())

      result.personalAllowance shouldBe Some(Change(10600, 0))
    }

  }

  "cyPlusOneUnderPayment " should {

    "return an empty change object when the underpayment amounts are the same for cy and cy+1. " in {

      val currentYearUnderpayment = 100
      val nextYearUnderpayment = 100

      val current = TaxSummaryDetails(
        "",
        1,
        totalLiability = Some(
          TotalLiability(underpaymentPreviousYear = BigDecimal(currentYearUnderpayment), totalTax = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        totalLiability =
          Some(TotalLiability(underpaymentPreviousYear = BigDecimal(nextYearUnderpayment), totalTax = BigDecimal(0))))

      val result = (new NextYearComparisonService).cyPlusOneUnderPayment(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.personalAllowance shouldBe None

    }

    "Contain underpayment change in cy+1 " in {

      val current = NpsData.getNpsPotentialUnderpaymentTaxAccount().toTaxSummary(1, Nil)
      val nextYear = NpsData.getNpsChildBenefitTaxAccount().toTaxSummary(1, Nil)

      val result = (new NextYearComparisonService).cyPlusOneUnderPayment(current, nextYear, CYPlusOneChange())

      result.underPayment shouldBe Some(Change(0, 1000))
    }
  }

  "cyPlusOneTotalTax " should {

    "return an empty change object when the total tax amounts are the same for cy and cy+1. " in {

      val currentYearTotalTax = 100
      val nextYearTotalTax = 100

      val current =
        TaxSummaryDetails("", 1, totalLiability = Some(TotalLiability(totalTax = BigDecimal(currentYearTotalTax))))

      val nextYear =
        TaxSummaryDetails("", 1, totalLiability = Some(TotalLiability(totalTax = BigDecimal(nextYearTotalTax))))

      val result = (new NextYearComparisonService).cyPlusOneTotalTax(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.totalTax shouldBe None

    }

    "Contain total tax change in cy+1 " in {

      val annualAccounts = List(
        AnnualAccount(
          TaxYear(2016),
          Some(TaxAccount(
            None,
            None,
            1564.45,
            Map(TaxObject.Type.NonSavings -> TaxDetail(
              Some(1993.80),
              Some(9969),
              None,
              Seq(
                nps2.TaxBand(Some("pa"), None, 2290, 0, None, None, 0),
                nps2.TaxBand(Some("B"), None, 9969, 1993.80, Some(0), Some(33125), 20.00))
            ))
          ))
        ))

      val annualAccountsNy = List(
        AnnualAccount(
          TaxYear(2016),
          Some(TaxAccount(
            None,
            None,
            1564.45,
            Map(TaxObject.Type.NonSavings -> TaxDetail(
              Some(2000.80),
              Some(9969),
              None,
              Seq(
                nps2.TaxBand(Some("pa"), None, 2290, 0, None, None, 0),
                nps2.TaxBand(Some("B"), None, 9969, 1993.80, Some(0), Some(33125), 20.00))
            ))
          ))
        ))

      val current = NpsData.getNpsBankInterestAndDividendsTaxAccount().toTaxSummary(1, Nil, accounts = annualAccounts)
      val nextYear = NpsData.getNpsChildBenefitTaxAccount().toTaxSummary(1, Nil, accounts = annualAccountsNy)

      val result = (new NextYearComparisonService).cyPlusOneTotalTax(current, nextYear, CYPlusOneChange())
      result.totalTax shouldBe Some(Change(1297.2, 1680.1))
    }
  }

  "cyPlusOneEmpBenefits " should {

    "return an empty change object when the total tax amounts are the same for cy and cy+1. " in {

      val currentYearEmpBenefits = 100
      val nextYearEmpBenefits = 100

      val current = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(currentYearEmpBenefits), 1, "", Nil)),
            total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(nextYearEmpBenefits), 1, "", Nil)),
            total = BigDecimal(0))))

      val result = (new NextYearComparisonService).cyPlusOneEmpBenefits(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.employmentBenefits shouldBe None

    }

    "return stuff where iabd types and employments continue from cy to cy+1" in {

      val currentYearEmpBenefits = 100
      val nextYearEmpBenefits = 100

      val currentYearDeductions = 50
      val nextYearDeductions = 50

      val currentIabdSummaries = List(IabdSummary(29, "", currentYearDeductions, None, None))
      val nextYearIabdSummaries = List(IabdSummary(29, "", nextYearDeductions, None, None))

      val current = TaxSummaryDetails(
        "",
        1,
        increasesTax =
          Some(
            IncreasesTax(
              benefitsFromEmployment =
                Some(TaxComponent(BigDecimal(currentYearEmpBenefits), 1, "", currentIabdSummaries)),
              total = BigDecimal(0)))
      )

      val nextYear = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(nextYearEmpBenefits), 1, "", nextYearIabdSummaries)),
            total = BigDecimal(0)))
      )

      val result = (new NextYearComparisonService).cyPlusOneEmpBenefits(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.employmentBenefits shouldBe None

    }

    "return an empty change object when the benefit in kind totals are the same for cy and cy+1. " in {

      val IADB_TYPE_BENEFITS_IN_KIND_TOTAL = 28

      val currentBenefitInKindAmount = 100
      val nextYearBenefitInKindAmount = 100

      val currentIabdSummaries =
        List(IabdSummary(IADB_TYPE_BENEFITS_IN_KIND_TOTAL, "", currentBenefitInKindAmount, None, None))
      val nextYearIabdSummaries =
        List(IabdSummary(IADB_TYPE_BENEFITS_IN_KIND_TOTAL, "", nextYearBenefitInKindAmount, None, None))

      val current = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(1), 1, "", currentIabdSummaries)),
            total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(1), 1, "", nextYearIabdSummaries)),
            total = BigDecimal(0))))

      val result = (new NextYearComparisonService).cyPlusOneEmpBenefits(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()
      result.employmentBenefits shouldBe None

    }

    "return an updated change object (employmentBenefits) when the benefit in kind totals are different for cy and cy+1. " in {

      val IADB_TYPE_BENEFITS_IN_KIND_TOTAL = 28

      val currentBenefitInKindAmount = 100
      val nextYearBenefitInKindAmount = 101

      val currentIabdSummaries =
        List(IabdSummary(IADB_TYPE_BENEFITS_IN_KIND_TOTAL, "", currentBenefitInKindAmount, None, None))
      val nextYearIabdSummaries =
        List(IabdSummary(IADB_TYPE_BENEFITS_IN_KIND_TOTAL, "", nextYearBenefitInKindAmount, None, None))

      val current = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(1), 1, "", currentIabdSummaries)),
            total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        increasesTax = Some(
          IncreasesTax(
            benefitsFromEmployment = Some(TaxComponent(BigDecimal(1), 1, "", nextYearIabdSummaries)),
            total = BigDecimal(0))))

      val result = (new NextYearComparisonService).cyPlusOneEmpBenefits(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange(employmentBenefits = Some(true))
      result.employmentBenefits shouldBe Some(true)

    }

    "Contain company benefit change in cy+1 " in {

      val current = NpsData.getNpsBasicRateExtnTaxAccount().toTaxSummary(1, Nil)
      val nextYear = NpsData.getNpsNonCodedTaxAccount().toTaxSummary(1, Nil)

      val result = (new NextYearComparisonService).cyPlusOneEmpBenefits(current, nextYear, CYPlusOneChange())
      result.employmentBenefits shouldBe Some(true)
    }
  }

  "cyPlusOnePersonalSavingsAllowance" should {

    "return an empty change object when the PSA amounts are the same for cy and cy+1." in {

      val currentYearPSA = 100
      val nextYearPSA = 100

      val current = TaxSummaryDetails(
        "",
        1,
        decreasesTax = Some(
          DecreasesTax(
            personalSavingsAllowance = Some(TaxComponent(currentYearPSA, 0, "", Nil)),
            total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        decreasesTax = Some(
          DecreasesTax(personalSavingsAllowance = Some(TaxComponent(nextYearPSA, 0, "", Nil)), total = BigDecimal(0))))

      val result =
        (new NextYearComparisonService).cyPlusOnePersonalSavingsAllowance(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange()

      result.personalSavingsAllowance shouldBe None

    }

    "return a change object which shows the correct change values when the PSA amounts are different for cy and cy+1." in {

      val currentYearPSA = 101
      val nextYearPSA = 100

      val current = TaxSummaryDetails(
        "",
        1,
        decreasesTax = Some(
          DecreasesTax(
            personalSavingsAllowance = Some(TaxComponent(currentYearPSA, 0, "", Nil)),
            total = BigDecimal(0))))

      val nextYear = TaxSummaryDetails(
        "",
        1,
        decreasesTax = Some(
          DecreasesTax(personalSavingsAllowance = Some(TaxComponent(nextYearPSA, 0, "", Nil)), total = BigDecimal(0))))

      val result =
        (new NextYearComparisonService).cyPlusOnePersonalSavingsAllowance(current, nextYear, CYPlusOneChange())

      result shouldBe CYPlusOneChange(personalSavingsAllowance = Some(Change(currentYearPSA, nextYearPSA)))

      result.personalSavingsAllowance.isDefined shouldBe true

      result.personalSavingsAllowance foreach { psa =>
        psa.currentYear shouldBe currentYearPSA
        psa.currentYearPlusOne shouldBe nextYearPSA
      }
    }
  }
}
