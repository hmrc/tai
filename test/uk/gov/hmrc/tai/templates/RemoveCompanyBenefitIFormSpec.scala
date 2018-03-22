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

package uk.gov.hmrc.tai.templates

import org.jsoup.Jsoup
import org.scalatestplus.play.PlaySpec
import play.twirl.api.Html
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.templates.RemoveCompanyBenefitViewModel

import scala.util.Random

class RemoveCompanyBenefitIFormSpec extends PlaySpec{

  "RemoveCompanyBenefitIForm" must {
    "display the correct static content of remove company benefit iform" when {
      "the html document is created" in {

        val sut = createSUT(removeCompanyBenefitModel)
        val doc = Jsoup.parse(sut.toString())
        doc.title mustBe "What do you want to do?"
        doc.select("h1").text() mustBe "Tell us about benefit from employment"
        doc.select("h2").text() mustBe "Internal Information"
        doc.select("h3:nth-of-type(1)").text() mustBe "Summary"
        doc.select("h3:nth-of-type(2)").text() mustBe "Your details"
        doc.select("h3:nth-of-type(3)").text() mustBe "What do you want to tell us?"
        doc.select("h3:nth-of-type(4)").text() mustBe "Benefit details"
      }
    }
    "display the 'Your details' section of the remove company benefit iform" when {
      "an removeCompanyBenefitModel is provided" in {

        val sut = createSUT(removeCompanyBenefitModel)

        val doc = Jsoup.parse(sut.toString())

        val yourDetailsTable = doc.select("table:nth-of-type(1) > tbody")

        val nationalInsuranceNumberTableRow = yourDetailsTable.select("tr:nth-of-type(1)")

        nationalInsuranceNumberTableRow.select("td:nth-of-type(1)").text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.nino

        val firstNameTableRow = yourDetailsTable.select("tr:nth-of-type(2)")

        firstNameTableRow.select("td:nth-of-type(1)").text() mustBe "First name"
        firstNameTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.firstName

        val surnameTableRow = yourDetailsTable.select("tr:nth-of-type(3)")

        surnameTableRow.select("td:nth-of-type(1)").text() mustBe "Last name"
        surnameTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.lastName

        val dobTableRow = yourDetailsTable.select("tr:nth-of-type(4)")

        dobTableRow.select("td:nth-of-type(1)").text() mustBe "Date of birth"
        dobTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(5)")

        telephoneNumberQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.select("tr:nth-of-type(6)")

        telephoneNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Telephone number"
        telephoneNumberTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(7)")

        ukAddressQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.select("td:nth-of-type(2)").text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.select("tr:nth-of-type(8)")

        ukAddressTableRow.select("td:nth-of-type(1)").text() mustBe "UK address"
        ukAddressTableRow.select("td:nth-of-type(2)").text() mustBe
          s"${removeCompanyBenefitModel.addressLine1} ${removeCompanyBenefitModel.addressLine2} " +
            s"${removeCompanyBenefitModel.addressLine3} ${removeCompanyBenefitModel.postcode}"
      }
    }
    "display the 'What do you want to tell us?' section of the remove company benefit iform" when {
      "an removeCompanyBenefitModel is provided" in{

        val sut = createSUT(removeCompanyBenefitModel)

        val doc = Jsoup.parse(sut.toString())

        val whatDoYouWantToTellUsSection = doc.select("table:nth-of-type(2) > tbody")

        val incorrectCompanyBenefitSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(1)")

        incorrectCompanyBenefitSection.select("td:nth-of-type(1)").text() mustBe "Do you have a company benefit that is incorrect?"
        incorrectCompanyBenefitSection.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.isUpdate

        val missingCompanyBenefitSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(2)")

        missingCompanyBenefitSection.select("td:nth-of-type(1)").text() mustBe "Do you have a company benefit that is missing?"
        missingCompanyBenefitSection.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.isAdd

        val endedCompanyBenefitSection = whatDoYouWantToTellUsSection.select("tr:nth-of-type(3)")

        endedCompanyBenefitSection.select("td:nth-of-type(1)").text() mustBe "Do you have a company benefit that has ended?"
        endedCompanyBenefitSection.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.isEnd
      }
    }
    "display the 'Benefit details' section of the remove company benefit iform" which {
      "contains 'benefit name' 'amount received' 'end date' and 'what you told us' information" in {

        val sut = createSUT(removeCompanyBenefitModel)

        val doc = Jsoup.parse(sut.toString())

        val yourBenefitDetailsTable = doc.select("table:nth-of-type(3) > tbody")

        val benefitNameTableRow = yourBenefitDetailsTable.select("tr:nth-of-type(1)")

        benefitNameTableRow.select("td:nth-of-type(1)").text() mustBe "Name of company benefit that has ended?"
        benefitNameTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.companyBenefitName

        val amountReceivedTableRow = yourBenefitDetailsTable.select("tr:nth-of-type(2)")

        amountReceivedTableRow.select("td:nth-of-type(1)").text() mustBe "Total amount of company benefit received"
        amountReceivedTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.amountReceived

        val benefitEndedTableRow = yourBenefitDetailsTable.select("tr:nth-of-type(3)")

        benefitEndedTableRow.select("td:nth-of-type(1)").text() mustBe "Company benefit ended on"
        benefitEndedTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.endDate

        val whatYouToldUsTableRow = yourBenefitDetailsTable.select("tr:nth-of-type(4)")

        whatYouToldUsTableRow.select("td:nth-of-type(1)").text() mustBe "Tell us what is incorrect and why"
        whatYouToldUsTableRow.select("td:nth-of-type(2)").text() mustBe removeCompanyBenefitModel.whatYouToldUs

      }
    }
  }

  private val nino = new Generator(new Random()).nextNino
  private val removeCompanyBenefitModel: RemoveCompanyBenefitViewModel =
    RemoveCompanyBenefitViewModel(
    nino.nino,
    "firstname",
    "lastname",
    "3 April 1984",
    "Yes",
    "1234567889",
    "addressLine1",
    "addressLine2",
    "addressLine3",
    "postcode",
    "No",
    "No",
    "Yes",
    "Mileage",
    "10030",
    "On or after 6 April 2017",
    "I no longer get this benefit"
  )
  private def createSUT(viewModel: RemoveCompanyBenefitViewModel): Html = uk.gov.hmrc.tai.templates.html.RemoveCompanyBenefitIForm(viewModel)

}
