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

package uk.gov.hmrc.tai.model.domain

import org.mockito.MockitoSugar.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.crypto.{Decrypter, Encrypter}
import uk.gov.hmrc.tai.model.domain.AnnualAccount.annualAccountHodReads
import uk.gov.hmrc.tai.model.tai.TaxYear

import java.io.File
import java.time.LocalDate
import scala.io.BufferedSource

class AnnualAccountSpec extends PlaySpec with BeforeAndAfterEach {
  private val sutWithNoPayments =
    AnnualAccount(0, taxYear = TaxYear("2017"), realTimeStatus = Available, payments = Nil, endOfTaxYearUpdates = Nil)

  private val sutWithNoPayroll =
    AnnualAccount(1, taxYear = TaxYear("2017"), realTimeStatus = Available, payments = Nil, endOfTaxYearUpdates = Nil)

  private val sutWithOnePayment = AnnualAccount(
    2,
    taxYear = TaxYear("2017"),
    realTimeStatus = Available,
    payments = List(
      Payment(
        date = LocalDate.of(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = Monthly,
        duplicate = None
      )
    ),
    endOfTaxYearUpdates = Nil
  )

  private val sutWithMultiplePayments = sutWithOnePayment.copy(
    payments = sutWithOnePayment.payments :+
      Payment(
        date = LocalDate.of(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = Weekly,
        duplicate = None
      ) :+
      Payment(
        date = LocalDate.of(2017, 5, 26),
        amountYearToDate = 2000,
        taxAmountYearToDate = 1200,
        nationalInsuranceAmountYearToDate = 1500,
        amount = 200,
        taxAmount = 100,
        nationalInsuranceAmount = 150,
        payFrequency = FortNightly,
        duplicate = None
      )
  )

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentHodFormattersTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString(""))
    jsVal
  }

  private trait EncrypterDecrypter extends Encrypter with Decrypter
  private implicit val mockEncrypterDecrypter: EncrypterDecrypter = mock[EncrypterDecrypter]
  private val encryptedValueAsString: String = "encrypted"

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockEncrypterDecrypter)
  }

  "annualAccountHodReads" must {
    "return an AnnualAccount for each employment in the specified year" when {
      "there is one Payment" in {

        val payment = Payment(
          date = LocalDate.parse("2016-04-30"),
          amountYearToDate = 5000,
          taxAmountYearToDate = 1500,
          nationalInsuranceAmountYearToDate = 600,
          amount = 5000,
          taxAmount = 1500,
          nationalInsuranceAmount = 600,
          payFrequency = Quarterly,
          duplicate = None
        )

        val annualAccount =
          getJson("rtiSingleEmploymentSinglePayment").as[Seq[AnnualAccount]](annualAccountHodReads).head
        annualAccount mustBe AnnualAccount(39, TaxYear(2016), Available, Seq(payment), Nil)
      }

      "there is one Payment and one EndOfTaxYearUpdate" in {

        val payment = Payment(
          date = LocalDate.parse("2016-04-30"),
          amountYearToDate = 5000,
          taxAmountYearToDate = 1500,
          nationalInsuranceAmountYearToDate = 600,
          amount = 5000,
          taxAmount = 1500,
          nationalInsuranceAmount = 600,
          payFrequency = Monthly,
          duplicate = None
        )

        val eyu = EndOfTaxYearUpdate(
          date = LocalDate.parse("2016-06-17"),
          Seq(
            Adjustment(TaxAdjustment, -27.99),
            Adjustment(NationalInsuranceAdjustment, 12.3)
          )
        )

        val annualAccount =
          getJson("rtiSingleEmploymentSinglePaymentOneEyu").as[Seq[AnnualAccount]](annualAccountHodReads).head
        annualAccount mustBe AnnualAccount(39, TaxYear(2016), Available, Seq(payment), Seq(eyu))
      }
    }

    "cater for missing data in the json result" in {
      val annualAccounts = getJson("rtiWithMissingData").as[Seq[AnnualAccount]](annualAccountHodReads)
      annualAccounts.head.payments.head.nationalInsuranceAmount mustBe 0
      annualAccounts.head.payments.head.nationalInsuranceAmountYearToDate mustBe 0
    }

    "return payments in chronological order" in {
      val annualAccount =
        getJson("rtiSingleEmploymentMultiplePayments").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.payments.size mustBe 3
      annualAccount.payments.head.date mustBe LocalDate.parse("2016-04-30")
      annualAccount.payments(1).date mustBe LocalDate.parse("2016-06-09")
      annualAccount.payments(2).date mustBe LocalDate.parse("2017-06-09")
    }

    "return end of tax year updates in chronological order" in {
      val annualAccount =
        getJson("rtiSingleEmploymentSinglePaymentMutipleEyu").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.endOfTaxYearUpdates.size mustBe 3
      annualAccount.endOfTaxYearUpdates.head.date mustBe LocalDate.parse("2016-06-09")
      annualAccount.endOfTaxYearUpdates(1).date mustBe LocalDate.parse("2016-06-17")
      annualAccount.endOfTaxYearUpdates(2).date mustBe LocalDate.parse("2017-06-09")
    }

    "generate key and employerDesignation attributes with any leading zeroes removed from a numeric 'officeNo' (aka tax district number) field" in {
      val annualAccount = getJson("rtiLeadingZeroOfficeNo").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.sequenceNumber mustBe 39
    }

    "generate key and employerDesignation attributes incorporating non-numeric 'officeNo' (aka tax district number) field" in {
      val annualAccount = getJson("rtiNonNumericOfficeNo").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.sequenceNumber mustBe 39
    }
  }

  "AnnualAccount" must {
    "create an instance given correct input" in {
      val sequenceNumber = 0
      val realTimeStatus = Available
      val taxYear = TaxYear()

      AnnualAccount(sequenceNumber, taxYear, realTimeStatus) mustBe AnnualAccount(
        sequenceNumber,
        taxYear,
        realTimeStatus,
        Nil,
        Nil
      )
    }
  }

  "totalIncome" must {
    "return the latest year to date value from the payments" when {
      "there is only one payment" in {
        sutWithOnePayment.totalIncomeYearToDate mustBe 2000
      }
      "there are multiple payments" in {
        sutWithMultiplePayments.totalIncomeYearToDate mustBe 2000
      }
    }
    "return zero for the latest year to date value" when {
      "there are no payments" in {
        sutWithNoPayments.totalIncomeYearToDate mustBe 0
      }
    }

    "Check for sequenceNumber" when {
      "Employment has an employee payrollNumber present" in {

        val desig = sutWithNoPayments.sequenceNumber
        desig mustBe 0
      }
      "Employment has no employee payrollNumber" in {

        val desig = sutWithNoPayroll.sequenceNumber
        desig mustBe 1
      }
    }
  }

}
