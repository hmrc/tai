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
import play.twirl.api.Html
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel

import scala.util.Random

class PensionProviderIFormSpec extends PlaySpec {

  "PensionProviderIForm" must {
    "display the correct static content of pension iform" when {
      "the html document is created" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())

        doc.title mustBe "What do you want to do?"
        doc.select("h1").text() mustBe "Tell us about income from employment or pension"
        doc.select("h2").text() mustBe "Internal Information"
        doc.select("h3:nth-of-type(1)").text() mustBe "Summary"
        doc.select("h3:nth-of-type(2)").text() mustBe "Tell us what tax year your query is about"
        doc.select("h3:nth-of-type(3)").text() mustBe "Your details"
        doc.select("h3:nth-of-type(4)").text() mustBe "What do you want to tell us?"
        doc.select("h3:nth-of-type(5)").text() mustBe "Pension details"
      }
    }

    "display the 'Tell us what tax year your query is about' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val tableRow = doc.select("table:nth-of-type(1) > tbody > tr")

        tableRow.select("td:nth-of-type(1)").text() mustBe "Which tax year is your query about?"
        tableRow.select("td:nth-of-type(2)").text() mustBe "6 April 2017 to 5 April 2018"
      }
    }

    "display the 'Your details' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val yourDetailsTable = doc.select("table:nth-of-type(2) > tbody")

        val nationalInsuranceNumberTableRow = yourDetailsTable.select("tr:nth-of-type(1)")
        nationalInsuranceNumberTableRow.select("td:nth-of-type(1)").text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.nino

        val firstNameTableRow = yourDetailsTable.select("tr:nth-of-type(2)")
        firstNameTableRow.select("td:nth-of-type(1)").text() mustBe "First name"
        firstNameTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.firstName

        val surnameTableRow = yourDetailsTable.select("tr:nth-of-type(3)")
        surnameTableRow.select("td:nth-of-type(1)").text() mustBe "Last name"
        surnameTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.lastName

        val dobTableRow = yourDetailsTable.select("tr:nth-of-type(4)")
        dobTableRow.select("td:nth-of-type(1)").text() mustBe "Date of birth"
        dobTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(5)")
        telephoneNumberQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow
          .select("td:nth-of-type(2)")
          .text() mustBe addPensionModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.select("tr:nth-of-type(6)")
        telephoneNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Telephone number"
        telephoneNumberTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(7)")
        ukAddressQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.select("td:nth-of-type(2)").text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.select("tr:nth-of-type(8)")
        ukAddressTableRow.select("td:nth-of-type(1)").text() mustBe "UK address"
        ukAddressTableRow.select("td:nth-of-type(2)").text() mustBe
          s"${addPensionModel.addressLine1} ${addPensionModel.addressLine2} " +
            s"${addPensionModel.addressLine3} ${addPensionModel.postcode}"
      }
    }

    "display the 'What do you want to tell us?' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(3) > tbody")

        val incorrectEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")
        incorrectEmploymentOrPensionSection
          .select("td:nth-of-type(1)")
          .text() mustBe "Do you have an employment or pension that is incorrect?"
        incorrectEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe addPensionModel.isUpdate

        val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")
        missingEmploymentOrPensionSection
          .select("td:nth-of-type(1)")
          .text() mustBe "Do you have an employment or pension that is missing?"
        missingEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe addPensionModel.isAdd

        val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")
        endedEmploymentOrPensionSection
          .select("td:nth-of-type(1)")
          .text() mustBe "Do you have an employment or pension that has ended?"
        endedEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe addPensionModel.isEnd

        val employmentOrPensionMissing = whatDoYouWantToTellUsSection.select("tr:nth-of-type(4)")
        employmentOrPensionMissing.select("td:nth-of-type(1)").text() mustBe "Is your employment or pension missing?"
        employmentOrPensionMissing.select("td:nth-of-type(2)").text() mustBe "My pension is missing"
      }
    }

    "display the 'Pension details' section of the pension iform" which {
      "contains pension name questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val yourPensionDetailsTable = doc.select("table:nth-of-type(4) > tbody")

        val pensionNameTableRow = yourPensionDetailsTable.select("tr:nth-of-type(1)")
        pensionNameTableRow.select("td:nth-of-type(1)").text() mustBe "Pension provider's name"
        pensionNameTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.employmentPensionName
      }

      "contains pension number questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val yourPensionDetailsTable = doc.select("table:nth-of-type(4) > tbody")

        val pensionNumberTableRow = yourPensionDetailsTable.select("tr:nth-of-type(2)")
        pensionNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Do you know your pension number?"
        pensionNumberTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.payrollNumber
      }

      "contains first date questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val yourPensionDetailsTable = doc.select("table:nth-of-type(4) > tbody")

        val pensionNameTableRow = yourPensionDetailsTable.select("tr:nth-of-type(3)")
        pensionNameTableRow.select("td:nth-of-type(1)").text() mustBe "First date you were paid"
        pensionNameTableRow.select("td:nth-of-type(2)").text() mustBe addPensionModel.startDate
      }

      "contains an add pension line" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString())
        val yourPensionDetailsTable = doc.select("table:nth-of-type(4) > tbody")
        val employmentOrPensionEndedQuestionTableRow = yourPensionDetailsTable.select("tr:nth-of-type(4)")

        employmentOrPensionEndedQuestionTableRow
          .select("td:nth-of-type(1)")
          .text() mustBe "Tell us what is incorrect and why:"
        employmentOrPensionEndedQuestionTableRow
          .select("td:nth-of-type(2)")
          .text() mustBe s"I want to add a pension provider for which I got first payment " +
          s"on ${addPensionModel.startDate}"
      }
    }
  }

  private val nino = new Generator(new Random()).nextNino

  private val addPensionModel = EmploymentPensionViewModel(
    "6 April 2017 to 5 April 2018",
    nino.nino,
    "firstname",
    "lastname",
    "3 April 1982",
    "Yes",
    "123456789",
    "address line 1",
    "address line 2",
    "address line 3",
    "postcode",
    "Yes",
    "No",
    "No",
    "pension name",
    "12345",
    "9 June 2017",
    "",
    ""
  )

  private def createSUT(viewModel: EmploymentPensionViewModel): Html =
    uk.gov.hmrc.tai.templates.html.PensionProviderIForm(viewModel)
}
