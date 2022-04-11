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

package uk.gov.hmrc.tai.model.nps

import data.NpsData
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.{DeductionType, IabdType, TaxAccount, TaxDetail, TaxObject}
import uk.gov.hmrc.tai.model.rti.PayFrequency
import uk.gov.hmrc.tai.model.tai.{AnnualAccount, TaxYear}
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.util.TaiConstants

import java.time.{LocalDate, LocalDateTime, ZoneId}
import scala.util.Random

class NpsTaxAccountSpec extends PlaySpec {

  "NpsTaxAccount" must {
    "return the TaxSummaryDetails " when {
      "given a version, and empty list of nps employments, iabds, rti calc and accounts" in {

        val taxSummaryDetails = createSUT().toTaxSummary(1, Nil, Nil, Nil, Nil)

        taxSummaryDetails.nino mustBe nino.nino
        taxSummaryDetails.version mustBe 1
        taxSummaryDetails.accounts mustBe Nil
        taxSummaryDetails.increasesTax mustBe None
        taxSummaryDetails.decreasesTax mustBe None
        taxSummaryDetails.totalLiability mustBe None
        taxSummaryDetails.adjustedNetIncome mustBe BigDecimal(0)
        taxSummaryDetails.extensionReliefs must not be None
        taxSummaryDetails.gateKeeper mustBe None
        taxSummaryDetails.taxCodeDetails mustBe None
        taxSummaryDetails.incomeData must not be None
        taxSummaryDetails.cyPlusOneChange mustBe None
        taxSummaryDetails.cyPlusOneSummary mustBe None
      }

      "given a version, nps employments, iabds, rti calc and accounts" in {

        val accounts = List(
          AnnualAccount(
            TaxYear(2016),
            Some(TaxAccount(
              None,
              None,
              1564.45,
              Map(TaxObject.Type.NonSavings -> TaxDetail(
                Some(1111.11),
                Some(9969),
                None,
                Seq(
                  nps2.TaxBand(Some("pa"), None, 2290, 0, None, None, 0),
                  nps2.TaxBand(Some("B"), None, 9969, 1993.80, Some(0), Some(33125), 20.00))
              ))
            ))
          ))

        val npsEmployments =
          List(NpsEmployment(1, NpsDate(LocalDate.of(2017, 4, 24)), None, "1234", "1234", Some("PAYEEMPLOYER"), 2))

        val iabds = List(NpsIabdRoot(nino = nino.nino, None, `type` = IabdType.ChildBenefit.code, grossAmount = None))

        val rtiCalc = List(
          RtiCalc(
            employmentType = 2,
            employmentId = 2,
            employmentStatus = 1,
            employerName = "PAYEEMPLOYER",
            paymentDate = Some(LocalDate.of(2016, 7, 3)),
            totalPayToDate = BigDecimal(8000),
            payFrequency = Some(PayFrequency.Annually),
            calculationResult = Some(BigDecimal(10000))
          ))

        val taxSummaryDetails = createSUT().toTaxSummary(1, npsEmployments, iabds, rtiCalc, accounts)

        taxSummaryDetails.nino mustBe nino.nino
        taxSummaryDetails.version mustBe 1
        taxSummaryDetails.accounts mustBe Nil
        taxSummaryDetails.increasesTax mustBe None
        taxSummaryDetails.decreasesTax mustBe None
        taxSummaryDetails.totalLiability mustBe None
        taxSummaryDetails.adjustedNetIncome mustBe BigDecimal(0)
        taxSummaryDetails.extensionReliefs must not be None
        taxSummaryDetails.gateKeeper mustBe None
        taxSummaryDetails.taxCodeDetails mustBe None
        taxSummaryDetails.incomeData must not be None
        taxSummaryDetails.cyPlusOneChange mustBe None
        taxSummaryDetails.cyPlusOneSummary mustBe None
      }
    }
  }

