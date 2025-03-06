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

package uk.gov.hmrc.tai.templates

import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.{Address, BankAccount, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.IncorrectBankAccount

import java.time.LocalDate
import scala.util.Random

class IncorrectBankAccountIformSpec extends PlaySpec {
  private val nino: Nino = new Generator(new Random).nextNino
  private val dateOfBirth = LocalDate.parse("2017-02-01")

  private val personDetails = Person(
    Nino(nino.nino),
    "test",
    "tester",
    Some(dateOfBirth),
    Address("line1", "line2", "line3", "postcode", "country")
  )
  private val bankAccount = BankAccount(1, Some("123456789"), Some("123456"), Some("TEST"), 10, Some("Source"), Some(0))
  private val incorrectBankAccount = IncorrectBankAccount(personDetails, TaxYear(), bankAccount)

  private def incorrectBankAccountTemplate(viewModel: IncorrectBankAccount = incorrectBankAccount) =
    uk.gov.hmrc.tai.templates.html.IncorrectBankAccountIform(viewModel)

  "Incorrect Bank Account Iform" must {
    "display Logo" in {
      val sut = incorrectBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)

      doc.select(".organisation-logo").text() mustBe "HM Revenue & Customs"
      doc.select(".heading-large").get(1).text() mustBe "Tell us about UK bank and building society interest"
    }

    "display the what do you want to tell us section" in {
      val sut = incorrectBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)
      val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(3) > tbody")

      doc.select("h3").get(3).text() mustBe "What do you want to tell us?"

      val bankAndBuildingIncorrect = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")

      bankAndBuildingIncorrect
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that is incorrect?"
      bankAndBuildingIncorrect.select("td:nth-of-type(2)").text() mustBe "Yes"

      val bankAndBuildingMissing = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")

      bankAndBuildingMissing
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that is missing?"
      bankAndBuildingMissing.select("td:nth-of-type(2)").text() mustBe "No"

      val bankAndBuildingEnded = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")

      bankAndBuildingEnded
        .select("td:nth-of-type(1)")
        .text() mustBe "Do you have UK bank or building society account interest that has ended?"
      bankAndBuildingEnded.select("td:nth-of-type(2)").text() mustBe "No"
    }

    "display ended section" in {
      val sut = incorrectBankAccountTemplate()
      val doc = Jsoup.parse(sut.toString)

      doc.select("h3").get(4).text() mustBe "Incorrect UK bank or building society interest"
      val endSection = doc.select("table:nth-of-type(4) > tbody")

      endSection.select("tr:nth-of-type(1) td:nth-of-type(1)").text() mustBe "UK bank or building society name"
      endSection
        .select("tr:nth-of-type(1) td:nth-of-type(2)")
        .text() mustBe incorrectBankAccount.bankAccount.bankName.get
      endSection.select("tr:nth-of-type(2) td:nth-of-type(1)").text() mustBe "Account holder's name"
      endSection
        .select("tr:nth-of-type(2) td:nth-of-type(2)")
        .text() mustBe incorrectBankAccount.personDetails.firstName
      endSection.select("tr:nth-of-type(3) td:nth-of-type(1)").text() mustBe "Sort code"
      endSection
        .select("tr:nth-of-type(3) td:nth-of-type(2)")
        .text() mustBe incorrectBankAccount.bankAccount.sortCode.get
      endSection
        .select("tr:nth-of-type(4) td:nth-of-type(1)")
        .text() mustBe "Account number of the UK bank or building society"
      endSection
        .select("tr:nth-of-type(4) td:nth-of-type(2)")
        .text() mustBe incorrectBankAccount.bankAccount.accountNumber.get
      endSection.select("tr:nth-of-type(5) td:nth-of-type(1)").text() mustBe "Is this a joint account?"
      endSection.select("tr:nth-of-type(5) td:nth-of-type(2)").text() mustBe "No"
      endSection.select("tr:nth-of-type(6) td:nth-of-type(1)").text() mustBe "Tell us what is incorrect and why"
      endSection.select("tr:nth-of-type(6) td:nth-of-type(2)").text() mustBe "I never had this account"

    }

    "display correct amount section" in {
      val sut = incorrectBankAccountTemplate(IncorrectBankAccount(personDetails, TaxYear(), bankAccount, Some(1000.12)))
      val doc = Jsoup.parse(sut.toString)

      val endSection = doc.select("table:nth-of-type(4) > tbody")
      endSection.select("tr:nth-of-type(6) td:nth-of-type(1)").text() mustBe "Correct amount of gross interest"
      endSection.select("tr:nth-of-type(6) td:nth-of-type(2)").text() mustBe "1000.12"
      endSection.select("tr:nth-of-type(7) td:nth-of-type(1)").text() mustBe "Tell us what is incorrect and why"
      endSection.select("tr:nth-of-type(7) td:nth-of-type(2)").text() mustBe "My gross interest is wrong"
    }
  }
}
