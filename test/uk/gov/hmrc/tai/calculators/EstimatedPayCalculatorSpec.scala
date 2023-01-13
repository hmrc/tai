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

package uk.gov.hmrc.tai.calculators

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.PayDetails
import uk.gov.hmrc.tai.model.enums.PayFreq
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.time.temporal.ChronoUnit
import scala.math.BigDecimal.RoundingMode

class EstimatedPayCalculatorSpec extends PlaySpec {

  "Estimated Pay Calculator" must {

    "calculate the correct gross and net amount" when {
      "monthly amount is entered" in {
        val payDetails = PayDetails(paymentFrequency = PayFreq.monthly, pay = Some(12), taxablePay = Some(10))
        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(144))
        calculated.netAnnualPay mustBe Some(BigDecimal(120))
      }

      "weekly amount is entered" in {
        val payDetails = PayDetails(paymentFrequency = PayFreq.weekly, pay = Some(12), taxablePay = Some(10))
        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(624))
        calculated.netAnnualPay mustBe Some(BigDecimal(520))
      }

      "2 weekly amount is entered" in {
        val payDetails = PayDetails(paymentFrequency = PayFreq.fortnightly, pay = Some(12), taxablePay = Some(10))
        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(312))
        calculated.netAnnualPay mustBe Some(BigDecimal(260))
      }

      "other amount is entered and days have been entered with no bonus" in {
        val payDetails =
          PayDetails(paymentFrequency = PayFreq.other, pay = Some(100), taxablePay = Some(80), days = Some(27))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(1351))
        calculated.netAnnualPay mustBe Some(BigDecimal(1081))
      }

      "other amount is entered and days have been entered and bounus has been entered" in {
        val payDetails = PayDetails(
          paymentFrequency = PayFreq.other,
          pay = Some(100),
          taxablePay = Some(80),
          days = Some(27),
          bonus = Some(1000))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(2351))
        calculated.netAnnualPay mustBe Some(BigDecimal(2081))
      }

      "other amount is entered and No days have been entered and bonus has been entered" in {
        val payDetails =
          PayDetails(paymentFrequency = PayFreq.other, pay = Some(100), taxablePay = Some(80), bonus = Some(1000))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(1000))
        calculated.netAnnualPay mustBe Some(BigDecimal(1000))
      }
    }

    "return the net pay the same as the gross pay" when {

      "no net has been entered" in {
        val payDetails =
          PayDetails(paymentFrequency = PayFreq.other, pay = Some(100), taxablePay = None, bonus = Some(1000))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay mustBe Some(BigDecimal(1000))
        calculated.netAnnualPay mustBe Some(BigDecimal(1000))
      }
    }

    "calculate net and gross pay amounts which are apportioned for the current year" when {

      "an employment start date is supplied which falls in the current tax year" in {

        val daysInHalfYear: Int = ChronoUnit.DAYS.between(TaxYear().start, TaxYear().next.start).toInt / 2

        val startDateHalfThroughYear = TaxYear().end.minusDays(daysInHalfYear)

        val payDetails = PayDetails(
          paymentFrequency = PayFreq.weekly,
          pay = Some(100),
          taxablePay = Some(80),
          bonus = None,
          startDate = Some(startDateHalfThroughYear))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(2607.12)
        calculated.netAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(2085.69)
      }
    }

    "calculate net and gross pay amounts which are 0" when {

      "an employment start date is supplied which falls in the current tax year and the annual amount is 0" in {
        val payDetails = PayDetails(
          paymentFrequency = PayFreq.weekly,
          pay = Some(0),
          taxablePay = Some(0),
          bonus = None,
          startDate = Some(TaxYear().start))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0)
        calculated.netAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0)
      }

      "an employment start date is supplied which falls in the next tax year" in {
        val startDateInNextTaxYear = TaxYear().next.start
        val payDetails = PayDetails(
          paymentFrequency = PayFreq.weekly,
          pay = Some(100),
          taxablePay = Some(80),
          bonus = None,
          startDate = Some(startDateInNextTaxYear))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0.00)
        calculated.netAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0.00)
      }

      "no annual pay or taxablePay amounts are supplied, but an employment start date is supplied" in {
        val payDetails = PayDetails(
          paymentFrequency = PayFreq.weekly,
          pay = None,
          taxablePay = None,
          bonus = None,
          startDate = Some(TaxYear().start))

        val calculated = EstimatedPayCalculator.calculate(payDetails = payDetails)

        calculated.grossAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0.00)
        calculated.netAnnualPay.get.setScale(2, RoundingMode.FLOOR) mustBe BigDecimal(0.00)
      }
    }
  }
}
