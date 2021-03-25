/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.formatters

import java.io.File

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import play.api.libs.json._
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain.income.Live
import uk.gov.hmrc.tai.model.domain.{Payment, _}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.io.BufferedSource

class EmploymentHodFormattersSpec extends PlaySpec with EmploymentHodFormatters {

  val samplePayment: Payment = Payment(
    date = new LocalDate(2017, 5, 26),
    amountYearToDate = 2000,
    taxAmountYearToDate = 1200,
    nationalInsuranceAmountYearToDate = 300,
    amount = 200,
    taxAmount = 100,
    nationalInsuranceAmount = 150,
    payFrequency = Irregular,
    duplicate = None
  )

  val sampleEndOfTaxYearUpdate: EndOfTaxYearUpdate =
    EndOfTaxYearUpdate(new LocalDate(2016, 6, 4), Seq(Adjustment(TaxAdjustment, -20.99)))
  val sampleEndOfTaxYearUpdateMultipleAdjusts: EndOfTaxYearUpdate = EndOfTaxYearUpdate(
    new LocalDate(2016, 6, 4),
    Seq(
      Adjustment(TaxAdjustment, -20.99),
      Adjustment(IncomeAdjustment, -21.99),
      Adjustment(NationalInsuranceAdjustment, 44.2)))
  val sampleEndOfTaxYearUpdateTwoAdjusts: EndOfTaxYearUpdate = EndOfTaxYearUpdate(
    new LocalDate(2016, 6, 4),
    Seq(Adjustment(TaxAdjustment, -20.99), Adjustment(NationalInsuranceAdjustment, 44.2)))

  private def extractErrorsPerPath(exception: JsResultException): Seq[String] =
    for {
      (path: JsPath, errors: Seq[JsonValidationError]) <- exception.errors
      error: JsonValidationError                       <- errors
      message: String                                  <- error.messages
    } yield {
      path.toString() + " -> " + message
    }

