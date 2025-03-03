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

import java.time.LocalDate

class IncomeFormsSpec extends PlaySpec {

  "EditIncomeForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = EditIncomeForm(
        name = "Employer A",
        description = "Salary",
        employmentId = 123,
        newAmount = Some("50000"),
        oldAmount = 45000,
        worksNumber = Some("W123"),
        jobTitle = Some("Software Engineer"),
        startDate = Some(LocalDate.of(2020, 1, 1)),
        endDate = None,
        payToDate = Some("20000")
      )

      val json = Json.toJson(form)
      json.as[EditIncomeForm] mustBe form
    }
  }

  "HowToUpdateForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = HowToUpdateForm(Some("ManualUpdate"))
      val json = Json.toJson(form)

      json.as[HowToUpdateForm] mustBe form
    }
  }

  "HoursWorkedForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = HoursWorkedForm(Some("40"))
      val json = Json.toJson(form)

      json.as[HoursWorkedForm] mustBe form
    }
  }

  "PayPeriodForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = PayPeriodForm(Some("Monthly"), Some(30))
      val json = Json.toJson(form)

      json.as[PayPeriodForm] mustBe form
    }
  }

  "PayslipForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = PayslipForm(Some("3000"))
      val json = Json.toJson(form)

      json.as[PayslipForm] mustBe form
    }
  }

  "TaxablePayslipForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = TaxablePayslipForm(Some("2800"))
      val json = Json.toJson(form)

      json.as[TaxablePayslipForm] mustBe form
    }
  }

  "PayslipDeductionsForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = PayslipDeductionsForm(Some("200"))
      val json = Json.toJson(form)

      json.as[PayslipDeductionsForm] mustBe form
    }
  }

  "BonusPaymentsForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = BonusPaymentsForm(Some("5000"), Some("Yes"))
      val json = Json.toJson(form)

      json.as[BonusPaymentsForm] mustBe form
    }
  }

  "BonusOvertimeAmountForm JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = BonusOvertimeAmountForm(Some("1500"))
      val json = Json.toJson(form)

      json.as[BonusOvertimeAmountForm] mustBe form
    }
  }

  "IncomeCalculation JSON serialization" must {
    "serialize and deserialize correctly" in {
      val form = IncomeCalculation(
        incomeId = Some(123),
        howToUpdateForm = Some(HowToUpdateForm(Some("Auto"))),
        hoursWorkedForm = Some(HoursWorkedForm(Some("40"))),
        payPeriodForm = Some(PayPeriodForm(Some("Monthly"), Some(30))),
        payslipForm = Some(PayslipForm(Some("3000"))),
        taxablePayslipForm = Some(TaxablePayslipForm(Some("2800"))),
        payslipDeductionsForm = Some(PayslipDeductionsForm(Some("200"))),
        bonusPaymentsForm = Some(BonusPaymentsForm(Some("5000"), Some("Yes"))),
        bonusOvertimeAmountForm = Some(BonusOvertimeAmountForm(Some("1500"))),
        netAmount = Some(BigDecimal(2500)),
        grossAmount = Some(BigDecimal(3000))
      )

      val json = Json.toJson(form)
      json.as[IncomeCalculation] mustBe form
    }
  }
}
