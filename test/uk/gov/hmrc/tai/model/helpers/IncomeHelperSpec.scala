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
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps._
import uk.gov.hmrc.tai.model.enums.IncomeType._
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.Income.{Ceased, Live, PotentiallyCeased}
import uk.gov.hmrc.tai.util.TaiConstants

class IncomeHelperSpec extends PlaySpec {

  "isEditableByUser" must {
    "return if user is editable or not when given different employment status" when {
      val isEditable =
        (employmentStatus: Option[Int]) => sut.isEditableByUser(Some(false), None, Some(false), employmentStatus)

      "user is live" in {
        isEditable(Some(Live.code)) mustBe true
      }

      "user is potentially ceased" in {
        isEditable(Some(PotentiallyCeased.code)) mustBe true
      }

      "user is ceased" in {
        isEditable(Some(Ceased.code)) mustBe false
      }

      "user status status is not defined" in {
        isEditable(None) mustBe true
      }
    }

    "return if user is editable or not when given different source indicators" when {
      val isEditable = (otherIncomeSourceIndicator: Option[Boolean]) =>
        sut.isEditableByUser(otherIncomeSourceIndicator, None, Some(false), Some(Live.code))

      "otherIncomeSourceIndicator is true" in {
        isEditable(Some(true)) mustBe false
      }

      "otherIncomeSourceIndicator is false" in {
        isEditable(Some(false)) mustBe true
      }

      "user status is not defined" in {
        isEditable(None) mustBe true
      }
    }

    "return if user is editable or not when given cessationPayThisEmployment" when {
      val isEditable = (cessationPayThisEmployment: Option[BigDecimal]) =>
        sut.isEditableByUser(None, cessationPayThisEmployment, Some(false), Some(Live.code))

      "cessationPayThisEmployment is true" in {
        isEditable(Some(2)) mustBe false
      }

      "cessationPayThisEmployment is not defined" in {
        isEditable(None) mustBe true
      }
    }

    "return if user is editable or not when given different jsa indicators" when {
      val isEditable =
        (jsaIndicator: Option[Boolean]) => sut.isEditableByUser(None, None, jsaIndicator, Some(Live.code))

      "jsaIndicator is true" in {
        isEditable(Some(true)) mustBe false
      }

      "jsaIndicator is false" in {
        isEditable(Some(false)) mustBe true
      }

      "jsaIndicator is not defined" in {
        isEditable(None) mustBe true
      }
    }
  }

  "isPrimary" must {
    "return if employment is primary" when {
      "employmentType is primary" in {
        sut.isPrimary(Some(TaiConstants.PrimaryEmployment)) mustBe true
      }

      "employmentType is None" in {
        sut.isPrimary(None) mustBe true
      }

      "employmentType is Not primary" in {
        sut.isPrimary(Some(4)) mustBe false
      }
    }
  }

  "isLive" must {
    "return if employment is live" when {
      "employmentStatus is primary" in {
        sut.isLive(Some(Live.code)) mustBe true
      }

      "employmentStatus is None" in {
        sut.isLive(None) mustBe true
      }

      "employmentStatus is Not Live" in {
        sut.isLive(Some(PotentiallyCeased.code)) mustBe false
      }
    }
  }

