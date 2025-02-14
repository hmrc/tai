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

class EmploymentIFormSpec extends PlaySpec {

  "EmploymentIForm" must {

    "display the correct static content of employment iform" when {

      "the html document is created" in {

        val sut = createSUT(endEmploymentViewModel)
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
        doc.getElementsByAttributeValue("font-size", "11px").get(4).text() mustBe "Employer details"
      }
    }

    "display the 'Tell us what tax year your query is about' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in {

        val sut = createSUT(endEmploymentViewModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)

        val tableRow = doc.getElementsByTag("fo:table").first()
        tableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Which tax year is your query about?"
        tableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe "2017-2018"
      }
    }

    "display the 'Your details' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in {

        val sut = createSUT(endEmploymentViewModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourDetailsTable = doc.getElementsByTag("fo:table").get(1)

        val nationalInsuranceNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(0)

        nationalInsuranceNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.nino

        val firstNameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(1)

        firstNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "First name"
        firstNameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe endEmploymentViewModel.firstName

        val surnameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(2)

        surnameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Last name"
        surnameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe endEmploymentViewModel.lastName

        val dobTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(3)

        dobTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Date of birth"
        dobTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe endEmploymentViewModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(4)

        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(5)

        telephoneNumberTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Telephone number"
        telephoneNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(6)

        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(7)

        ukAddressTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "UK address"
        ukAddressTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe
          s"${endEmploymentViewModel.addressLine1} ${endEmploymentViewModel.addressLine2} " +
          s"${endEmploymentViewModel.addressLine3} ${endEmploymentViewModel.postcode}"
      }
    }

    "display the 'What do you want to tell us?' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in {

        val sut = createSUT(endEmploymentViewModel)
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
          .text() mustBe endEmploymentViewModel.isUpdate

        val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(1)

        missingEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have an employment or pension that is missing?"
        missingEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.isAdd

        val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(2)

        endedEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have an employment or pension that has ended?"
        endedEmploymentOrPensionSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.isEnd
      }
    }

    "display the 'Employer details' section of the employment iform" which {
      "contains employer name and payroll number questions" in {

        val sut = createSUT(endEmploymentViewModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
        val yourEmployerDetailsTable = doc.getElementsByTag("fo:table").get(3)

        val employerNameTableRow = yourEmployerDetailsTable.getElementsByTag("fo:table-row").get(0)

        employerNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Employer name"
        employerNameTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.employmentPensionName

        val payrollNumberTableRow = yourEmployerDetailsTable.getElementsByTag("fo:table-row").get(1)

        payrollNumberTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Do you know your payroll number?"
        payrollNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe endEmploymentViewModel.payrollNumber
      }

      "contains an end employment line" when {
        "employmentPensionViewModel is provided with end employment" in {

          val sut = createSUT(endEmploymentViewModel)
          val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
          val yourEmployerDetailsTable = doc.getElementsByTag("fo:table").get(3)

          val employmentOrPensionEndedQuestionTableRow =
            yourEmployerDetailsTable.getElementsByTag("fo:table-row").get(2)

          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(0)
            .text() mustBe "Has your employment or pension ended?"
          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(1)
            .text() mustBe s"My employment has ended on ${endEmploymentViewModel.endDate}"
        }
      }

      "contains an add employment line" when {
        "employmentPensionViewModel is provided with add employment" in {

          val sut = createSUT(addEmploymentViewModel)
          val doc = Jsoup.parse(sut.toString())

          val yourEmployerDetailsTable = doc.getElementsByTag("fo:table").get(3)

          val employmentOrPensionEndedQuestionTableRow =
            yourEmployerDetailsTable.getElementsByTag("fo:table-row").get(2)

          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(0)
            .text() mustBe "Tell us what is incorrect and why:"
          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(1)
            .text() mustBe s"I want to add a missing " +
            s"employment which started on ${addEmploymentViewModel.startDate}"
        }
      }

      "contains what you told us" when {
        "incorrect view model is provided" in {
          val sut = createSUT(incorrectEmploymentViewModel)
          val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)
          val yourEmployerDetailsTable = doc.getElementsByTag("fo:table").get(3)

          val employmentOrPensionEndedQuestionTableRow =
            yourEmployerDetailsTable.getElementsByTag("fo:table-row").get(2)

          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(0)
            .text() mustBe "Tell us what is incorrect and why:"
          employmentOrPensionEndedQuestionTableRow
            .getElementsByTag("fo:table-cell")
            .get(1)
            .text() mustBe incorrectEmploymentViewModel.whatYouToldUs
        }
      }

      "displays YES on update" when {
        "incorrect view model is provided" in {
          val sut = createSUT(incorrectEmploymentViewModel)
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
            .text() mustBe incorrectEmploymentViewModel.isUpdate

          val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(1)

          missingEmploymentOrPensionSection
            .getElementsByTag("fo:table-cell")
            .get(0)
            .text() mustBe "Do you have an employment or pension that is missing?"
          missingEmploymentOrPensionSection
            .getElementsByTag("fo:table-cell")
            .get(1)
            .text() mustBe incorrectEmploymentViewModel.isAdd

          val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(2)

          endedEmploymentOrPensionSection
            .getElementsByTag("fo:table-cell")
            .get(0)
            .text() mustBe "Do you have an employment or pension that has ended?"
          endedEmploymentOrPensionSection
            .getElementsByTag("fo:table-cell")
            .get(1)
            .text() mustBe incorrectEmploymentViewModel.isEnd
        }
      }
    }
  }

  private val nino = new Generator(new Random()).nextNino

  private val endEmploymentViewModel: EmploymentPensionViewModel =
    EmploymentPensionViewModel(
      "2017-2018",
      nino.nino,
      "firstname",
      "lastname",
      "1982-04-03",
      "Yes",
      "123456789",
      "address line 1",
      "address line 2",
      "address line 3",
      "postcode",
      "No",
      "No",
      "Yes",
      "employerName",
      "12345",
      "",
      "2018-12-12",
      ""
    )

  private val addEmploymentViewModel: EmploymentPensionViewModel =
    EmploymentPensionViewModel(
      "2017-2018",
      nino.nino,
      "firstname",
      "lastname",
      "1982-04-03",
      "Yes",
      "123456789",
      "address line 1",
      "address line 2",
      "address line 3",
      "postcode",
      "Yes",
      "No",
      "No",
      "employerName",
      "12345",
      "2017-12-12",
      "",
      ""
    )

  private val incorrectEmploymentViewModel: EmploymentPensionViewModel =
    EmploymentPensionViewModel(
      "2017-2018",
      nino.nino,
      "firstname",
      "lastname",
      "1982-04-03",
      "Yes",
      "123456789",
      "address line 1",
      "address line 2",
      "address line 3",
      "postcode",
      "No",
      "Yes",
      "No",
      "employerName",
      "12345",
      "2017-12-12",
      "",
      "WHAT YOU TOLD US"
    )

  private def createSUT(viewModel: EmploymentPensionViewModel) =
    uk.gov.hmrc.tai.templates.xml.EmploymentIForm(viewModel)
}