  val sampleSingleEmployment = List(
    Employment(
      "EMPLOYER1",
      Live,
      Some("0000"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = false))
  val sampleDualEmployment = List(
    Employment(
      "EMPLOYER1",
      Live,
      Some("0000"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      None,
      hasPayrolledBenefit = true,
      receivingOccupationalPension = false),
    Employment(
      "EMPLOYER2",
      Live,
      Some("0000"),
      new LocalDate(2016, 4, 6),
      None,
      Nil,
      "000",
      "00000",
      2,
      Some(100),
      hasPayrolledBenefit = false,
      receivingOccupationalPension = false)
  )

  private def getJson(fileName: String): JsValue = {
    val jsonFilePath = "test/resources/data/EmploymentHodFormattersTesting/" + fileName + ".json"
    val file: File = new File(jsonFilePath)
    val source: BufferedSource = scala.io.Source.fromFile(file)
    val jsVal = Json.parse(source.mkString(""))
    jsVal
  }

  "numberChecked" should {

    "return a new string with leading zeros removed, when supplied with a numeric string" in {
      numberChecked("012") mustBe "12"
      numberChecked("00000100002355") mustBe "100002355"
      numberChecked("00001") mustBe "1"
      numberChecked("000010") mustBe "10"
    }

    "return non numeric strings unchanged" in {
      numberChecked("0000012B") mustBe "0000012B"
      numberChecked("A123") mustBe "A123"
      numberChecked("01B12") mustBe "01B12"
      numberChecked("0012-32") mustBe "0012-32"
    }
  }

  "employmentHodReads" should {
    "un-marshall employment json" when {
      "reading single employment from Hod" in {
        val employment = getJson("npsSingleEmployment").as[EmploymentCollection]

        employment.employments mustBe sampleSingleEmployment
      }
      "reading multiple employments from Hod" in {
        val employment = getJson("npsDualEmployment").as[EmploymentCollection]

        employment.employments mustBe sampleDualEmployment
      }
    }

    "Remove any leading zeroes from a numeric 'taxDistrictNumber' field" in {
      val employment = getJson("npsLeadingZeroTaxDistrictNumber").as[EmploymentCollection]
      employment.employments.head.taxDistrictNumber mustBe "000"
      employment.employments.head.employerDesignation mustBe "000-00000"
      employment.employments.head.key mustBe "000-00000-0000"
    }

    "Correctly handle a non numeric 'taxDistrictNumber' field" in {
      val employment = getJson("npsNonNumericTaxDistrictNumber").as[EmploymentCollection]
      employment.employments.head.taxDistrictNumber mustBe "000"
      employment.employments.head.employerDesignation mustBe "000-00000"
      employment.employments.head.key mustBe "000-00000-0000"
    }
  }

  "annualAccountHodReads" must {
    "return an AnnualAccount for each employment in the specified year" when {
      "there is one Payment" in {

        val payment = Payment(
          date = new LocalDate("2016-04-30"),
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
        annualAccount mustBe AnnualAccount("0000-0000-0000", TaxYear(2016), Available, Seq(payment), Nil)
      }

      "there is one Payment and one EndOfTaxYearUpdate" in {

        val payment = Payment(
          date = new LocalDate("2016-04-30"),
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
          date = new LocalDate("2016-06-17"),
          Seq(
            Adjustment(TaxAdjustment, -27.99),
            Adjustment(NationalInsuranceAdjustment, 12.3)
          )
        )

        val annualAccount =
          getJson("rtiSingleEmploymentSinglePaymentOneEyu").as[Seq[AnnualAccount]](annualAccountHodReads).head
        annualAccount mustBe AnnualAccount("0000-0000-0000", TaxYear(2016), Available, Seq(payment), Seq(eyu))
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
      annualAccount.payments.head.date mustBe new LocalDate("2016-04-30")
      annualAccount.payments(1).date mustBe new LocalDate("2016-06-09")
      annualAccount.payments(2).date mustBe new LocalDate("2017-06-09")
    }

    "return end of tax year updates in chronological order" in {
      val annualAccount =
        getJson("rtiSingleEmploymentSinglePaymentMutipleEyu").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.endOfTaxYearUpdates.size mustBe 3
      annualAccount.endOfTaxYearUpdates.head.date mustBe new LocalDate("2016-06-09")
      annualAccount.endOfTaxYearUpdates(1).date mustBe new LocalDate("2016-06-17")
      annualAccount.endOfTaxYearUpdates(2).date mustBe new LocalDate("2017-06-09")
    }

    "generate key and employerDesignation attributes with any leading zeroes removed from a numeric 'officeNo' (aka tax district number) field" in {
      val annualAccount = getJson("rtiLeadingZeroOfficeNo").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.employerDesignation mustBe "0000-0000"
      annualAccount.key mustBe "0000-0000-0000"
    }

    "generate key and employerDesignation attributes incorporating non-numeric 'officeNo' (aka tax district number) field" in {
      val annualAccount = getJson("rtiNonNumericOfficeNo").as[Seq[AnnualAccount]](annualAccountHodReads).head
      annualAccount.employerDesignation mustBe "0000-0000"
      annualAccount.key mustBe "0000-0000-0000"
    }
  }

  "Payment reads" should {

    "read nps json and convert it to payment object" in {
      val parsedJson: Payment = getJson("rtiInYearFragment").as[Payment](paymentHodReads)
      parsedJson mustBe samplePayment
    }

    "throw an error" when {
      "a field key is wrong" in {

        val incorrectJson = Json.obj(
          "aaa"                               -> "2017-05-26",
          "amountYearToDate"                  -> 2000,
          "taxAmountYearToDate"               -> 1200,
          "nationalInsuranceAmountYearToDate" -> 1500,
          "amount"                            -> 200,
          "taxAmount"                         -> 100,
          "nationalInsuranceAmount"           -> 150
        )

        an[JsResultException] mustBe thrownBy(incorrectJson.as[Payment](paymentHodReads))
      }

      "a key is wrong" in {
        val incorrectJson =
          Json.obj(
            "date"                              -> "aaa",
            "amountYearToDate"                  -> 2000,
            "taxAmountYearToDate"               -> 1200,
            "nationalInsuranceAmountYearToDate" -> 1500,
            "amount"                            -> 200,
            "taxAmount"                         -> 100,
            "nationalInsuranceAmount"           -> 150
          )

        an[JsResultException] mustBe thrownBy(incorrectJson.as[Payment](paymentHodReads))
      }
    }
  }

  "EndOfTaxYearUpdate reads" should {

    "read rti json with a single adjustment, and convert it to TotalTaxDelta adjustment object" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentSingleAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdate
    }

    "read rti json with multip[le adjustments convert adjustment objects" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentMultipleAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdateMultipleAdjusts
    }

    "read rti json with multip[le adjustments and ignore those of zero value" in {
      val result: EndOfTaxYearUpdate =
        getJson("rtiEyuFragmentZeroAdjust").as[EndOfTaxYearUpdate](endOfTaxYearUpdateHodReads)
      result mustBe sampleEndOfTaxYearUpdateTwoAdjusts
    }
  }

  "taxYearFormatter" should {

    "Format a valid tax year string from rti" in {
      val rtiTaxYearJsVal = JsString("16-17")
      rtiTaxYearJsVal.as[TaxYear](taxYearHodReads) mustBe TaxYear(2016)
    }

    "Thrwo an error when encountering an invalid tax year string" in {
      val rtiTaxYearJsVal = JsString("16-")
      an[IllegalArgumentException] mustBe thrownBy(rtiTaxYearJsVal.as[TaxYear](taxYearHodReads) mustBe TaxYear(2016))
    }
  }
}
