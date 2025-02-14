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
import org.jsoup.parser.Parser
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel

import scala.util.Random

class PensionProviderIFormSpec extends PlaySpec {

  "PensionProviderIForm" must {
    "display the correct static content of pension iform" when {
      "the html document is created" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)

        doc
          .getElementsByAttributeValue("font-size", "14px")
          .text() mustBe "Tell us about income from employment or pension"
        doc.getElementsByAttributeValue("font-size", "13px").text() mustBe "Internal Information"
        doc.getElementsByAttributeValue("font-size", "12px").text() mustBe "Summary"
        doc
          .getElementsByAttributeValue("font-size", "11px")
          .get(1)
          .text() mustBe "Tell us what tax year your query is about"
        doc.getElementsByAttributeValue("font-size", "11px").get(2).text() mustBe "Your details"
        doc.getElementsByAttributeValue("font-size", "11px").get(3).text() mustBe "What do you want to tell us?"
        doc.getElementsByAttributeValue("font-size", "11px").get(4).text() mustBe "Pension details"
      }
    }

    "display the 'Tell us what tax year your query is about' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val tableRow = doc.getElementsByTag("fo:table").first()

        tableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Which tax year is your query about?"
        tableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe "6 April 2017 to 5 April 2018"
      }
    }

    "display the 'Your details' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourDetailsTable = doc.getElementsByTag("fo:table").get(1)

        val nationalInsuranceNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(0)
        nationalInsuranceNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.nino

        val firstNameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(1)
        firstNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "First name"
        firstNameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.firstName

        val surnameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(2)
        surnameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Last name"
        surnameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.lastName

        val dobTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(3)
        dobTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Date of birth"
        dobTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(4)
        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe addPensionModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(5)
        telephoneNumberTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Telephone number"
        telephoneNumberTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(6)
        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(7)
        ukAddressTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "UK address"
        ukAddressTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe
          s"${addPensionModel.addressLine1} ${addPensionModel.addressLine2} " +
          s"${addPensionModel.addressLine3} ${addPensionModel.postcode}"
      }
    }

    "display the 'What do you want to tell us?' section of the pension iform" when {
      "an employmentPensionViewModel is provided" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val whatDoYouWantToTellUsSection = doc.getElementsByTag("fo:table").get(2)

        val incorrectEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(0)
        incorrectEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have an employment or pension that is incorrect?"
        incorrectEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe addPensionModel.isUpdate

        val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(1)
        missingEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have an employment or pension that is missing?"
        missingEmploymentOrPensionSection.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.isAdd

        val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(2)
        endedEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have an employment or pension that has ended?"
        endedEmploymentOrPensionSection.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.isEnd

        val employmentOrPensionMissing = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(3)
        employmentOrPensionMissing
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Is your employment or pension missing?"
        employmentOrPensionMissing.getElementsByTag("fo:table-cell").get(1).text() mustBe "My pension is missing"
      }
    }

    "display the 'Pension details' section of the pension iform" which {
      "contains pension name questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourPensionDetailsTable = doc.getElementsByTag("fo:table").get(3)

        val pensionNameTableRow = yourPensionDetailsTable.getElementsByTag("fo:table-row").get(0)
        pensionNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Pension provider's name"
        pensionNameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.employmentPensionName
      }

      "contains pension number questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourPensionDetailsTable = doc.getElementsByTag("fo:table").get(3)

        val pensionNumberTableRow = yourPensionDetailsTable.getElementsByTag("fo:table-row").get(1)
        pensionNumberTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Do you know your pension number?"
        pensionNumberTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.payrollNumber
      }

      "contains first date questions" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourPensionDetailsTable = doc.getElementsByTag("fo:table").get(3)

        val pensionNameTableRow = yourPensionDetailsTable.getElementsByTag("fo:table-row").get(2)
        pensionNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "First date you were paid"
        pensionNameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe addPensionModel.startDate
      }

      "contains an add pension line" in {
        val sut = createSUT(addPensionModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourPensionDetailsTable = doc.getElementsByTag("fo:table").get(3)

        val employmentOrPensionEndedQuestionTableRow = yourPensionDetailsTable.getElementsByTag("fo:table-row").get(3)
        employmentOrPensionEndedQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Tell us what is incorrect and why:"
        employmentOrPensionEndedQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
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

  private def createSUT(viewModel: EmploymentPensionViewModel) =
    uk.gov.hmrc.tai.templates.xml.PensionProviderIForm(viewModel)
}
