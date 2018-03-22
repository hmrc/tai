/*
 * Copyright 2018 HM Revenue & Customs
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


import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.enums.IncomeType.IncomeTypeEmployment
import uk.gov.hmrc.tai.model.nps2.{AllowanceType, DeductionType}
import uk.gov.hmrc.tai.model.{Tax, TaxCodeIncomeSummary}
import uk.gov.hmrc.tai.model.enums.BasisOperation
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation
import uk.gov.hmrc.tai.util.TaiConstants


class NpsIncomeSourceSpec extends PlaySpec  {
  "toTaxCodeIncomeSummary" should {
    "create a tax code income summary" when {
      "there is an employment but no adjusted net income" in {
        val startDate = NpsDate(new LocalDate("2017-01-01"))
        val endDate = NpsDate(new LocalDate("2017-01-02"))
        val employmentType = 42
        val worksNumber = "This is the works number"
        val jobTitle = "This is the job title"
        val name = "This is the name"
        val taxCode = "This is the tax code"
        val employmentId = 314
        val employmentStatus = 21
        val employmentTaxDistrictNumber = 99
        val payeRef = "This is the PAYE ref"
        val otherIncomeSourceIndicator = true

        val employment = NpsEmployment(sequenceNumber = 1,
          startDate = startDate,
          endDate = Some(endDate),
          taxDistrictNumber = "",
          payeNumber = "",
          employerName = None,
          employmentType = employmentType,
          employmentStatus = None,
          worksNumber = Some(worksNumber),
          jobTitle = Some(jobTitle),
          startingTaxCode = None,
          receivingJobseekersAllowance = None,
          receivingOccupationalPension = None,
          otherIncomeSourceIndicator = None,
          payrolledTaxYear = None,
          payrolledTaxYear1 = None,
          cessationPayThisEmployment = None)

        val sut = createSUT(
          name = Some(name),
          taxCode = Some(taxCode),
          employmentType = Some(TaiConstants.SecondaryEmployment),
          payAndTax = Some(NpsTax()),
          employmentId = Some(employmentId),
          employmentStatus = Some(employmentStatus),
          employmentTaxDistrictNumber = Some(employmentTaxDistrictNumber),
          employmentPayeRef = Some(payeRef),
          pensionIndicator = Some(true),
          otherIncomeSourceIndicator = Some(otherIncomeSourceIndicator),
          jsaIndicator = Some(false),
          basisOperation = Some(BasisOperation.Cumulative)
        )

        val result = sut.toTaxCodeIncomeSummary(Some(employment))

        result mustBe TaxCodeIncomeSummary(
          name = name,
          taxCode = taxCode,
          employmentId = Some(employmentId),
          employmentPayeRef = Some(payeRef),
          employmentType = Some(TaiConstants.SecondaryEmployment),
          incomeType = Some(1),
          employmentStatus = Some(employmentStatus),
          tax = Tax(),
          worksNumber = Some(worksNumber),
          jobTitle = Some(jobTitle),
          startDate = Some(startDate.localDate),
          endDate = Some(endDate.localDate),
          income = None,
          otherIncomeSourceIndicator = Some(otherIncomeSourceIndicator),
          isOccupationalPension = true,
          isPrimary = false,
          potentialUnderpayment = None,
          basisOperation = Some(BasisOperation.Cumulative)
        )
      }

      "there is no employment or adjusted net income" in {
        val sut = createSUT()

        val result = sut.toTaxCodeIncomeSummary()

        result mustBe TaxCodeIncomeSummary(name = "", taxCode = "", incomeType = Some(0), tax = Tax(), isEditable = true, isLive = true)
      }
    }

    "create a tax code income summary with the total income amount" when {
      "there is a total income amount in pay and tax" in {
        val totalIncomeAmount = 111
        val payAndTax = NpsTax(totalIncome = Some(NpsComponent(amount = Some(totalIncomeAmount))))

        val sut = createSUT(payAndTax = Some(payAndTax))

        val result = sut.toTaxCodeIncomeSummary()

        result mustBe TaxCodeIncomeSummary(
          name = "",
          taxCode = "",
          incomeType = Some(0),
          tax = Tax(Some(111)),
          income = Some(111),
          isEditable = true,
          isLive = true
        )
      }
    }

    "create a tax code income summary with the total income amount" when {
      "there is an adjusted net income" in {
        val adjustedNetIncome = 777

        val sut = createSUT()

        val result = sut.toTaxCodeIncomeSummary(adjustedNetIncome = Some(adjustedNetIncome))

        result mustBe TaxCodeIncomeSummary(
          name = "",
          taxCode = "",
          incomeType = Some(0),
          tax = Tax(),
          income = Some(adjustedNetIncome),
          isEditable = true,
          isLive = true
        )
      }
    }
  }

  "personalAllowanceComponent" should {
    "return the personal allowance standard nps component" when {
      "there is one in the nps income source" in {
        val personalAllowanceStandard = NpsComponent(`type` = Some(AllowanceType.PersonalAllowanceStandard.id))

        val sut = createSUT(allowances = Some(List(personalAllowanceStandard)))
        val result = sut.personalAllowanceComponent

        result mustBe Some(personalAllowanceStandard)
      }
    }
    "return the personal allowance aged nps component" when {
      "there is one in the nps income source" in {
        val personalAllowanceAged = NpsComponent(`type` = Some(AllowanceType.PersonalAllowanceAged.id))

        val sut = createSUT(allowances = Some(List(personalAllowanceAged)))
        val result = sut.personalAllowanceComponent

        result mustBe Some(personalAllowanceAged)
      }
    }

    "return the personal allowance elderly nps component" when {
      "there is one in the nps income source" in {
        val personalAllowanceElderly = NpsComponent(`type` = Some(AllowanceType.PersonalAllowanceElderly.id))

        val sut = createSUT(allowances = Some(List(personalAllowanceElderly)))
        val result = sut.personalAllowanceComponent

        result mustBe Some(personalAllowanceElderly)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val personalSavingsAllowance = NpsComponent(`type` = Some(AllowanceType.PersonalSavingsAllowance.id))

        val sut = createSUT(allowances = Some(List(personalSavingsAllowance)))
        val result = sut.personalAllowanceComponent

        result mustBe None
      }
    }
  }

  "underpaymentComponent" should {
    "return the underpayment amount component" when {
      "there is one in the nps income source" in {
        val underpaymentAmount = NpsComponent(`type` = Some(DeductionType.UnderpaymentAmount.id))

        val sut = createSUT(deductions = Some(List(underpaymentAmount)))
        val result = sut.underpaymentComponent

        result mustBe Some(underpaymentAmount)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val underpaymentRestriction = NpsComponent(`type` = Some(DeductionType.UnderpaymentRestriction.id))

        val sut = createSUT(deductions = Some(List(underpaymentRestriction)))
        val result = sut.underpaymentComponent

        result mustBe None
      }
    }
  }

  "inYearAdjustmentComponent" should {
    "return the in year adjustment component" when {
      "there is one in the nps component" in {
        val inYearAdjustment = NpsComponent(`type` = Some(DeductionType.InYearAdjustment.id))

        val sut = createSUT(deductions = Some(List(inYearAdjustment)))
        val result = sut.inYearAdjustmentComponent

        result mustBe Some(inYearAdjustment)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val interestWithoutTaxTakenOffGrossInterest = NpsComponent(`type` = Some(DeductionType.InterestWithoutTaxTakenOffGrossInterest.id))

        val sut = createSUT(deductions = Some(List(interestWithoutTaxTakenOffGrossInterest)))
        val result = sut.inYearAdjustmentComponent

        result mustBe None
      }
    }
  }

  "outstandingDebtComponent" should {
    "return the outstanding debt component" when {
      "there is one in the nps component" in {
        val outstandingDebtRestriction = NpsComponent(`type` = Some(DeductionType.OutstandingDebtRestriction.id))

        val sut = createSUT(deductions = Some(List(outstandingDebtRestriction)))
        val result = sut.outstandingDebtComponent

        result mustBe Some(outstandingDebtRestriction)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val otherEarnings = NpsComponent(`type` = Some(DeductionType.OtherEarnings.id))

        val sut = createSUT(deductions = Some(List(otherEarnings)))
        val result = sut.outstandingDebtComponent

        result mustBe None
      }
    }
  }

  "personalAllowanceTransferred" should {
    "return the personal allowance transferred component" when {
      "there is one in the nps component" in {
        val personalAllowanceTransferred = NpsComponent(`type` = Some(DeductionType.PersonalAllowanceTransferred.id))

        val sut = createSUT(deductions = Some(List(personalAllowanceTransferred)))
        val result = sut.personalAllowanceTransferred

        result mustBe Some(personalAllowanceTransferred)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val personalAllowanceReceived = NpsComponent(`type` = Some(AllowanceType.PersonalAllowanceReceived.id))

        val sut = createSUT(allowances = Some(List(personalAllowanceReceived)))
        val result = sut.personalAllowanceTransferred

        result mustBe None
      }
    }
  }

  "personalAllowanceReceived" should {
    "return the personal allowance received component" when {
      "there is one in the nps component" in {
        val personalAllowanceReceived  = NpsComponent(`type` = Some(AllowanceType.PersonalAllowanceReceived.id))

        val sut = createSUT(allowances = Some(List(personalAllowanceReceived)))
        val result = sut.personalAllowanceReceived

        result mustBe Some(personalAllowanceReceived)
      }
    }

    "not return an nps component" when {
      "there are no matching personal allowance components" in {
        val personalAllowanceTransferred = NpsComponent(`type` = Some(DeductionType.PersonalAllowanceTransferred.id))

        val sut = createSUT(deductions = Some(List(personalAllowanceTransferred)))
        val result = sut.personalAllowanceReceived

        result mustBe None
      }
    }
  }

  "statePension" should {
    "return the amount of state pension or benefits" when {
      "there is an nps component of type state pension or benefits with an amount" in {
        val statePensionOrBenefits = NpsComponent(amount = Some(555),`type` = Some(DeductionType.StatePensionOrBenefits.id))

        val sut = createSUT(deductions = Some(List(statePensionOrBenefits)))
        val result = sut.statePension

        result mustBe Some(555)
      }
    }

    "not return an amount" when {
      "there is an nps component of type state pension or benefits but there is no amount" in {
        val statePensionOrBenefits = NpsComponent(amount = None, `type` = Some(DeductionType.StatePensionOrBenefits.id))

        val sut = createSUT(deductions = Some(List(statePensionOrBenefits)))
        val result = sut.statePension

        result mustBe None
      }

      "there is no nps component of type state pension of benefits" in {
        val statePensionOrBenefits = NpsComponent(amount = Some(666), `type` = Some(DeductionType.OtherPension.id))

        val sut = createSUT(deductions = Some(List(statePensionOrBenefits)))
        val result = sut.statePension

        result mustBe None
      }
    }
  }

  "incomeType" should {
    "return the correct income type" when {
      "there is no employment tax district number, employment paye ref, employment type, JSA indicator and pension indicator" in {
        val sut = createSUT()

        val result = sut.incomeType

        result mustBe IncomeTypeEmployment.code
      }
    }
  }

  "TaxCodeIncomeSummary Model" should {
    "return the basisOperation if basisOperation exists " in {
      val npsComponent = NpsComponent(amount = Some(BigDecimal(333.33)), `type` = Some(11), iabdSummaries = None, npsDescription = Some("npsDescription"))

      val payAndTax = NpsTax(
        totalIncome = Some(npsComponent),
        allowReliefDeducts = None,
        totalTaxableIncome = Some(BigDecimal(222.22)),
        totalTax = Some(BigDecimal(111.111)),
        taxBands = None
      )

      val npsIncomeSource = new NpsIncomeSource(
        name = None,
        taxCode = None,
        employmentType = Some(1),
        allowances = None,
        deductions = None,
        payAndTax = Some(payAndTax),
        employmentId = None,
        employmentStatus = None,
        employmentTaxDistrictNumber = None,
        employmentPayeRef = None,
        pensionIndicator = Some(false),
        otherIncomeSourceIndicator = Some(false),
        basisOperation = Some(BasisOperation.Week1Month1))

      npsIncomeSource.toTaxCodeIncomeSummary().basisOperation mustBe Some(BasisOperation.Week1Month1)
    }
  }

  "NpsIncomeSource Json" should {
    "transform correctly to valid NpsIncomeSource object With the the old Api" in {
      val npsIncomeSource = Json.parse(incomeSourceJson).as[NpsIncomeSource]
      npsIncomeSource.basisOperation mustBe None
    }

    "transform correctly to valid NpsIncomeSource object With the added new field basisOperation" in {
      val npsIncomeSource = Json.parse(incomeSourceWithBasisOperationIncludedInJson).as[NpsIncomeSource]
      npsIncomeSource.basisOperation mustBe Some(BasisOperation.Week1Month1)
    }
  }

  val incomeSourceJson: String = """{    "employmentId": 5,
                            |    "employmentType": 1,
                            |    "employmentStatus": 1,
                            |    "employmentTaxDistrictNumber": 1,
                            |    "employmentPayeRef": "000",
                            |    "pensionIndicator": true,
                            |    "otherIncomeSourceIndicator": false,
                            |    "jsaIndicator": false,
                            |    "name": "EMPLOYER1",
                            |    "taxCode": "K136",
                            |    "allowances": [],
                            |    "deductions": []
                            }""".stripMargin

  val incomeSourceWithBasisOperationIncludedInJson: String = """{
                                 "employmentId": 5,
                            |    "employmentType": 1,
                            |    "employmentStatus": 1,
                            |    "employmentTaxDistrictNumber": 1,
                            |    "employmentPayeRef": "000",
                            |    "pensionIndicator": true,
                            |    "otherIncomeSourceIndicator": false,
                            |    "jsaIndicator": false,
                            |    "name": "00000",
                            |    "taxCode": "K136",
                            |    "basisOperation":1,
                            |    "allowances": [],
                            |    "deductions": []
                            }""".stripMargin

  private def createSUT(name: Option[String] = None,
                        taxCode: Option[String] = None,
                        employmentType: Option[Int] = None,
                        allowances: Option[List[NpsComponent]] = None,
                        deductions: Option[List[NpsComponent]] = None,
                        payAndTax: Option[NpsTax] = None,
                        employmentId: Option[Int] = None,
                        employmentStatus: Option[Int] = None,
                        employmentTaxDistrictNumber: Option[Int] = None,
                        employmentPayeRef: Option[String] = None,
                        pensionIndicator: Option[Boolean] = None,
                        otherIncomeSourceIndicator: Option[Boolean] = None,
                        jsaIndicator: Option[Boolean] = None,
                        basisOperation: Option[BasisOperation] = None): NpsIncomeSource = {
    NpsIncomeSource(
      name = name,
      taxCode = taxCode,
      employmentType = employmentType,
      allowances = allowances,
      deductions = deductions,
      payAndTax = payAndTax,
      employmentId = employmentId,
      employmentStatus = employmentStatus,
      employmentTaxDistrictNumber = employmentTaxDistrictNumber,
      employmentPayeRef = employmentPayeRef,
      pensionIndicator = pensionIndicator,
      otherIncomeSourceIndicator = otherIncomeSourceIndicator,
      jsaIndicator = jsaIndicator,
      basisOperation = basisOperation
    )
  }
}
