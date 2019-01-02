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

package uk.gov.hmrc.tai.templates

import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import play.twirl.api.Html
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel

import scala.util.Random

class EmploymentIFormSpec extends PlaySpec {

  "EmploymentIForm" must {

    "display the correct static content of employment iform" when {

      "the html document is created" in {

        val sut = createSUT(endEmploymentViewModel)

        val doc = Jsoup.parse(sut.toString())

        doc.title mustBe "What do you want to do?"
        doc.select("h1").text() mustBe "Tell us about income from employment or pension"
        doc.select("h2").text() mustBe "Internal Information"
        doc.select("h3:nth-of-type(1)").text() mustBe "Summary"
        doc.select("h3:nth-of-type(2)").text() mustBe "Tell us what tax year your query is about"
        doc.select("h3:nth-of-type(3)").text() mustBe "Your details"
        doc.select("h3:nth-of-type(4)").text() mustBe "What do you want to tell us?"
        doc.select("h3:nth-of-type(5)").text() mustBe "Employer details"
      }
    }

    "display the 'Tell us what tax year your query is about' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in {

        val sut = createSUT(endEmploymentViewModel)

        val doc = Jsoup.parse(sut.toString())

        val tableRow = doc.select("table:nth-of-type(1) > tbody > tr")
        tableRow.select("td:nth-of-type(1)").text() mustBe "Which tax year is your query about?"
        tableRow.select("td:nth-of-type(2)").text() mustBe "2017-2018"
      }
    }

    "display the 'Your details' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in {

        val sut = createSUT(endEmploymentViewModel)

        val doc = Jsoup.parse(sut.toString())

        val yourDetailsTable = doc.select("table:nth-of-type(2) > tbody")

        val nationalInsuranceNumberTableRow = yourDetailsTable.select("tr:nth-of-type(1)")

        nationalInsuranceNumberTableRow.select("td:nth-of-type(1)").text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.nino

        val firstNameTableRow = yourDetailsTable.select("tr:nth-of-type(2)")

        firstNameTableRow.select("td:nth-of-type(1)").text() mustBe "First name"
        firstNameTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.firstName

        val surnameTableRow = yourDetailsTable.select("tr:nth-of-type(3)")

        surnameTableRow.select("td:nth-of-type(1)").text() mustBe "Last name"
        surnameTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.lastName

        val dobTableRow = yourDetailsTable.select("tr:nth-of-type(4)")

        dobTableRow.select("td:nth-of-type(1)").text() mustBe "Date of birth"
        dobTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(5)")

        telephoneNumberQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.select("tr:nth-of-type(6)")

        telephoneNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Telephone number"
        telephoneNumberTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(7)")

        ukAddressQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.select("td:nth-of-type(2)").text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.select("tr:nth-of-type(8)")

        ukAddressTableRow.select("td:nth-of-type(1)").text() mustBe "UK address"
        ukAddressTableRow.select("td:nth-of-type(2)").text() mustBe
          s"${endEmploymentViewModel.addressLine1} ${endEmploymentViewModel.addressLine2} " +
            s"${endEmploymentViewModel.addressLine3} ${endEmploymentViewModel.postcode}"
      }
    }

    "display the 'What do you want to tell us?' section of the employment iform" when {
      "an employmentPensionViewModel is provided" in{

        val sut = createSUT(endEmploymentViewModel)

        val doc = Jsoup.parse(sut.toString())

        val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(3) > tbody")

        val incorrectEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")

        incorrectEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that is incorrect?"
        incorrectEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.isUpdate

        val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")

        missingEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that is missing?"
        missingEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.isAdd

        val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")

        endedEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that has ended?"
        endedEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.isEnd
      }
    }

    "display the 'Employer details' section of the employment iform" which {
      "contains employer name and payroll number questions" in{

        val sut = createSUT(endEmploymentViewModel)

        val doc = Jsoup.parse(sut.toString())

        val yourEmployerDetailsTable = doc.select("table:nth-of-type(4) > tbody")

        val employerNameTableRow = yourEmployerDetailsTable.select("tr:nth-of-type(1)")

        employerNameTableRow.select("td:nth-of-type(1)").text() mustBe "Employer name"
        employerNameTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.employmentPensionName

        val payrollNumberTableRow = yourEmployerDetailsTable.select("tr:nth-of-type(2)")

        payrollNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Do you know your payroll number?"
        payrollNumberTableRow.select("td:nth-of-type(2)").text() mustBe endEmploymentViewModel.payrollNumber
      }

      "contains an end employment line" when {
        "employmentPensionViewModel is provided with end employment" in {

          val sut = createSUT(endEmploymentViewModel)

          val doc = Jsoup.parse(sut.toString())

          val yourEmployerDetailsTable = doc.select("table:nth-of-type(4) > tbody")

          val employmentOrPensionEndedQuestionTableRow = yourEmployerDetailsTable.select("tr:nth-of-type(3)")

          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Has your employment or pension ended?"
          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(2)").text() mustBe s"My employment has ended on ${endEmploymentViewModel.endDate}"
        }
      }

      "contains an add employment line" when {
        "employmentPensionViewModel is provided with add employment" in {

          val sut = createSUT(addEmploymentViewModel)

          val doc = Jsoup.parse(sut.toString())

          val yourEmployerDetailsTable = doc.select("table:nth-of-type(4) > tbody")

          val employmentOrPensionEndedQuestionTableRow = yourEmployerDetailsTable.select("tr:nth-of-type(3)")

          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Tell us what is incorrect and why:"
          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(2)").text() mustBe s"I want to add a missing " +
            s"employment which started on ${addEmploymentViewModel.startDate}"
        }
      }

      "contains what you told us" when {
        "incorrect view model is provided" in {
          val sut = createSUT(incorrectEmploymentViewModel)

          val doc = Jsoup.parse(sut.toString())

          val yourEmployerDetailsTable = doc.select("table:nth-of-type(4) > tbody")

          val employmentOrPensionEndedQuestionTableRow = yourEmployerDetailsTable.select("tr:nth-of-type(3)")

          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Tell us what is incorrect and why:"
          employmentOrPensionEndedQuestionTableRow.select("td:nth-of-type(2)").text() mustBe incorrectEmploymentViewModel.whatYouToldUs
        }
      }

      "displays YES on update" when {
        "incorrect view model is provided" in {
          val sut = createSUT(incorrectEmploymentViewModel)

          val doc = Jsoup.parse(sut.toString())

          val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(3) > tbody")

          val incorrectEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")

          incorrectEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that is incorrect?"
          incorrectEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe incorrectEmploymentViewModel.isUpdate

          val missingEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")

          missingEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that is missing?"
          missingEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe incorrectEmploymentViewModel.isAdd

          val endedEmploymentOrPensionSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")

          endedEmploymentOrPensionSection.select("td:nth-of-type(1)").text() mustBe "Do you have an employment or pension that has ended?"
          endedEmploymentOrPensionSection.select("td:nth-of-type(2)").text() mustBe incorrectEmploymentViewModel.isEnd
        }
      }
    }
  }

  private val nino = new Generator(new Random()).nextNino

  private val endEmploymentViewModel: EmploymentPensionViewModel =
    EmploymentPensionViewModel("2017-2018",
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
    EmploymentPensionViewModel("2017-2018",
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
    EmploymentPensionViewModel("2017-2018",
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


  private def createSUT(viewModel: EmploymentPensionViewModel): Html = uk.gov.hmrc.tai.templates.html.EmploymentIForm(viewModel)
}
