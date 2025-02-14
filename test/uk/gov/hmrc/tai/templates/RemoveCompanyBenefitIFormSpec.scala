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
import uk.gov.hmrc.tai.model.templates.RemoveCompanyBenefitViewModel

import scala.util.Random

class RemoveCompanyBenefitIFormSpec extends PlaySpec {

  "RemoveCompanyBenefitIForm" must {
    "display the correct static content of remove company benefit iform" when {
      "the html document is created" in {

        val sut = createSUT(removeCompanyBenefitModel)
        val doc = Jsoup.parse(sut.body, "", Parser.xmlParser)
        doc.getElementsByAttributeValue("font-size", "14px").text() mustBe "Tell us about benefit from employment"
        doc.getElementsByAttributeValue("font-size", "13px").text() mustBe "Internal Information"
        doc.getElementsByAttributeValue("font-size", "12px").text() mustBe "Summary"
        doc.getElementsByAttributeValue("font-size", "11px").get(1).text() mustBe "Your details"
        doc.getElementsByAttributeValue("font-size", "11px").get(2).text() mustBe "What do you want to tell us?"
        doc.getElementsByAttributeValue("font-size", "11px").get(3).text() mustBe "Benefit details"
      }
    }
    "display the 'Your details' section of the remove company benefit iform" when {
      "an removeCompanyBenefitModel is provided" in {

        val sut = createSUT(removeCompanyBenefitModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)

        val yourDetailsTable = doc.getElementsByTag("fo:table").first()

        val nationalInsuranceNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(0)
        nationalInsuranceNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "National Insurance number"
        nationalInsuranceNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text mustBe removeCompanyBenefitModel.nino

        val firstNameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(1)

        firstNameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "First name"
        firstNameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe removeCompanyBenefitModel.firstName

        val surnameTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(2)

        surnameTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Last name"
        surnameTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe removeCompanyBenefitModel.lastName

        val dobTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(3)

        dobTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Date of birth"
        dobTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe removeCompanyBenefitModel.dateOfBirth

        val telephoneNumberQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(4)

        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Can we contact you by telephone?"
        telephoneNumberQuestionTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.telephoneContactAllowed

        val telephoneNumberTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(5)

        telephoneNumberTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Telephone number"
        telephoneNumberTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.telephoneNumber

        val ukAddressQuestionTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(6)

        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Is your address in the UK?"
        ukAddressQuestionTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe "Yes"

        val ukAddressTableRow = yourDetailsTable.getElementsByTag("fo:table-row").get(7)

        ukAddressTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "UK address"
        ukAddressTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe
          s"${removeCompanyBenefitModel.addressLine1} ${removeCompanyBenefitModel.addressLine2} " +
          s"${removeCompanyBenefitModel.addressLine3} ${removeCompanyBenefitModel.postcode}"
      }
    }
    "display the 'What do you want to tell us?' section of the remove company benefit iform" when {
      "an removeCompanyBenefitModel is provided" in {

        val sut = createSUT(removeCompanyBenefitModel)
        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)

        val whatDoYouWantToTellUsSection = doc.getElementsByTag("fo:table").get(1)

        val incorrectCompanyBenefitSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(0)

        incorrectCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have a company benefit that is incorrect?"
        incorrectCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.isUpdate

        val missingCompanyBenefitSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(1)

        missingCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have a company benefit that is missing?"
        missingCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.isAdd

        val endedCompanyBenefitSection = whatDoYouWantToTellUsSection.getElementsByTag("fo:table-row").get(2)

        endedCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Do you have a company benefit that has ended?"
        endedCompanyBenefitSection
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.isEnd
      }
    }
    "display the 'Benefit details' section of the remove company benefit iform" which {
      "contains 'benefit name' 'amount received' 'end date' and 'what you told us' information" in {

        val sut = createSUT(removeCompanyBenefitModel)

        val doc = Jsoup.parse(sut.toString(), "", Parser.xmlParser)

        val yourBenefitDetailsTable = doc.getElementsByTag("fo:table").get(2)

        val benefitNameTableRow = yourBenefitDetailsTable.getElementsByTag("fo:table-row").get(0)

        benefitNameTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Name of company benefit that has ended?"
        benefitNameTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.companyBenefitName

        val amountReceivedTableRow = yourBenefitDetailsTable.getElementsByTag("fo:table-row").get(1)

        amountReceivedTableRow
          .getElementsByTag("fo:table-cell")
          .get(0)
          .text() mustBe "Total amount of company benefit received"
        amountReceivedTableRow
          .getElementsByTag("fo:table-cell")
          .get(1)
          .text() mustBe removeCompanyBenefitModel.amountReceived

        val benefitEndedTableRow = yourBenefitDetailsTable.getElementsByTag("fo:table-row").get(2)

        benefitEndedTableRow.getElementsByTag("fo:table-cell").get(0).text() mustBe "Company benefit ended on"
        benefitEndedTableRow.getElementsByTag("fo:table-cell").get(1).text() mustBe removeCompanyBenefitModel.endDate
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
      "On or after 6 April 2017"
    )
  private def createSUT(viewModel: RemoveCompanyBenefitViewModel) =
    uk.gov.hmrc.tai.templates.xml.RemoveCompanyBenefitIForm(viewModel)

}
