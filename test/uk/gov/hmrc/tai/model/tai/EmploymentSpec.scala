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

package uk.gov.hmrc.tai.model.tai

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.model.nps2.IabdType.GiftAidPayments
import uk.gov.hmrc.tai.model.nps2.IabdUpdateSource.Letter
import uk.gov.hmrc.tai.model.nps2.{Iabd, Income, NpsEmployment}
import uk.gov.hmrc.tai.model.nps2.Income.{IncomeType, Status}
import uk.gov.hmrc.tai.model.rti.{PayFrequency, RtiEmployment, RtiPayment}
import uk.gov.hmrc.tai.model.enums.BasisOperation

class EmploymentSpec extends PlaySpec {

  "Employment" should {
    "Create an employment object" when {
      "Given an primary employment nps object but no rti object" in {
        val employment: Employment = Employment(testIncome, None)
        employment.isPrimary mustBe true
        employment.payments mustBe Nil
      }

      "Given a non primary nps object but no rti object" in {
        val employment: Employment = Employment(testIncome2, None)
        employment.isPrimary mustBe false
        employment.payments mustBe Nil
      }

      "Given an nps object and an rti object" in {
        val employment: Employment = Employment(testIncome, Some(rtiEmp))
        employment.isPrimary mustBe true
        employment.payments.head.taxablePayYTD mustBe 20000
      }
    }
  }

  private val fixedDate = LocalDate.parse("2017-12-12")

  private val testIabd = Iabd(amount = 10,
    iabdType = GiftAidPayments,
    source = Letter,
    description = "dummyDescription",
    employmentSequence = Some(32))

  private val testNpsEmployment = NpsEmployment(
    employerName = Some("Company Plc"),
    isPrimary = true,
    sequenceNumber = 1,
    worksNumber = Some("1234"),
    districtNumber = 1,
    iabds = List(testIabd),
    cessationPay = Some(2200.22),
    start = fixedDate
  )

  private val testNpsEmployment2 = NpsEmployment(
    employerName = Some("Company Plc"),
    isPrimary = false,
    sequenceNumber = 1,
    worksNumber = Some("1234"),
    districtNumber = 1,
    iabds = List(testIabd),
    cessationPay = Some(2200.22),
    start = fixedDate
  )

  private val testIncome = Income(
    employmentId = Some(1),
    isPrimary = true,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), ceased = Some(fixedDate)),
    taxDistrict = Some(1),
    payeRef = "payRef",
    name = "name",
    worksNumber = Some("1234"),
    taxCode="AB1234",
    potentialUnderpayment = 20.20,
    employmentRecord = Some(testNpsEmployment),
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  private val testIncome2 = Income(
    employmentId = Some(2),
    isPrimary = false,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), ceased = Some(fixedDate)),
    taxDistrict = Some(1),
    payeRef = "payRef",
    name = "name",
    worksNumber = Some("1234"),
    taxCode="AB1234",
    potentialUnderpayment = 20.20,
    employmentRecord = Some(testNpsEmployment2),
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  private val payment = RtiPayment(PayFrequency.FourWeekly, new LocalDate(2017, 4, 20), new LocalDate(2017, 4, 20),
    BigDecimal(20), BigDecimal(20000), BigDecimal(0), BigDecimal(0), None, isOccupationalPension = false, None, Some(10))

  private val rtiEmp = RtiEmployment("", "123", "", List(payment), Nil, Some("1000"), 1)

}