  "toLocalDate" must {

    "return None" when {
      "None is supplied" in {

        val sut = createSUT()

        val result = sut.toLocalDate(None)

        result mustBe None

      }
    }

    "return a local date" when {
      "an NpsDate is supplied" in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2000-01-02")

        val result = sut.toLocalDate(Some(NpsDate(localDate)))

        result mustBe Some(localDate)
      }
    }
  }

  "getEmploymentCeasedDetail" must {

    "return an empty CeasedEmploymentDetails object" when {

      "a Nil list is supplied" in {

        val sut = createSUT()

        val result = sut.getEmploymentCeasedDetail(Nil)

        result mustBe CeasedEmploymentDetails(None, None, None, None)
      }

      "receiving occupational pension is set to None and employment status is set to None" in {

        val sut = createSUT()

        val dateCYMinus2 = TaxYear().start

        val npsEmployment = NpsEmployment(
          0,
          NpsDate(dateCYMinus2),
          Some(NpsDate(dateCYMinus2)),
          "",
          "",
          None,
          0,
          receivingOccupationalPension = None,
          employmentStatus = None)

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(None, None, None, None)
      }
    }

    "return a CeasedEmploymentDetails object with pension set to true and employment status set to None" when {

      "an employment is supplied set as receiving occupational pension and status is not set" in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2010-01-02")
        val npsEmployment =
          NpsEmployment(0, NpsDate(localDate), None, "", "", None, 0, receivingOccupationalPension = Some(true))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(None, Some(true), None, None)
      }
    }

    "return a CeasedEmploymentDetails object with pension set to false and employment status set to None" when {

      "an employment is supplied set as receiving occupational pension and status is not set" in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2010-01-02")
        val npsEmployment =
          NpsEmployment(0, NpsDate(localDate), None, "", "", None, 0, receivingOccupationalPension = Some(false))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(None, Some(false), None, None)
      }
    }

    "return a CeasedEmploymentDetails object with pension set to true and employment status set to 123" when {

      "an employment is supplied set as receiving occupational pension and status is set to 123" in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2010-01-02")
        val npsEmployment = NpsEmployment(
          0,
          NpsDate(localDate),
          None,
          "",
          "",
          None,
          0,
          employmentStatus = Some(123),
          receivingOccupationalPension = Some(true))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(None, Some(true), None, Some(123))
      }
    }

    "return a CeasedEmploymentDetails object with ceased status set to CY-2" when {

      "an employment is supplied with a date set at CY-2" in {

        val sut = createSUT()

        val dateCYMinus2 = TaxYear().start.minusYears(2)

        val npsEmployment = NpsEmployment(
          0,
          NpsDate(dateCYMinus2),
          Some(NpsDate(dateCYMinus2)),
          "",
          "",
          None,
          0,
          receivingOccupationalPension = Some(true),
          employmentStatus = Some(123))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(
          Some(dateCYMinus2),
          Some(true),
          Some(TaiConstants.CEASED_MINUS_TWO),
          Some(123))

      }
    }

    "return a CeasedEmploymentDetails object with ceased status set to CY-1" when {

      "an employment is supplied with a date set at CY-1" in {

        val sut = createSUT()

        val dateCYMinus2 = TaxYear().start.minusYears(1)

        val npsEmployment = NpsEmployment(
          0,
          NpsDate(dateCYMinus2),
          Some(NpsDate(dateCYMinus2)),
          "",
          "",
          None,
          0,
          receivingOccupationalPension = Some(true),
          employmentStatus = Some(123))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(
          Some(dateCYMinus2),
          Some(true),
          Some(TaiConstants.CEASED_MINUS_ONE),
          Some(123))

      }
    }

    "return a CeasedEmploymentDetails object with ceased status set to None" when {

      "an employment is supplied with a date set at CY" in {

        val sut = createSUT()

        val dateCYMinus2 = TaxYear().start

        val npsEmployment = NpsEmployment(
          0,
          NpsDate(dateCYMinus2),
          Some(NpsDate(dateCYMinus2)),
          "",
          "",
          None,
          0,
          receivingOccupationalPension = Some(true),
          employmentStatus = Some(123))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(Some(dateCYMinus2), Some(true), None, Some(123))
      }
    }

    "return a CeasedEmploymentDetails object with ceased status set to CY-3" when {

      "an employment is supplied with a date set at CY-3" in {

        val sut = createSUT()

        val dateCYMinus3 = TaxYear().start.minusYears(3)

        val npsEmployment = NpsEmployment(
          0,
          NpsDate(dateCYMinus3),
          Some(NpsDate(dateCYMinus3)),
          "",
          "",
          None,
          0,
          receivingOccupationalPension = Some(true),
          employmentStatus = Some(123))

        val result = sut.getEmploymentCeasedDetail(List(npsEmployment))

        result mustBe CeasedEmploymentDetails(
          Some(dateCYMinus3),
          Some(true),
          Some(TaiConstants.CEASED_MINUS_THREE),
          Some(123))

      }
    }
  }

  "findCeasedEmploymentDetails" must {

    "return the first CeasedEmploymentDetails" when {

      "both end dates are None provided" in {

        val sut = createSUT()

        val ceasedEmploymentDetails1 = CeasedEmploymentDetails(None, None, Some("ceased1"), None)
        val ceasedEmploymentDetails2 = CeasedEmploymentDetails(None, None, Some("ceased2"), None)

        val result = sut.findCeasedEmploymentDetails(ceasedEmploymentDetails1, ceasedEmploymentDetails2)

        result mustBe ceasedEmploymentDetails1
      }

      "ceased employment 1 doesn't provide an end date but ceased employement 2 does provide an end date." in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2010-01-02")

        val ceasedEmploymentDetails1 = CeasedEmploymentDetails(None, None, Some("ceased1"), None)
        val ceasedEmploymentDetails2 = CeasedEmploymentDetails(Some(localDate), None, Some("ceased2"), None)

        val result = sut.findCeasedEmploymentDetails(ceasedEmploymentDetails1, ceasedEmploymentDetails2)

        result mustBe ceasedEmploymentDetails1
      }

      "ceased employment 1 has a later end date than ceased employement 2" in {

        val sut = createSUT()

        val localDateEarlier = LocalDate.parse("2010-01-02")
        val localDateLater = LocalDate.parse("2010-02-02")

        val ceasedEmploymentDetails1 = CeasedEmploymentDetails(Some(localDateLater), None, Some("ceased1"), None)
        val ceasedEmploymentDetails2 = CeasedEmploymentDetails(Some(localDateEarlier), None, Some("ceased2"), None)

        val result = sut.findCeasedEmploymentDetails(ceasedEmploymentDetails1, ceasedEmploymentDetails2)

        result mustBe ceasedEmploymentDetails1
      }
    }

    "return the second CeasedEmploymentDetails" when {

      "ceased employment 2 doesn't provide an end date but ceased employement 1 does provide an end date." in {

        val sut = createSUT()

        val localDate = LocalDate.parse("2010-01-02")

        val ceasedEmploymentDetails1 = CeasedEmploymentDetails(Some(localDate), None, Some("ceased1"), None)
        val ceasedEmploymentDetails2 = CeasedEmploymentDetails(None, None, Some("ceased2"), None)

        val result = sut.findCeasedEmploymentDetails(ceasedEmploymentDetails1, ceasedEmploymentDetails2)

        result mustBe ceasedEmploymentDetails2
      }

      "ceased employment 2 has a later end date than ceased employement 1" in {

        val sut = createSUT()

        val localDateEarlier = LocalDate.parse("2010-01-02")
        val localDateLater = LocalDate.parse("2010-02-02")

        val ceasedEmploymentDetails1 = CeasedEmploymentDetails(Some(localDateEarlier), None, Some("ceased1"), None)
        val ceasedEmploymentDetails2 = CeasedEmploymentDetails(Some(localDateLater), None, Some("ceased2"), None)

        val result = sut.findCeasedEmploymentDetails(ceasedEmploymentDetails1, ceasedEmploymentDetails2)

        result mustBe ceasedEmploymentDetails2
      }
    }
  }

  "getTaxableStateBenefit" must {

    "not return any taxable state benefits" when {

      "there are no taxable state benefits supplied either through income sources or nps components" in {

        val sut = createSUT()

        val result = sut.getTaxableStateBenefit(Nil)

        result mustBe None
      }

      "income sources contain the taxable state benefit (Employment Support Allowance)" in {

        val sut = createSUT()

        val esaBenefit = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.ESA_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.ESA_PAYE_NUMBER)
        )

        val incomeSources = List(MergedEmployment(esaBenefit))

        val result = sut.getTaxableStateBenefit(incomeSources)

        result mustBe None
      }

      "income sources contain the taxable state benefit (Incapacity Benefit)" in {

        val sut = createSUT()

        val incapacityBenefit = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.INCAPACITY_BENEFIT_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.INCAPACITY_BENEFIT_PAYE_NUMBER)
        )

        val incomeSources = List(MergedEmployment(incapacityBenefit))

        val result = sut.getTaxableStateBenefit(incomeSources)

        result mustBe None
      }

      "income sources contain the taxable state benefit (Jobseekers Allowance)" in {

        val sut = createSUT()

        val jsaBenefit = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.JSA_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.JSA_PAYE_NUMBER)
        )

        val incomeSources = List(MergedEmployment(jsaBenefit))

        val result = sut.getTaxableStateBenefit(incomeSources)

        result mustBe None
      }

      "income sources and an nps component both contain the taxable state benefit (Employment Support Allowance) " in {

        val esaBenefit = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.ESA_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.ESA_PAYE_NUMBER)
        )

        val incomeSourcesWithEsaBenefit = List(MergedEmployment(esaBenefit))
        val iabdSummaries = List(NpsIabdSummary(`type` = Some(EmploymentAndSupportAllowance.code)))
        val npsComponentWithEsaIabd = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithEsaIabd))

        val result = sut.getTaxableStateBenefit(incomeSourcesWithEsaBenefit)

        result mustBe None
      }
    }

    "return taxable state benefits" when {

      "no income sources are supplied but EmploymentAndSupportAllowance (taxable benefit) is supplied as " +
        "an nps component " in {

        val iabdSummaries = List(NpsIabdSummary(`type` = Some(EmploymentAndSupportAllowance.code)))
        val npsComponentWithESA = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithESA))

        val result = sut.getTaxableStateBenefit(Nil)

        result mustBe Some(NpsComponent(amount = Some(0), iabdSummaries = Some(iabdSummaries)))
      }

      "no income sources are supplied but JobSeekersAllowance (taxable benefit) is supplied as an nps component" in {

        val iabdSummaries = List(NpsIabdSummary(`type` = Some(JobSeekersAllowance.code)))
        val npsComponentWithJSA = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithJSA))

        val result = sut.getTaxableStateBenefit(Nil)

        result mustBe Some(NpsComponent(amount = Some(0), iabdSummaries = Some(iabdSummaries)))
      }

      "no income sources are supplied but Incapacity Benefit (taxable benefit) is supplied as an nps component " in {

        val iabdSummaries = List(NpsIabdSummary(`type` = Some(IncapacityBenefit.code)))
        val npsComponentWithIB = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithIB))

        val result = sut.getTaxableStateBenefit(Nil)

        result mustBe Some(NpsComponent(amount = Some(0), iabdSummaries = Some(iabdSummaries)))
      }

      "non taxable benefits are filtered out" in {

        val iabdSummaries = List(
          NpsIabdSummary(`type` = Some(IncapacityBenefit.code)),
          NpsIabdSummary(`type` = Some(GiftAidPayments.code))
        )

        val npsComponentWithIB = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithIB))

        val result = sut.getTaxableStateBenefit(Nil)

        result flatMap {
          _.iabdSummaries
        } mustBe Some(List(NpsIabdSummary(`type` = Some(IncapacityBenefit.code))))
      }
    }
  }

  "getIncomeData" must {

    val employmentName = "test"
    val employmentId: Int = 1234
    val employmentType: Int = 111
    val npsSequenceNumber, employmentSequenceNumber: Int = employmentId
    val cessationPay: Int = 123
    val testDate = LocalDate.parse("2017-01-01")

    val employments = TaxCodeIncomeTotal(
      totalIncome = 123.0,
      totalTax = 12.0,
      totalTaxableIncome = 120,
      taxCodeIncomes = List(
        TaxCodeIncomeSummary(
          name = employmentName,
          taxCode = "",
          employmentId = Some(employmentId),
          employmentType = Some(employmentType),
          tax = Tax()))
    )

    val npsEmployments = List(
      NpsEmployment(
        sequenceNumber = npsSequenceNumber,
        startDate = NpsDate(LocalDate.parse("2017-01-01")),
        endDate = None,
        taxDistrictNumber = "",
        payeNumber = "",
        employerName = Some(""),
        employmentType = employmentType
      ))

    "return IncomeData with an empty list of IncomeExplanation" when {

      "None, Nil and false are provided" in {

        val sut = createSUT()

        val result = sut.getIncomeData(None, None, None, None, Nil, Nil, Nil, hasDuplicateEmploymentNames = false)

        result mustBe IncomeData(List())
      }

      "return IncomeData with one IncomeExplanation item containing employment data supplied as employments" in {

        val sut = createSUT()

        val result =
          sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, Nil, hasDuplicateEmploymentNames = false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.employerName mustBe employmentName
        incomeExplanation.incomeId mustBe employmentId
        incomeExplanation.employmentType mustBe None
      }

      "return IncomeData with one IncomeExplanation item containing data from a supplied TaxCodeIncomeSummary and " +
        "NpsEmployment with matching ids" in {

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType,
            cessationPayThisEmployment = Some(cessationPay)
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(
          Some(employments),
          None,
          None,
          None,
          Nil,
          npsEmployments,
          Nil,
          hasDuplicateEmploymentNames = false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.employerName mustBe employmentName
        incomeExplanation.incomeId mustBe employmentId
        incomeExplanation.employmentType mustBe Some(employmentType)
        incomeExplanation.cessationPay mustBe Some(123)

        incomeExplanation.iabdSource mustBe None
      }

      "return IncomeData with one IncomeExplanation item containing data from a supplied TaxCodeIncomeSummary but not" +
        "NpsEmployment as ids don't match" in {

        val npsSequenceNumber: Int = 1235

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(
          Some(employments),
          None,
          None,
          None,
          Nil,
          npsEmployments,
          Nil,
          hasDuplicateEmploymentNames = false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.employerName mustBe employmentName
        incomeExplanation.incomeId mustBe employmentId
        incomeExplanation.employmentType mustBe None
        incomeExplanation.cessationPay mustBe None
      }

      "return incomeExplanation - editableDetails - payRollingBiks as false when payrolledTaxYear is None" in {

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe false
      }

      "return incomeExplanation - editableDetails - payRollingBiks as false when payrolledTaxYear is false" in {

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType,
            payrolledTaxYear = Some(false)
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe false
      }

      "return incomeExplanation - editableDetails - payRollingBiks as true when payrolledTaxYear is true" in {

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType,
            payrolledTaxYear = Some(true)
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe true
      }

      "return incomeExplanation - editableDetails - payRollingBiks as false when payrolledTaxYear1 is None" in {

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe false
      }

      "return incomeExplanation - editableDetails - payRollingBiks as false when payrolledTaxYear1 is false" in {

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType,
            payrolledTaxYear1 = Some(false)
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe false
      }

      "return incomeExplanation - editableDetails - payRollingBiks as true when payrolledTaxYear1 is true" in {

        val npsEmployments = List(
          NpsEmployment(
            sequenceNumber = npsSequenceNumber,
            startDate = NpsDate(LocalDate.parse("2017-01-01")),
            endDate = None,
            taxDistrictNumber = "",
            payeNumber = "",
            employerName = Some(""),
            employmentType = employmentType,
            payrolledTaxYear1 = Some(true)
          ))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, npsEmployments, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.editableDetails.payRollingBiks mustBe true
      }

      "contain a notification date when that date is supplied through a NewEstimatedPay iabd which matches " +
        "a supplied employment" in {

        val iabds = List(
          NpsIabdRoot(
            nino = "",
            `type` = NewEstimatedPay.code,
            employmentSequenceNumber = Some(employmentSequenceNumber),
            receiptDate = Some(NpsDate(testDate))))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, iabds, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.notificationDate mustBe Some(testDate)
        incomeExplanation.updateActionDate mustBe None
      }

      "contains pay data when a NewEstimatedPay iabd is supplied and employment id matches" in {

        val iabds = List(
          NpsIabdRoot(
            nino = "",
            `type` = NewEstimatedPay.code,
            employmentSequenceNumber = Some(employmentSequenceNumber),
            captureDate = Some(NpsDate(testDate))))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, iabds, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.notificationDate mustBe None
        incomeExplanation.updateActionDate mustBe Some(testDate)
      }

      "does not contain pay data when a NewEstimatedPay iabd is supplied but employment id does not match" in {

        val employmentSequenceNumber: Int = 1235

        val iabds = List(
          NpsIabdRoot(
            nino = "",
            `type` = NewEstimatedPay.code,
            employmentSequenceNumber = Some(employmentSequenceNumber),
            captureDate = Some(NpsDate(testDate))))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, iabds, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.notificationDate mustBe None
        incomeExplanation.updateActionDate mustBe None
      }

      "include Rti data in an income explanation when an rti calc is supplied and employment id matches" in {

        val payToDate: BigDecimal = 123.45
        val payFrequency = PayFrequency.Weekly

        val rti = List(
          RtiCalc(
            employmentType,
            Some(testDate),
            Some(payFrequency),
            employmentId,
            employmentStatus = 0,
            "",
            totalPayToDate = payToDate,
            calculationResult = None))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, rti, Nil, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.payToDate mustBe payToDate
        incomeExplanation.payFrequency mustBe Some(payFrequency)
        incomeExplanation.paymentDate mustBe Some(testDate)

        incomeExplanation.updateActionDate mustBe None
      }

      "not include Rti data in an income explanation when an rti calc is supplied but employmentId does not match" in {

        val payToDate: BigDecimal = 123.45
        val payFrequency = PayFrequency.Weekly

        val rti = List(
          RtiCalc(
            employmentType,
            Some(testDate),
            Some(payFrequency),
            employmentId,
            employmentStatus = 0,
            "",
            totalPayToDate = payToDate,
            calculationResult = None))

        val sut = createSUT()

        val result = sut.getIncomeData(Some(employments), None, None, None, rti, Nil, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.payToDate mustBe payToDate
        incomeExplanation.payFrequency mustBe Some(payFrequency)
        incomeExplanation.paymentDate mustBe Some(testDate)

        incomeExplanation.updateActionDate mustBe None
      }

      "include gross amount and calc amount in an income explanation when an iabd of NewEstimatedPay is supplied " +
        "and employment id matches" in {

        val amount: BigDecimal = 567.89
        val estimatedPaySource: Int = 321

        val iabdSummaries = List(
          NpsIabdSummary(
            amount = Some(amount),
            `type` = Some(NewEstimatedPay.code),
            employmentId = Some(employmentId),
            estimatedPaySource = Some(estimatedPaySource)))

        val npsComponentWithNewEstimatedPay = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithNewEstimatedPay))

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.grossAmount mustBe Some(amount)
        incomeExplanation.calcAmount mustBe Some(amount)
        incomeExplanation.iabdSource mustBe Some(estimatedPaySource)
      }

      "does not include gross amount and calc amount in an income explanation when an iabd of NewEstimatedPay is supplied " +
        "but employment id does not match" in {

        val iabdEmploymentId: Int = 1235
        val amount: BigDecimal = 567.89
        val estimatedPaySource: Int = 321

        val iabdSummaries = List(
          NpsIabdSummary(
            amount = Some(amount),
            `type` = Some(NewEstimatedPay.code),
            employmentId = Some(iabdEmploymentId),
            estimatedPaySource = Some(estimatedPaySource)))

        val npsComponentWithNewEstimatedPay = NpsComponent(iabdSummaries = Some(iabdSummaries))

        val sut = createSUT(adjustedNetIncome = Some(npsComponentWithNewEstimatedPay))

        val result = sut.getIncomeData(Some(employments), None, None, None, Nil, Nil, Nil, false)

        result.incomeExplanations.length mustBe 1

        val incomeExplanation = result.incomeExplanations.head

        incomeExplanation.grossAmount mustBe None
        incomeExplanation.calcAmount mustBe None
      }
    }
  }

  "getTaxCodeDescriptors" must {
    "output a list of tax code descriptors" when {
      "given a tax code with a valid prefix" in {
        val sut = createSUT()

        val taxCode = "K100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("K")
      }

      "given a tax code with a valid suffix" in {
        val sut = createSUT()

        val taxCode = "1000T"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("T")
      }

      "given a tax code with a valid prefix and suffix" in {
        val sut = createSUT()

        val taxCode = "SK1000T"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("S", "K", "T")
      }

      "an \"S\" prefix appears anywhere in the tax code " in {
        val sut = createSUT()

        val taxCode = "1S000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("S")
      }

      "a \"K\" prefix appears anywhere in the tax code " in {
        val sut = createSUT()

        val taxCode = "1K000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("K")
      }

      "a \"D0\" prefix appears anywhere in the tax code " in {
        val sut = createSUT()

        val taxCode = "1D000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("D0")
      }

      "a \"D1\" prefix appears anywhere in the tax code " in {
        val sut = createSUT()

        val taxCode = "1D1000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("D1")
      }

      "a \"0T\" suffix appears at the beginning of the tax code " in {
        val sut = createSUT()

        val taxCode = "0T1000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("0T")
      }

      "an \"NT\" suffix appears at the beginning of the tax code " in {
        val sut = createSUT()

        val taxCode = "NT1000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("NT")
      }

      "a \"BR\" suffix appears at the beginning of the tax code " in {
        val sut = createSUT()

        val taxCode = "BR1000"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("BR")
      }

      "an \"L\" suffix appears at the end of the tax code " in {
        val sut = createSUT()

        val taxCode = "1000L"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("L")
      }

      "a \"Y\" suffix appears at the end of the tax code " in {
        val sut = createSUT()

        val taxCode = "1000Y"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("Y")
      }

      "an \"M\" suffix appears at the end of the tax code " in {
        val sut = createSUT()

        val taxCode = "1000M"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("M")
      }

      "an \"N\" suffix appears at the end of the tax code " in {
        val sut = createSUT()

        val taxCode = "1000N"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("N")
      }

      "a \"T\" suffix appears at the end of the tax code " in {
        val sut = createSUT()

        val taxCode = "1000T"

        sut.getTaxCodeDescriptors(taxCode) mustBe List("T")
      }
    }

    "output an empty list" when {
      "a \"0T\" suffix is not at the beginning of the tax code" in {
        val sut = createSUT()

        val taxCode = "110T00"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "an \"NT\" suffix is not at the beginning of the tax code" in {
        val sut = createSUT()

        val taxCode = "11NT00"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "an \"BR\" suffix is not at the beginning of the tax code" in {
        val sut = createSUT()

        val taxCode = "11BR00"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "an \"L\" suffix is not at the end of the tax code" in {
        val sut = createSUT()

        val taxCode = "L1100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "a \"Y\" suffix is not at the end of the tax code" in {
        val sut = createSUT()

        val taxCode = "Y1100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "an \"M\" suffix is not at the end of the tax code" in {
        val sut = createSUT()

        val taxCode = "M1100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "an \"N\" suffix is not at the end of the tax code" in {
        val sut = createSUT()

        val taxCode = "N1100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }

      "a \"T\" suffix is not at the end of the tax code" in {
        val sut = createSUT()

        val taxCode = "T1100"

        sut.getTaxCodeDescriptors(taxCode) mustBe List()
      }
    }

    "return personal allowance component" when {
      "getPersonalAllowanceComponent is called" in {
        val personalAllowance = NpsComponent(
          amount = Some(BigDecimal(666.33)),
          `type` = Some(12),
          npsDescription = Some("npsDescription"),
          sourceAmount = Some(BigDecimal(777.33)))

        val allowances = Some(List(personalAllowance))

        val npsMainIncomeSource = new NpsIncomeSource(None, None, Some(1), allowances)

        val npsTaxAccount = createSUT().copy(incomeSources = Some(List(npsMainIncomeSource)))

        val getPersonalAllowanceComponent = npsTaxAccount.getPersonalAllowanceComponent

        getPersonalAllowanceComponent mustBe Some(
          NpsComponent(Some(666.33), Some(12), None, Some("npsDescription"), Some(777.33)))
      }
    }

    "return under payment from previous year component" when {
      "getUnderpaymentPreviousYearComponent is called" in {
        val underPaymentCompenent = NpsComponent(
          amount = Some(BigDecimal(666.33)),
          `type` = Some(35),
          npsDescription = Some("npsDescription"),
          sourceAmount = Some(BigDecimal(777.33)))

        val deductions = Some(List(underPaymentCompenent))

        val npsMainIncomeSource = new NpsIncomeSource(None, None, Some(1), None, deductions)

        val npsTaxAccount = createSUT().copy(incomeSources = Some(List(npsMainIncomeSource)))

        val getUnderpaymentPreviousYearComponent = npsTaxAccount.getUnderpaymentPreviousYearComponent

        getUnderpaymentPreviousYearComponent mustBe Some(
          NpsComponent(Some(666.33), Some(35), None, Some("npsDescription"), Some(777.33)))
      }
    }

    "return personal allowance transferred" when {
      "getPersonalAllowanceTransferred is called" in {
        val personalAllowanceTransferred = NpsComponent(
          amount = Some(BigDecimal(666.33)),
          `type` = Some(43),
          npsDescription = Some("npsDescription"),
          sourceAmount = Some(BigDecimal(777.33)))

        val deductions = Some(List(personalAllowanceTransferred))

        val npsMainIncomeSource = new NpsIncomeSource(None, None, Some(1), None, deductions)

        val npsTaxAccount = createSUT().copy(incomeSources = Some(List(npsMainIncomeSource)))

        val getPersonalAllowanceTransferred = npsTaxAccount.getPersonalAllowanceTransferred
        getPersonalAllowanceTransferred mustBe Some(
          NpsComponent(Some(666.33), Some(43), None, Some("npsDescription"), Some(777.33)))
      }
    }

    "return personal allowance received" when {
      "getPersonalAllowanceReceived is called" in {
        val personalAllowanceReceived = NpsComponent(
          amount = Some(BigDecimal(666.33)),
          `type` = Some(32),
          npsDescription = Some("npsDescription"),
          sourceAmount = Some(BigDecimal(777.33)))

        val allowances = Some(List(personalAllowanceReceived))

        val npsMainIncomeSource = new NpsIncomeSource(None, None, Some(1), allowances)

        val npsTaxAccount = createSUT().copy(incomeSources = Some(List(npsMainIncomeSource)))

        val getPersonalAllowanceReceived = npsTaxAccount.getPersonalAllowanceReceived
        getPersonalAllowanceReceived mustBe Some(
          NpsComponent(Some(666.33), Some(32), None, Some("npsDescription"), Some(777.33)))
      }
    }

    "return outstanding debt" when {
      "getOutstandingDebt is called" in {
        val outStandingDebt = NpsComponent(
          amount = Some(BigDecimal(666.33)),
          `type` = Some(41),
          npsDescription = Some("npsDescription"),
          sourceAmount = Some(BigDecimal(777.33)))

        val deductions = Some(List(outStandingDebt))

        val npsMainIncomeSource = new NpsIncomeSource(None, None, Some(1), None, deductions)

        val npsTaxAccount = createSUT().copy(incomeSources = Some(List(npsMainIncomeSource)))

        val getOutstandingDebt = npsTaxAccount.getOutstandingDebt
        getOutstandingDebt mustBe Some(NpsComponent(Some(666.33), Some(41), None, Some("npsDescription"), Some(777.33)))
      }
    }
  }

  "getFullTaxCodeDescription" must {
    "return an empty list" when {
      "given an empty list" in {
        val sut = createSUT()

        sut.getFullTaxCodeDescription(Nil) mustBe List()
      }
    }
    "return a list of empty Tax Code Descriptions" when {
      "given a list of empty income sources" in {
        val sut = createSUT()

        val incomeSource = NpsIncomeSource()

        sut.getFullTaxCodeDescription(List(incomeSource)) mustBe List(TaxCodeDescription("", "", List()))
      }
    }

    "return a list of Tax Code Descriptions with no rate" when {
      "given a list of income sources" in {
        val sut = createSUT()

        val incomeSource = NpsIncomeSource(Some("Income"), Some("1000L"))

        sut.getFullTaxCodeDescription(List(incomeSource)) mustBe List(
          TaxCodeDescription("1000L", "Income", List(TaxCode(Some("L"), None))))
      }
    }

    "return a list of Tax Code Descriptions with a rate of 20" when {
      "given a list of income sources that includes a BR" in {
        val npsTax = NpsTax(taxBands = Some(List(TaxBand(rate = Some(20)))))
        val incomeSource = NpsIncomeSource(name = Some("Income"), taxCode = Some("BR"), payAndTax = Some(npsTax))

        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getFullTaxCodeDescription(List(incomeSource)) mustBe List(
          TaxCodeDescription("BR", "Income", List(TaxCode(Some("BR"), Some(20)))))
      }
    }

    "return a list of Tax Code Descriptions with a rate of 40" when {
      "given a list of income sources that includes a BR" in {
        val npsTax = NpsTax(taxBands = Some(List(TaxBand(rate = Some(20)), TaxBand(rate = Some(40)))))
        val incomeSourceOne = NpsIncomeSource(name = Some("IncomeOne"), taxCode = Some("BR"), payAndTax = Some(npsTax))
        val incomeSourceTwo = NpsIncomeSource(name = Some("IncomeTwo"), taxCode = Some("D0"), payAndTax = Some(npsTax))

        val sut = createSUT(incomeSources = Some(List(incomeSourceOne, incomeSourceTwo)))

        sut.getFullTaxCodeDescription(List(incomeSourceOne, incomeSourceTwo)) mustBe List(
          TaxCodeDescription("BR", "IncomeOne", List(TaxCode(Some("BR"), Some(20)))),
          TaxCodeDescription("D0", "IncomeTwo", List(TaxCode(Some("D0"), Some(40))))
        )
      }
    }
  }

  "getStatePension" must {
    "return the state pension amount" when {
      "there is a deduction of state pension" in {
        val npsComponentAmount = 41
        val statePension =
          NpsComponent(Some(npsComponentAmount), Some(DeductionType.StatePensionOrBenefits.id), None, None, None)
        val incomeSource =
          NpsIncomeSource(employmentType = Some(TaiConstants.PrimaryEmployment), deductions = Some(List(statePension)))

        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePension mustBe Some(npsComponentAmount)
      }

      "there is no state pension deduction but there is an adjusted net income of state pension" in {
        val npsIabdSummaryAmount = 82
        val iabdSummary = NpsIabdSummary(amount = Some(npsIabdSummaryAmount), `type` = Some(StatePension.code))
        val adjustedNetIncome = NpsComponent(iabdSummaries = Some(List(iabdSummary)))

        val sut = createSUT(adjustedNetIncome = Some(adjustedNetIncome))

        sut.getStatePension mustBe Some(npsIabdSummaryAmount)
      }
    }

    "not return anything" when {
      "there are benefits but none are state pensions" in {
        val publicServicePension =
          NpsComponent(Some(42), Some(DeductionType.PublicServicesPension.id), None, None, None)
        val incomeSource = NpsIncomeSource(
          employmentType = Some(TaiConstants.PrimaryEmployment),
          deductions = Some(List(publicServicePension)))

        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePension mustBe None
      }

      "there is no state pension deduction and there is an adjusted net income of something other than state pension" in {
        val iabdSummary = NpsIabdSummary(amount = Some(82), `type` = Some(OccupationalPension.code))
        val adjustedNetIncome = NpsComponent(iabdSummaries = Some(List(iabdSummary)))

        val sut = createSUT(adjustedNetIncome = Some(adjustedNetIncome))

        sut.getStatePension mustBe None
      }

      "there is an adjusted net income of state pension but no amount" in {
        val iabdSummary = NpsIabdSummary(`type` = Some(StatePension.code))
        val adjustedNetIncome = NpsComponent(iabdSummaries = Some(List(iabdSummary)))

        val sut = createSUT(adjustedNetIncome = Some(adjustedNetIncome))

        sut.getStatePension mustBe None
      }

      "there is an empty list of IABD summaries" in {
        val adjustedNetIncome = NpsComponent(iabdSummaries = Some(Nil))

        val sut = createSUT(adjustedNetIncome = Some(adjustedNetIncome))

        sut.getStatePension mustBe None
      }

      "there is an empty nps component" in {
        val adjustedNetIncome = NpsComponent()

        val sut = createSUT(adjustedNetIncome = Some(adjustedNetIncome))

        sut.getStatePension mustBe None
      }
    }
  }

  "getStatePensionLumpSum" must {
    "return the amount of the state pension lump sum" when {
      "there is a matching tax district number and a matching employment paye ref" in {
        val npsComponentAmount = 22
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER),
          payAndTax = Some(NpsTax(totalIncome = Some(NpsComponent(amount = Some(npsComponentAmount)))))
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe Some(npsComponentAmount)
      }
    }

    "return no amount" when {
      "there is a matching tax district number and employment paye ref but no amount" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER),
          payAndTax = Some(NpsTax(totalIncome = Some(NpsComponent(amount = None))))
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is a matching tax district number and employment paye ref but no total income component" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER),
          payAndTax = Some(NpsTax(totalIncome = None))
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is a matching tax district number and employment paye ref but no nps pay and tax" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER),
          payAndTax = None
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is a matching tax district number and employment paye ref but it does not match the pension lump sum paye number" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT),
          employmentPayeRef = Some("This is not a pension lump sum paye number")
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is a matching tax district number but there is not employment paye ref" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(TaiConstants.PENSION_LUMP_SUM_TAX_DISTRICT)
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is an employment paye ref and a employment tax district number but it does not match the pension lump sum tax district" in {
        val incomeSource = NpsIncomeSource(
          employmentTaxDistrictNumber = Some(-1),
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER)
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is an employment paye ref but there is not employment tax district number" in {
        val incomeSource = NpsIncomeSource(
          employmentPayeRef = Some(TaiConstants.PENSION_LUMP_SUM_PAYE_NUMBER)
        )
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is an nps income source but it is empty" in {
        val incomeSource = NpsIncomeSource()
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.getStatePensionLumpSum mustBe None
      }

      "there is a list of income sources but it is empty" in {
        val sut = createSUT(incomeSources = Some(Nil))

        sut.getStatePensionLumpSum mustBe None
      }

      "there are no income sources" in {
        val sut = createSUT()

        sut.getStatePensionLumpSum mustBe None
      }
    }
  }

  "primaryEmployment" must {
    "return a single nps income source" when {
      s"income sources has an employment type of ${TaiConstants.PrimaryEmployment}" in {
        val incomeSource = NpsIncomeSource(employmentType = Some(TaiConstants.PrimaryEmployment))
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.primaryEmployment mustBe Some(incomeSource)
      }

      "an empty nps income source is created" in {
        val incomeSource = NpsIncomeSource()
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.primaryEmployment mustBe Some(incomeSource)
      }
    }

    "return nothing" when {
      s"an income source has a value other than ${TaiConstants.PrimaryEmployment}" in {
        val incomeSource = NpsIncomeSource(employmentType = Some(TaiConstants.SecondaryEmployment))
        val sut = createSUT(incomeSources = Some(List(incomeSource)))

        sut.primaryEmployment mustBe None
      }

      "income sources is an empty list" in {
        val sut = createSUT(incomeSources = Some(Nil))

        sut.primaryEmployment mustBe None
      }

      "income sources is nothing" in {
        val sut = createSUT(incomeSources = None)

        sut.primaryEmployment mustBe None
      }
    }
  }

  "toTaxCodeIncomeTotal" must {
    "return a tax code income total" when {
      "given a list with a single merged employment" in {
        val sut = createSUT()
        val incomeSourceOne = "incomeSourceOne"
        val incomeAmount = 1023
        val totalTax = 42
        val totalTaxableIncome = 43
        val adjustedNetIncome = 1022

        val npsTax = NpsTax(
          totalIncome = Some(NpsComponent(amount = Some(incomeAmount))),
          totalTax = Some(totalTax),
          totalTaxableIncome = Some(totalTaxableIncome)
        )
        val npsIncomeSource = NpsIncomeSource(
          name = Some(incomeSourceOne),
          employmentType = Some(TaiConstants.PrimaryEmployment),
          payAndTax = Some(npsTax))
        val mergedEmployment = MergedEmployment(npsIncomeSource, None, adjustedNetIncome = Some(adjustedNetIncome))

        sut.toTaxCodeIncomeTotal(List(mergedEmployment)) mustBe Some(
          TaxCodeIncomeTotal(
            List(TaxCodeIncomeSummary(
              name = incomeSourceOne,
              taxCode = "",
              employmentType = Some(TaiConstants.PrimaryEmployment),
              incomeType = Some(0),
              tax = Tax(
                totalIncome = Some(incomeAmount),
                totalTax = Some(totalTax),
                totalTaxableIncome = Some(totalTaxableIncome),
                actualTaxDueAssumingBasicRateAlreadyPaid = Some(totalTax)
              ),
              income = Some(adjustedNetIncome),
              isEditable = true,
              isLive = true
            )),
            adjustedNetIncome,
            totalTax,
            totalTaxableIncome
          )
        )
      }
    }

    "return a sorted tax code income total" when {
      "given a list with multiple merged employments" in {
        val sut = createSUT()
        val incomeSourceOne = "incomeSourceOne"
        val incomeSourceTwo = "incomeSourceTwo"
        val incomeAmountOne = 1023
        val incomeAmountTwo = 1000
        val totalTaxOne = 42
        val totalTaxTwo = 50
        val totalTaxableIncomeOne = 43
        val totalTaxableIncomeTwo = 40
        val adjustedNetIncomeOne = 1022
        val adjustedNetIncomeTwo = 1001

        val npsTaxOne = NpsTax(
          totalIncome = Some(NpsComponent(amount = Some(incomeAmountOne))),
          totalTax = Some(totalTaxOne),
          totalTaxableIncome = Some(totalTaxableIncomeOne)
        )
        val npsTaxTwo = NpsTax(
          totalIncome = Some(NpsComponent(amount = Some(incomeAmountTwo))),
          totalTax = Some(totalTaxTwo),
          totalTaxableIncome = Some(totalTaxableIncomeTwo)
        )

        val npsIncomeSourceOne = NpsIncomeSource(
          name = Some(incomeSourceOne),
          employmentType = Some(TaiConstants.PrimaryEmployment),
          payAndTax = Some(npsTaxOne)
        )
        val npsIncomeSourceTwo = NpsIncomeSource(
          name = Some(incomeSourceTwo),
          employmentType = Some(TaiConstants.SecondaryEmployment),
          payAndTax = Some(npsTaxTwo)
        )

        val mergedEmploymentOne =
          MergedEmployment(npsIncomeSourceOne, None, adjustedNetIncome = Some(adjustedNetIncomeOne))
        val mergedEmploymentTwo =
          MergedEmployment(npsIncomeSourceTwo, None, adjustedNetIncome = Some(adjustedNetIncomeTwo))

        sut.toTaxCodeIncomeTotal(List(mergedEmploymentTwo, mergedEmploymentOne)) mustBe Some(
          TaxCodeIncomeTotal(
            List(
              TaxCodeIncomeSummary(
                name = incomeSourceOne,
                taxCode = "",
                employmentType = Some(TaiConstants.PrimaryEmployment),
                incomeType = Some(0),
                tax = Tax(
                  totalIncome = Some(incomeAmountOne),
                  totalTax = Some(totalTaxOne),
                  totalTaxableIncome = Some(totalTaxableIncomeOne),
                  actualTaxDueAssumingBasicRateAlreadyPaid = Some(totalTaxOne)
                ),
                income = Some(adjustedNetIncomeOne),
                isEditable = true,
                isLive = true
              ),
              TaxCodeIncomeSummary(
                name = incomeSourceTwo,
                taxCode = "",
                employmentType = Some(TaiConstants.SecondaryEmployment),
                incomeType = Some(0),
                tax = Tax(
                  totalIncome = Some(incomeAmountTwo),
                  totalTax = Some(totalTaxTwo),
                  totalTaxableIncome = Some(totalTaxableIncomeTwo),
                  actualTaxDueAssumingBasicRateAlreadyPaid = Some(totalTaxTwo)
                ),
                income = Some(adjustedNetIncomeTwo),
                isEditable = true,
                isLive = true,
                isPrimary = false
              )
            ),
            adjustedNetIncomeOne + adjustedNetIncomeTwo,
            totalTaxOne + totalTaxTwo,
            totalTaxableIncomeOne + totalTaxableIncomeTwo
          )
        )
      }
    }

    "return nothing" when {
      "given an empty list" in {
        val sut = createSUT()

        sut.toTaxCodeIncomeTotal(Nil) mustBe None
      }
    }

  }

  "getTaxCodeDetails" must {
    "not return TaxCodeDetails" when {
      "income sources are not given" in {

        createSUT().getTaxCodeDetails(None) mustBe None
      }
    }
    "return empty TaxCodeDetails" when {

      "income sources are an empty list" in {

        val expectedResult =
          TaxCodeDetails(Some(List()), Some(List()), Some(List()), Some(List()), Some(List()), None, 0)

        createSUT().getTaxCodeDetails(Some(Nil)) mustBe Some(expectedResult)
      }
    }
    "return populated TaxCodeDetails" when {
      "income sources are provided with one allowance and one deduction" in {

        val expectedResult = TaxCodeDetails(
          Some(List(Employments(Some(123), Some("name"), Some("K950L X"), Some(BasisOperation.Week1Month1)))),
          Some(List(TaxCode(Some("K"), None), TaxCode(Some("L"), None))),
          Some(List(TaxCodeDescription("K950L X", "name", List(TaxCode(Some("K"), None))))),
          Some(
            List(
              TaxCodeComponent(
                Some("professional subscriptions test description"),
                Some(50),
                Some(ProfessionalSubscriptions.code)))),
          Some(
            List(
              TaxCodeComponent(
                Some("personal payments test description"),
                Some(100),
                Some(PersonalPensionPayments.code)))),
          None,
          0
        )

        createSUT().getTaxCodeDetails(Some(List(incomeSourceWithOneAllowanceAndDeduction))) mustBe Some(expectedResult)
      }
      "income sources are provided with multiple allowances and deductions" in {

        val incomeSourceWithMultipleAllowancesAndDeductions = incomeSourceWithOneAllowanceAndDeduction.copy(
          allowances = incomeSourceWithOneAllowanceAndDeduction.allowances.map(
            NpsComponent(
              amount = Some(200),
              `type` = Some(PersonalPensionPayments.code),
              iabdSummaries = None,
              npsDescription = None,
              sourceAmount = None) :: _),
          deductions = incomeSourceWithOneAllowanceAndDeduction.deductions.map(
            NpsComponent(
              amount = Some(150),
              `type` = Some(ProfessionalSubscriptions.code),
              iabdSummaries = None,
              npsDescription = None,
              sourceAmount = None) :: _)
        )

        val expectedResult = TaxCodeDetails(
          Some(List(Employments(Some(123), Some("name"), Some("K950L X"), Some(BasisOperation.Week1Month1)))),
          Some(List(TaxCode(Some("K"), None), TaxCode(Some("L"), None))),
          Some(List(TaxCodeDescription("K950L X", "name", List(TaxCode(Some("K"), None))))),
          Some(List(TaxCodeComponent(None, Some(200), Some(ProfessionalSubscriptions.code)))),
          Some(List(TaxCodeComponent(None, Some(300), Some(PersonalPensionPayments.code)))),
          None,
          0
        )

        createSUT().getTaxCodeDetails(Some(List(incomeSourceWithMultipleAllowancesAndDeductions))) mustBe Some(
          expectedResult)

      }
    }
  }

  "getTaxCodeRate" must {
    "return the first rate for the given tax code" when {
      "the code is contained in the income sources" in {
        val sut = createSUT(None, Some(List(incomeSourceWithOneAllowanceAndDeduction)))

        sut.getTaxCodeRate("BR") mustBe Some(20)
      }
    }
    "not return a rate" when {
      "the given code is not in the income sources" in {
        val sut = createSUT(None, Some(List(incomeSourceWithOneAllowanceAndDeduction)))

        sut.getTaxCodeRate("D1") mustBe None
      }
      "no income sources are provided" in {
        val sut = createSUT(None, None)

        sut.getTaxCodeRate("D1") mustBe None
      }
    }
  }

  "taxCodeRates" must {
    "retrun a single pair of tax codes and rates" when {
      "given one tax rate in the income sources" in {
        val sut = createSUT(None, Some(List(incomeSourceWithOneAllowanceAndDeduction)))

        sut.taxCodeRates mustBe Some(List(("BR", Some(20))))
      }
    }
    "retrun multiple pairs of tax codes and rates" when {
      "given multiple tax rates in the income sources" in {

        val multipleTaxCodeIncomeSource = incomeSourceWithOneAllowanceAndDeduction.copy(
          payAndTax = incomeSourceWithOneAllowanceAndDeduction.payAndTax.map(_ =>
            NpsTax(taxBands = Some(List(firstTaxBand, secondTaxBand)))))
        val sut = createSUT(None, Some(List(multipleTaxCodeIncomeSource)))

        sut.taxCodeRates mustBe Some(List(("BR", Some(20)), ("D0", Some(40))))
      }
    }
  }

  "getOperatedTaxCode" must {
    "return the original taxcode" when {
      "there is no basis of operation specified" in {
        createSUT().getOperatedTaxCode(Some("BR"), None) mustBe Some("BR")
      }
      "the basis of operation is not week 1 month 1" in {
        createSUT().getOperatedTaxCode(Some("BR"), Some(BasisOperation.Cumulative)) mustBe Some("BR")
      }
      "the basis of operation is week 1 month 1 but the code is a no tax code" in {
        createSUT().getOperatedTaxCode(Some("NT"), Some(BasisOperation.Week1Month1)) mustBe Some("NT")
      }
    }
    "return the emergency tax code suffix" when {
      "the basis of operation is week 1 month 1" in {
        val sut = createSUT()

        sut.getOperatedTaxCode(Some("BR"), Some(BasisOperation.Week1Month1)) mustBe Some("BR X")
      }
    }
  }

  "getTaxCodeComponent" must {
    "return a tax code component" when {
      "given a single NpsComponent" in {
        val sut = createSUT()

        sut.getTaxCodeComponent(Some(List(PersonalPensionPaymentNpsComponent))) mustBe Some(
          List(PersonalPensionTaxCodeComponent))
      }
    }
    "return multiple tax code components" when {
      "given multiple NpsComponents" in {
        val sut = createSUT()

        val npsComponents = Some(List(PersonalPensionPaymentNpsComponent, ProfessionalSubscriptionsNpsComponent))

        val taxComponents = Some(List(PersonalPensionTaxCodeComponent, ProfessionalSubscriptionsTaxCodeComponent))

        sut.getTaxCodeComponent(npsComponents) mustBe taxComponents
      }
    }
    "return no tax components" when {
      "no NpsComponents are given" in {
        val sut = createSUT()

        sut.getTaxCodeComponent(None) mustBe None
        sut.getTaxCodeComponent(Some(Nil)) mustBe Some(Nil)
      }
    }
  }

  "getTaxCodes" must {
    "return a single tax code" when {
      "given a single income source" in {
        val sut = createSUT()

        sut.getTaxCodes(List(incomeSourceWithOneAllowanceAndDeduction)) mustBe List("K", "L")
      }
      "given a single income source with no tax code populated" in {
        val sut = createSUT()

        sut.getTaxCodes(List(incomeSourceWithOneAllowanceAndDeduction.copy(taxCode = None))) mustBe Nil
      }

    }
    "return multiple tax codes" when {
      "given multiple income sources" in {
        val sut = createSUT()

        sut.getTaxCodes(
          List(
            incomeSourceWithOneAllowanceAndDeduction,
            incomeSourceWithOneAllowanceAndDeduction.copy(taxCode = Some("S30NT")))) mustBe List("K", "L", "S", "T")
      }
    }
    "return no tax codes" when {
      "there are no income sources" in {
        createSUT().getTaxCodes(Nil) mustBe Nil
      }
    }
  }

  "TaxCodeIncomeSummary" must {
    "have isLive and isEditable set correctly if the employments are live" in {
      val npsEmployments = NpsData.getNpsBasicRateLivePensions()
      val npsTaxAccount = NpsData.getNpsBasicRateLivePensionTaxAccount()

      val mergedTaxAccount = npsTaxAccount.toTaxSummary(1, npsEmployments)
      val pensions = mergedTaxAccount.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.occupationalPensions))

      pensions.isDefined mustBe true
      pensions.get.taxCodeIncomes.size mustBe 3

      pensions.get.taxCodeIncomes(0).isLive mustBe true
      pensions.get.taxCodeIncomes(0).isEditable mustBe true
      pensions.get.taxCodeIncomes(0).isOccupationalPension mustBe true
      pensions.get.taxCodeIncomes(0).otherIncomeSourceIndicator mustBe Some(false)
      pensions.get.taxCodeIncomes(0).isPrimary mustBe true

      pensions.get.taxCodeIncomes(1).isPrimary mustBe false
      pensions.get.taxCodeIncomes(2).isPrimary mustBe false
    }

    "have isLive and isEditable set correctly if the employments are ceased" in {
      val npsEmployments = NpsData.getNpsCeasedEmployments()
      val npsTaxAccount = NpsData.getNpsCeasedEmploymentTaxAccount()

      val mergedTaxAccount = npsTaxAccount.toTaxSummary(1, npsEmployments)
      val employments = mergedTaxAccount.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.ceasedEmployments))

      employments.isDefined mustBe true
      employments.get.taxCodeIncomes.size mustBe 1

      employments.get.taxCodeIncomes(0).isLive mustBe false
      employments.get.taxCodeIncomes(0).isEditable mustBe false
      employments.get.taxCodeIncomes(0).isOccupationalPension mustBe false
      employments.get.taxCodeIncomes(0).otherIncomeSourceIndicator mustBe Some(false)
      employments.get.taxCodeIncomes(0).isPrimary mustBe true

    }

    "have isLive and isEditable set correctly the employments are potentially ceased" in {
      val npsEmployments = NpsData.getNpsPotentiallyCeasedEmployments()
      val npsTaxAccount = NpsData.getNpsPotentiallyCeasedEmploymentTaxAccount()

      val mergedTaxAccount = npsTaxAccount.toTaxSummary(1, npsEmployments)
      val employments = mergedTaxAccount.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.ceasedEmployments))

      employments.isDefined mustBe true
      employments.get.taxCodeIncomes.size mustBe 1

      employments.get.taxCodeIncomes(0).isLive mustBe false
      employments.get.taxCodeIncomes(0).isEditable mustBe true
      employments.get.taxCodeIncomes(0).isOccupationalPension mustBe false
      employments.get.taxCodeIncomes(0).otherIncomeSourceIndicator mustBe Some(false)
      employments.get.taxCodeIncomes(0).isPrimary mustBe true

      val npsEmploymentsWithCessationPay =
        npsEmployments.map(emp => emp.copy(cessationPayThisEmployment = Some(BigDecimal(12)))).toList
      val mergedTaxAccountWithCessationPay = npsTaxAccount.toTaxSummary(1, npsEmploymentsWithCessationPay)

      val employmentsWithCessation =
        mergedTaxAccountWithCessationPay.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.ceasedEmployments))

      employmentsWithCessation.isDefined mustBe true
      employmentsWithCessation.get.taxCodeIncomes.size mustBe 1

      employmentsWithCessation.get.taxCodeIncomes(0).isLive mustBe false
      employmentsWithCessation.get.taxCodeIncomes(0).isEditable mustBe false

    }

    "have isLive and isEditable set correctly the employments has a JSA Indicator" in {
      val npsEmployments = NpsData.getNpsTwoEmploymentsOneWithJSAIndicator()
      val npsTaxAccount = NpsData.getNpsTwoEmploymentsOneWithJSAIndicatorTaxAccount()

      val mergedTaxAccount = npsTaxAccount.toTaxSummary(1, npsEmployments)

      val taxableStateBenefit =
        mergedTaxAccount.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.taxableStateBenefitIncomes))
      taxableStateBenefit.isDefined mustBe true
      taxableStateBenefit.get.taxCodeIncomes.size mustBe 1

      taxableStateBenefit.get.taxCodeIncomes(0).isLive mustBe true
      taxableStateBenefit.get.taxCodeIncomes(0).isEditable mustBe false
      taxableStateBenefit.get.taxCodeIncomes(0).isOccupationalPension mustBe false
      taxableStateBenefit.get.taxCodeIncomes(0).otherIncomeSourceIndicator mustBe Some(false)
      taxableStateBenefit.get.taxCodeIncomes(0).isPrimary mustBe true

      val employments = mergedTaxAccount.increasesTax.flatMap(_.incomes.flatMap(_.taxCodeIncomes.employments))
      employments.isDefined mustBe true
      employments.get.taxCodeIncomes.size mustBe 1

      employments.get.taxCodeIncomes(0).isLive mustBe true
      employments.get.taxCodeIncomes(0).isEditable mustBe true
      employments.get.taxCodeIncomes(0).isOccupationalPension mustBe false
      employments.get.taxCodeIncomes(0).otherIncomeSourceIndicator mustBe Some(false)
      employments.get.taxCodeIncomes(0).isPrimary mustBe false

    }
  }

  val firstTaxBand = TaxBand(Some(1000), Some(200), Some(400), Some(800), Some(20))
  val secondTaxBand = TaxBand(Some(500), Some(10), Some(800), Some(6000), Some(40))

  val PersonalPensionPaymentNpsComponent = NpsComponent(
    amount = Some(100),
    `type` = Some(PersonalPensionPayments.code),
    iabdSummaries = None,
    npsDescription = Some("personal payments test description"),
    sourceAmount = None
  )

  val PersonalPensionTaxCodeComponent =
    TaxCodeComponent(Some("personal payments test description"), Some(100), Some(PersonalPensionPayments.code))

  val ProfessionalSubscriptionsNpsComponent = NpsComponent(
    amount = Some(50),
    `type` = Some(ProfessionalSubscriptions.code),
    iabdSummaries = None,
    npsDescription = Some("professional subscriptions test description"),
    sourceAmount = None
  )

  val ProfessionalSubscriptionsTaxCodeComponent = TaxCodeComponent(
    Some("professional subscriptions test description"),
    Some(50),
    Some(ProfessionalSubscriptions.code))

  val incomeSourceWithOneAllowanceAndDeduction = NpsIncomeSource(
    name = Some("name"),
    taxCode = Some("K950L"),
    employmentType = Some(1),
    allowances = Some(List(PersonalPensionPaymentNpsComponent)),
    deductions = Some(List(ProfessionalSubscriptionsNpsComponent)),
    payAndTax = Some(NpsTax(taxBands = Some(List(firstTaxBand)))),
    employmentId = Some(123),
    employmentStatus = None,
    employmentTaxDistrictNumber = None,
    employmentPayeRef = None,
    pensionIndicator = None,
    otherIncomeSourceIndicator = None,
    jsaIndicator = None,
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  private val nino: Nino = new Generator(new Random).nextNino

  private val taxYear: Int = LocalDateTime.now(ZoneId.of("Europe/London")).getYear

  private def createSUT(
    adjustedNetIncome: Option[NpsComponent] = None,
    incomeSources: Option[List[NpsIncomeSource]] = None) =
    NpsTaxAccount(Some(nino.nino), Some(taxYear), adjustedNetIncome = adjustedNetIncome, incomeSources = incomeSources)
}