  "getFromAdjustedNetIncome" must {
    "return iabd amount" when {
      "given NPSComponent and iabd type" in {
        sut.getFromAdjustedNetIncome(Some(npsComponent), GiftAidPayments.code) mustBe 15000
      }

      "given no NPSComponent" in {
        sut.getFromAdjustedNetIncome(None, GiftAidPayments.code) mustBe 0
      }

      "given no iabds in NPSComponent" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getFromAdjustedNetIncome(modifiedNpsComponent, GiftAidPayments.code) mustBe 0
      }
    }
  }

  "getGiftFromAdjustedNetIncome" must {
    "return gift amount" when {
      "given no income" in {
        sut.getGiftFromAdjustedNetIncome() mustBe 0
      }

      "given income with no gift paid amount" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getGiftFromAdjustedNetIncome(modifiedNpsComponent) mustBe 0
      }

      "given income with total gift aid payments" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(iabdSummaries = Some(List(modifiableIabdSummaryType(Some(TotalGiftAidPayments.code))))))
        sut.getGiftFromAdjustedNetIncome(modifiedNpsComponent) mustBe 14000
      }

      "given income with GiftAidPayments and GiftAidTreatedAsPaidInPreviousTaxYear" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(iabdSummaries =
            Some(List(iabdSummary1, modifiableIabdSummaryType(Some(GiftAidTreatedAsPaidInPreviousTaxYear.code))))))
        sut.getGiftFromAdjustedNetIncome(modifiedNpsComponent) mustBe 1000
      }
    }
  }

  "isEditableByAutoUpdateService" must {
    "inform if AutoUpdateService can edit" when {
      "there is no otherIncomeSource and no jsaIndicator" in {
        sut.isEditableByAutoUpdateService(None, None) mustBe true
      }

      "there otherIncomeSource is false and no jsaIndicator" in {
        sut.isEditableByAutoUpdateService(Some(false), None) mustBe true
      }

      "there otherIncomeSource is true and no true" in {
        sut.isEditableByAutoUpdateService(Some(true), Some(true)) mustBe false
      }
    }
  }

  "getIBFromAdjustedNetIncome" must {
    "return iabd amount for incapacity benefits" when {
      "given NPSComponent and iabd type as incapacity benefits" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(IncapacityBenefit.code))))))
        sut.getIBFromAdjustedNetIncome(modifiedNpsComponent) mustBe Some(14000)
      }

      "given no NPSComponent" in {
        sut.getIBFromAdjustedNetIncome(None) mustBe None
      }

      "given no iabds in NPSComponent" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getIBFromAdjustedNetIncome(modifiedNpsComponent) mustBe None
      }

      "given iabds have no incapacity benefits" in {
        sut.getIBFromAdjustedNetIncome(Some(npsComponent)) mustBe None
      }
    }
  }

  "getIabdAmountFromIncome" must {
    "return iabd amount" when {
      val iabdFilter = (npsIabdSummary: NpsIabdSummary) => npsIabdSummary.`type`.contains(GiftAidPayments.code)

      "given NPSComponent and iabd type" in {
        sut.getIabdAmountFromIncome(Some(npsComponent), iabdFilter) mustBe Some(15000)
      }

      "given no NPSComponent" in {
        sut.getIabdAmountFromIncome(None, iabdFilter) mustBe None
      }

      "given no iabds in NPSComponent" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getIabdAmountFromIncome(modifiedNpsComponent, iabdFilter) mustBe None
      }

      "given iabds have no element representing iabd" in {
        val iabdFilter = (npsIabdSummary: NpsIabdSummary) => npsIabdSummary.`type`.contains(IncapacityBenefit.code)
        sut.getIabdAmountFromIncome(Some(npsComponent), iabdFilter) mustBe None
      }
    }
  }

  "getESAFromAdjustedNetIncome" must {
    "return iabd amount for employment and support allowance" when {
      "given NPSComponent and iabd type as employment and support allowance" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(iabdSummaries =
            Some(List(iabdSummary1, modifiableIabdSummaryType(Some(EmploymentAndSupportAllowance.code))))))
        sut.getESAFromAdjustedNetIncome(modifiedNpsComponent) mustBe Some(14000)
      }

      "given no NPSComponent" in {
        sut.getESAFromAdjustedNetIncome(None) mustBe None
      }

      "given no iabds in NPSComponent" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getESAFromAdjustedNetIncome(modifiedNpsComponent) mustBe None
      }

      "given iabds have no incapacity benefits" in {
        sut.getESAFromAdjustedNetIncome(Some(npsComponent)) mustBe None
      }
    }
  }

  "getEstimatedPayFromAdjustedNetIncome" must {
    "return net income amount for employment" when {

      val EmploymentId = Some(4)

      "given NPSComponent and iabd type as net income" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(NewEstimatedPay.code))))))
        sut.getEstimatedPayFromAdjustedNetIncome(modifiedNpsComponent, EmploymentId) mustBe Some(14000)
      }

      "given employment id not in the income list" in {
        val modifiedNpsComponent = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(NewEstimatedPay.code))))))
        sut.getEstimatedPayFromAdjustedNetIncome(modifiedNpsComponent, Some(1)) mustBe None
      }

      "given no NPSComponent" in {
        sut.getEstimatedPayFromAdjustedNetIncome(None, None) mustBe None
      }

      "given no iabds in NPSComponent" in {
        val modifiedNpsComponent = Some(npsComponent.copy(iabdSummaries = Some(List())))
        sut.getEstimatedPayFromAdjustedNetIncome(modifiedNpsComponent, EmploymentId) mustBe None
      }

      "given iabds have no net income" in {
        sut.getEstimatedPayFromAdjustedNetIncome(Some(npsComponent), EmploymentId) mustBe None
      }
    }
  }

  "isOccupationalPension" must {
    def isOccupationalPensionWithDefaultNoneParams(
      empTaxDistrictNum: Option[Int] = None,
      empPayeRef: Option[String] = None,
      pensionInd: Option[Boolean] = None) = sut.isOccupationalPension(empTaxDistrictNum, empPayeRef, pensionInd)

    "return true if employment has pension" when {
      "given no input" in {
        isOccupationalPensionWithDefaultNoneParams() mustBe false
      }

      "given true as pension indicator" in {
        isOccupationalPensionWithDefaultNoneParams(pensionInd = Some(false)) mustBe false
      }

      "given pension financial tax district number and pension financial paye reference" in {
        isOccupationalPensionWithDefaultNoneParams(
          empTaxDistrictNum = Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT),
          empPayeRef = Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER)) mustBe true
      }

      "given pension financial tax district number and some other paye reference" in {
        isOccupationalPensionWithDefaultNoneParams(
          empTaxDistrictNum = Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT),
          empPayeRef = Some(TaiConstants.ESA_PAYE_NUMBER)) mustBe false
      }
    }
  }

  "getAllIncomes" must {
    "calculate merged incomes" when {
      "given no input" in {
        sut.getAllIncomes(Nil) mustBe Nil
      }

      "given Income sources" in {
        val incomeSources = Some(List(income1, pension))
        sut.getAllIncomes(Nil, incomeSources) mustBe List(
          MergedEmployment(income1, None, None),
          MergedEmployment(pension, None, None))
      }

      "given Income source of secondary employment and employment support allowance" in {
        val incomeSource = modifiableIncome(TaiConstants.ESA_TAX_DISTRICT, TaiConstants.ESA_PAYE_NUMBER)
        val adjustedNetIncome = Some(
          npsComponent.copy(iabdSummaries =
            Some(List(iabdSummary1, modifiableIabdSummaryType(Some(EmploymentAndSupportAllowance.code))))))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given Income source of secondary employment and incapacity benefits" in {
        val incomeSource =
          modifiableIncome(TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT, TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)
        val adjustedNetIncome = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(IncapacityBenefit.code))))))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given Income source of secondary employment and JSA Tax" in {
        val incomeSource = modifiableIncome(TaiConstants.JSA_TAX_DISTRICT, TaiConstants.JSA_PAYE_NUMBER)
        val adjustedNetIncome = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(IncapacityBenefit.code))))))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given Income source of secondary employment and JSA student tax" in {
        val incomeSource =
          modifiableIncome(TaiConstants.JSA_STUDENTS_TAX_DISTRICT, TaiConstants.JSA_STUDENTS_PAYE_NUMBER)
        val adjustedNetIncome = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(IncapacityBenefit.code))))))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given Income source of secondary employment and JSA DWP tax" in {
        val incomeSource = modifiableIncome(TaiConstants.JSA_NEW_DWP_TAX_DISTRICT, TaiConstants.JSA_NEW_DWP_PAYE_NUMBER)
        val adjustedNetIncome = Some(
          npsComponent.copy(
            iabdSummaries = Some(List(iabdSummary1, modifiableIabdSummaryType(Some(IncapacityBenefit.code))))))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given Income source of primary employment and jsa indicator as true" in {
        val adjustedNetIncome =
          Some(npsComponent.copy(iabdSummaries = Some(List(modifiableIabdSummaryType(Some(NewEstimatedPay.code))))))
        val incomeSource = income1.copy(jsaIndicator = Some(true))
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, None))
      }

      "given Income source of secondary employment and calculate adjustment pay" in {
        val adjustedNetIncome =
          Some(npsComponent.copy(iabdSummaries = Some(List(modifiableIabdSummaryType(Some(NewEstimatedPay.code))))))
        val incomeSource = modifiableIncome(0, "")
        sut.getAllIncomes(Nil, Some(List(incomeSource)), adjustedNetIncome) mustBe List(
          MergedEmployment(incomeSource, None, Some(14000)))
      }

      "given NPS Employment and no income sources" in {
        sut.getAllIncomes(List(employment1, employment2)) mustBe Nil
      }

      "given NPS employment with primary ceased income" in {
        val incomeAmount1: Option[BigDecimal] = Some(15000)
        val incomeAmount2: Option[BigDecimal] = Some(1291)
        val employmentsDetails = List(employment1, employment2)
        val income1AdjustedNetIncome = NpsIabdSummary(amount = incomeAmount1, `type` = Some(27), employmentId = Some(1))
        val income2AdjustedNetIncome = NpsIabdSummary(amount = incomeAmount2, `type` = Some(27), employmentId = Some(2))

        val adjustedNetIncomes = List(income1AdjustedNetIncome, income2AdjustedNetIncome)
        val adjustedNetIncomeForCeased =
          NpsComponent(amount = Some(16448), `type` = None, iabdSummaries = Some(adjustedNetIncomes))

        val mergedIncomes = sut.getAllIncomes(employmentsDetails, None, Some(adjustedNetIncomeForCeased))

        mergedIncomes.size mustBe 2
        mergedIncomes.head mustBe MergedEmployment(
          employment1.toNpsIncomeSource(incomeAmount1.get),
          Some(employment1),
          incomeAmount1)
        mergedIncomes.last mustBe MergedEmployment(
          employment2.toNpsIncomeSource(incomeAmount2.get),
          Some(employment2),
          incomeAmount2)
      }

      "given NPS employment with secondary employment and ESA income source" in {
        val incomeAmount1: Option[BigDecimal] = Some(15000)
        val incomeAmount2: Option[BigDecimal] = Some(1291)
        val employmentsDetails = List(employment1, employmentESA)
        val income1AdjustedNetIncome = NpsIabdSummary(amount = incomeAmount1, `type` = Some(27), employmentId = Some(1))
        val income2AdjustedNetIncome =
          NpsIabdSummary(amount = incomeAmount2, `type` = Some(123), employmentId = Some(2))

        val adjustedNetIncomes = List(income1AdjustedNetIncome, income2AdjustedNetIncome)
        val adjustedNetIncomeForCeased =
          NpsComponent(amount = Some(16448), `type` = None, iabdSummaries = Some(adjustedNetIncomes))

        val mergedIncomes = sut.getAllIncomes(employmentsDetails, None, Some(adjustedNetIncomeForCeased))

        mergedIncomes.size mustBe 2
        mergedIncomes.head mustBe MergedEmployment(
          employment1.toNpsIncomeSource(incomeAmount1.get),
          Some(employment1),
          incomeAmount1)
        mergedIncomes.last mustBe MergedEmployment(
          employmentESA.toNpsIncomeSource(incomeAmount2.get),
          Some(employmentESA),
          incomeAmount2)
      }

      "given both nps employment and income source" in {
        val incomeAmount1: Option[BigDecimal] = Some(15000)
        val employmentsDetails = List(employment1)
        val income1AdjustedNetIncome = NpsIabdSummary(amount = incomeAmount1, `type` = Some(27), employmentId = Some(1))

        val adjustedNetIncomes = List(income1AdjustedNetIncome)
        val adjustedNetIncomeForCeased =
          NpsComponent(amount = Some(16448), `type` = None, iabdSummaries = Some(adjustedNetIncomes))

        val incomeSources = Some(List(income1))
        val mergedIncomes = sut.getAllIncomes(employmentsDetails, incomeSources, Some(adjustedNetIncomeForCeased))

        mergedIncomes.size mustBe 1
        mergedIncomes.head mustBe MergedEmployment(income1, Some(employment1), Some(15000))
      }
    }
  }

  "incomeType" must {
    "return a valid incomeType code" when {

      def incomeTypeTestWithDefaultNoneParams(
        tdn: Option[Int] = None,
        pr: Option[String] = None,
        et: Option[Int] = None,
        jsaInd: Option[Boolean] = None,
        penInd: Option[Boolean] = None) = sut.incomeType(tdn, pr, et, jsaInd, penInd)

      "No Tax District number and Paye Ref is provided" in {
        incomeTypeTestWithDefaultNoneParams() mustBe IncomeTypeEmployment.code
      }

      "Tax District number and Paye Ref is state pension lumpsum" in {
        incomeTypeTestWithDefaultNoneParams(
          Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER)) mustBe IncomeTypeStatePensionLumpSum.code
      }

      "Income has Occupation Pension Indicator true" in {
        incomeTypeTestWithDefaultNoneParams(penInd = Some(true)) mustBe IncomeTypeOccupationalPension.code
      }

      "Income has Occupation Pension" in {
        incomeTypeTestWithDefaultNoneParams(
          Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_TAX_DISTRICT),
          Some(TaiConstants.PENSION_FINANCIAL_ASSISTANCE_PAYE_NUMBER)) mustBe IncomeTypeOccupationalPension.code
      }

      "Tax District number and Paye Ref is Employment support allowance" in {
        incomeTypeTestWithDefaultNoneParams(Some(TaiConstants.ESA_TAX_DISTRICT), Some(TaiConstants.ESA_PAYE_NUMBER)) mustBe IncomeTypeESA.code
      }

      "Tax District number and Paye Ref is Incapacity benefit tax" in {
        incomeTypeTestWithDefaultNoneParams(
          Some(TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT),
          Some(TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)) mustBe IncomeTypeIB.code
      }

      "Tax District number and Paye Ref is JSA" in {
        incomeTypeTestWithDefaultNoneParams(Some(TaiConstants.JSA_TAX_DISTRICT), Some(TaiConstants.JSA_PAYE_NUMBER)) mustBe IncomeTypeJSA.code
      }

      "Tax District number and Paye Ref is JSA student tax" in {
        incomeTypeTestWithDefaultNoneParams(Some(TaiConstants.JSA_TAX_DISTRICT), Some(TaiConstants.JSA_PAYE_NUMBER)) mustBe IncomeTypeJSA.code
      }

      "Tax District number and Paye Ref is JSA DWP tax" in {
        incomeTypeTestWithDefaultNoneParams(Some(TaiConstants.JSA_TAX_DISTRICT), Some(TaiConstants.JSA_PAYE_NUMBER)) mustBe IncomeTypeJSA.code
      }

      "Primary employment with JSA indicator is true" in {
        incomeTypeTestWithDefaultNoneParams(et = Some(TaiConstants.PrimaryEmployment), jsaInd = Some(true)) mustBe IncomeTypeJSA.code
      }
    }
  }

  val iabdSummary1 =
    NpsIabdSummary(amount = Some(BigDecimal(15000)), `type` = Some(GiftAidPayments.code), employmentId = Some(1))
  val iabdSummary2 = NpsIabdSummary(amount = Some(BigDecimal(1291)), `type` = Some(27), employmentId = Some(2))
  val iabdSummary3 = NpsIabdSummary(amount = Some(BigDecimal(23)), `type` = Some(23), employmentId = Some(3))
  val modifiableIabdSummaryType = (modifiedType: Option[Int]) =>
    NpsIabdSummary(amount = Some(BigDecimal(14000)), `type` = modifiedType, employmentId = Some(4))
  val npsComponent = NpsComponent(
    Some(BigDecimal(999.01)),
    Some(1),
    Option(List(iabdSummary1, iabdSummary2, iabdSummary3)),
    Some("npsDescription"))

  val income1 = NpsIncomeSource(
    name = Some("primary"),
    taxCode = Some("taxCode1"),
    employmentType = Some(1),
    employmentStatus = Some(Live.code),
    employmentId = Some(1))
  val pension = NpsIncomeSource(
    Some("pension1"),
    Some("taxCodePension"),
    Some(3),
    None,
    None,
    None,
    Some(5),
    Some(Live.code),
    None,
    None,
    Some(true),
    Some(true))
  val modifiableIncome = (etdn: Int, epr: String) =>
    NpsIncomeSource(
      name = Some("name"),
      taxCode = Some("taxCodeB"),
      employmentType = Some(2),
      employmentId = Some(4),
      employmentTaxDistrictNumber = Some(etdn),
      employmentPayeRef = Some(epr)
  )

  val employment1 = new NpsEmployment(
    sequenceNumber = 1,
    startDate = NpsDate(new LocalDate(2006, 12, 31)),
    endDate = Some(NpsDate(new LocalDate(2014, 12, 31))),
    taxDistrictNumber = TaiConstants.ESA_TAX_DISTRICT.toString,
    payeNumber = TaiConstants.ESA_PAYE_NUMBER,
    employerName = Some("AAA"),
    employmentType = 1,
    worksNumber = Some("00-000"),
    jobTitle = Some("jobTitle1"),
    startingTaxCode = Some("startingTaxCode1")
  )
  val employment2 = new NpsEmployment(
    sequenceNumber = 2,
    startDate = NpsDate(new LocalDate(2006, 12, 12)),
    endDate = None,
    taxDistrictNumber = "1",
    payeNumber = "payeno",
    employerName = Some("TTT"),
    employmentType = 2,
    worksNumber = Some("00-000"),
    jobTitle = Some("jobTitle2"),
    startingTaxCode = Some("startingTaxCode2")
  )
  val employmentESA = new NpsEmployment(
    sequenceNumber = 2,
    startDate = NpsDate(new LocalDate(2006, 12, 12)),
    endDate = None,
    taxDistrictNumber = TaiConstants.ESA_TAX_DISTRICT.toString,
    payeNumber = TaiConstants.ESA_PAYE_NUMBER,
    employerName = Some("TTT"),
    employmentType = 2,
    worksNumber = Some("00-000"),
    jobTitle = Some("jobTitle2"),
    startingTaxCode = Some("startingTaxCode2")
  )

  def sut = IncomeHelper
}
