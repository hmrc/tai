/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json._
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.nps2
import uk.gov.hmrc.tai.model.nps2.Income.{IncomeType, Status}
import uk.gov.hmrc.tai.model.nps2.{Income, NpsEmployment, TaxAccount, TaxDetail, TaxObject}
import uk.gov.hmrc.tai.model.rti._
import uk.gov.hmrc.tai.model.enums.BasisOperation

import scala.util.Random

class AnnualAccountSpec extends PlaySpec {

  "Annual Account employments" should {
    "fetch the correct sequence of employments" when {
      "there is no nps or rti data" in {
        sut() mustBe List()
      }

      "there is no RTI data" in {
        val taxAccount = TaxAccount(id = None, date = None, tax = 1564.45)

        sut(nps = Some(taxAccount)) mustBe List()
      }

      "there is no tax account data" in {
        sut(rti = RtiGenerator.rtiData.sample) mustBe List()
      }

      "there is one employment between nps and rti" in {
        val employment = rtiSampleEmployments.sample.get
        val rtiData = RtiData(nino.nino,TaxYear(2016),"blerg",employments = List(employment))

        val income = testIncome(employment.officeRefNo.toInt, employment.payeRef)
        val taxAccount = TaxAccount(id = None, date = None, tax = 1564.45, incomes = List(income))

        sut(nps = Some(taxAccount), rti = Some(rtiData)) mustBe List(Employment(income, Some(employment)))
      }

      "there are multiple employments between nps and rti with the same paye ref and matching tax district and office ref no" in {
        val employment = rtiSampleEmployments.sample.get
        val rtiData = RtiData(nino.nino,TaxYear(2016),"blerg",employments = List(employment))
        val income = testIncome(employment.officeRefNo.toInt, employment.payeRef)
        val taxAccount = TaxAccount(id = None, date = None, tax = 1564.45, incomes = List(income,income))

        sut(nps = Some(taxAccount), rti = Some(rtiData)) mustBe List()
      }

      "there are multiple employments between nps and rti with matching works number and current pay id" in {
        val employment = rtiSampleEmployments.sample.get.copy(currentPayId = Some("1234"))
        val rtiData = RtiData(nino.nino,TaxYear(2016),"blerg",employments = List(employment))
        val income = testIncome(employment.officeRefNo.toInt, employment.payeRef)
        val taxAccount = TaxAccount(id = None, date = None, tax = 1564.45, incomes = List(income,income))

        sut(nps = Some(taxAccount), rti = Some(rtiData)) mustBe List(Employment(income, Some(employment)))
      }
    }
  }

  val rtiSampleEmployments = RtiGenerator.employment
  private val nino: Nino = new Generator(new Random).nextNino

  private val date = LocalDate.parse("2017-12-12")

  private val testNpsEmployment = NpsEmployment(
    employerName = Some("Company Plc"),
    isPrimary = true,
    sequenceNumber = 1,
    worksNumber = Some("1234"),
    districtNumber = 1,
    iabds = List(),
    cessationPay = Some(2200.22),
    start = date
  )

  private def testIncome(taxDistrict: Int, payeRef: String) = Income(
    employmentId = Some(1),
    isPrimary = true,
    incomeType = IncomeType.Employment,
    status = Status(Some(1), ceased = Some(date)),
    taxDistrict = Some(taxDistrict),
    payeRef = payeRef,
    name = "name",
    worksNumber = Some("1234"),
    taxCode="AB1234",
    potentialUnderpayment = 20.20,
    employmentRecord = Some(testNpsEmployment),
    basisOperation = Some(BasisOperation.Week1Month1)
  )

  def sut(nps: Option[TaxAccount] = None,
          rti: Option[RtiData] = None,
          rtiStatus: Option[RtiStatus] = None) = AnnualAccount(TaxYear(2016), nps, rti, rtiStatus).employments
}
