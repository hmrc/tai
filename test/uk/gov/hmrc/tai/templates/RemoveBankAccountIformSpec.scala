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

package uk.gov.hmrc.tai.templates

import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.{Address, BankAccount, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.CloseBankAccount

import scala.util.Random

class RemoveBankAccountIformSpec extends PlaySpec {

  "Remove Bank Account Iform" must {
    "display Logo" in {
      val sut = removeBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)

      doc.select(".organisation-logo").text() mustBe "HM Revenue & Customs"
      doc.select(".heading-large").get(1).text() mustBe "Tell us about UK bank and building society interest"
    }

    "display the what do you want to tell us section" in {
      val sut = removeBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)
      val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(3) > tbody")

      doc.select("h3").get(3).text() mustBe "What do you want to tell us?"

      val bankAndBuildingIncorrect = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")

      bankAndBuildingIncorrect
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that is incorrect?"
      bankAndBuildingIncorrect.select("td:nth-of-type(2)").text() mustBe "No"

      val bankAndBuildingMissing = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")

      bankAndBuildingMissing
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that is missing?"
      bankAndBuildingMissing.select("td:nth-of-type(2)").text() mustBe "No"

      val bankAndBuildingEnded = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")

      bankAndBuildingEnded
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that has ended?"
      bankAndBuildingEnded.select("td:nth-of-type(2)").text() mustBe "Yes"
    }

    "display ended section" in {
      val sut = removeBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection.select("tr:nth-of-type(1) td:nth-of-type(1)").text() mustBe "UK bank or building society name"
      endSection.select("tr:nth-of-type(1) td:nth-of-type(2)").text() mustBe closeBankAccount.bankAccount.bankName.get
      endSection.select("tr:nth-of-type(2) td:nth-of-type(1)").text() mustBe "Account holder's name"
      endSection.select("tr:nth-of-type(2) td:nth-of-type(2)").text() mustBe closeBankAccount.personDetails.firstName
      endSection.select("tr:nth-of-type(3) td:nth-of-type(1)").text() mustBe "Sort code"
      endSection.select("tr:nth-of-type(3) td:nth-of-type(2)").text() mustBe closeBankAccount.bankAccount.sortCode.get
      endSection
        .select("tr:nth-of-type(4) td:nth-of-type(1)")
        .text() mustBe "Account number of the UK bank or building society"
      endSection
        .select("tr:nth-of-type(4) td:nth-of-type(2)")
        .text() mustBe closeBankAccount.bankAccount.accountNumber.get
      endSection.select("tr:nth-of-type(5) td:nth-of-type(1)").text() mustBe "Is this a joint account?"
      endSection.select("tr:nth-of-type(5) td:nth-of-type(2)").text() mustBe "No"
      endSection.select("tr:nth-of-type(6) td:nth-of-type(1)").text() mustBe "Date you closed the account"
      endSection.select("tr:nth-of-type(6) td:nth-of-type(2)").text() mustBe closeBankAccount.endDate.toString(
        "d MMMM yyyy")
    }

    "display ended section with interest supplied and within current tax year" in {

      val closeBankAccount =
        CloseBankAccount(personDetails, TaxYear(), bankAccount, startOfTaxYearCloseDate, Some(123.45))

      val sut = uk.gov.hmrc.tai.templates.html.RemoveBankAccountIform(closeBankAccount)
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection
        .select("tr:nth-of-type(7) td:nth-of-type(1)")
        .text() mustBe s"Interest earned since 6 April ${TaxYear().year}"
      endSection.select("tr:nth-of-type(7) td:nth-of-type(2)").text() mustBe "£123.45"
    }

    "display ended section with interest not supplied and within current tax year" in {

      val closeBankAccount = CloseBankAccount(personDetails, TaxYear(), bankAccount, startOfTaxYearCloseDate, None)

      val sut = uk.gov.hmrc.tai.templates.html.RemoveBankAccountIform(closeBankAccount)
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection
        .select("tr:nth-of-type(7) td:nth-of-type(1)")
        .text() mustBe s"Interest earned since 6 April ${TaxYear().year}"
      endSection.select("tr:nth-of-type(7) td:nth-of-type(2)").text() mustBe "I do not know"
    }

    "display ended section with interest not supplied and before current tax year" in {

      val closeBankAccount =
        CloseBankAccount(personDetails, TaxYear(), bankAccount, startOfTaxYearCloseDate.minusDays(1), None)

      val sut = uk.gov.hmrc.tai.templates.html.RemoveBankAccountIform(closeBankAccount)
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection.select("tr").size() mustBe 6

      endSection.select("tr:nth-of-type(7) td:nth-of-type(1)").text() mustBe ""
      endSection.select("tr:nth-of-type(7) td:nth-of-type(2)").text() mustBe ""
    }

    "display ended section with interest not supplied and after current tax year" in {

      val closeBankAccount = CloseBankAccount(personDetails, TaxYear(), bankAccount, TaxYear().end, None)

      val sut = uk.gov.hmrc.tai.templates.html.RemoveBankAccountIform(closeBankAccount)
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection
        .select("tr:nth-of-type(7) td:nth-of-type(1)")
        .text() mustBe s"Interest earned since 6 April ${TaxYear().year}"
      endSection.select("tr:nth-of-type(7) td:nth-of-type(2)").text() mustBe "I do not know"
    }
  }
  private val nino: Nino = new Generator(new Random).nextNino
  private val dateOfBirth = LocalDate.parse("2017-02-01")

  private val personDetails = Person(
    Nino(nino.nino),
    "test",
    "tester",
    Some(dateOfBirth),
    Address("line1", "line2", "line3", "postcode", "country"))

  private val startOfTaxYearCloseDate = TaxYear().start

  private val bankAccount = BankAccount(1, Some("123456789"), Some("123456"), Some("TEST"), 10, Some("Source"), Some(0))
  private val closeBankAccount = CloseBankAccount(personDetails, TaxYear(), bankAccount, startOfTaxYearCloseDate, None)
  private def removeBankAccountTemplate(viewModel: CloseBankAccount = closeBankAccount) =
    uk.gov.hmrc.tai.templates.html.RemoveBankAccountIform(viewModel)
}
