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

import java.time.LocalDate
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import org.scalatestplus.play.PlaySpec
import play.twirl.api.Html
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.model.domain.{Address, Person}

import java.time.format.DateTimeFormatter
import scala.util.Random

class PersonDetailsTemplateSpec extends PlaySpec {

  "Person Details" must {

    "display the national insurance number" in {
      val nationalInsuranceNumberTableRow = yourDetailsTable.select("tr:nth-of-type(1)")

      nationalInsuranceNumberTableRow.select("td:nth-of-type(1)").text() mustBe "National Insurance number"
      nationalInsuranceNumberTableRow.select("td:nth-of-type(2)").text() mustBe personDetails.nino.nino
    }

    "display the first name" in {
      val firstNameTableRow = yourDetailsTable.select("tr:nth-of-type(2)")

      firstNameTableRow.select("td:nth-of-type(1)").text() mustBe "First name"
      firstNameTableRow.select("td:nth-of-type(2)").text() mustBe personDetails.firstName
    }

    "display the last name" in {
      val surnameTableRow = yourDetailsTable.select("tr:nth-of-type(3)")

      surnameTableRow.select("td:nth-of-type(1)").text() mustBe "Last name"
      surnameTableRow.select("td:nth-of-type(2)").text() mustBe personDetails.surname
    }

    "display the date of birth" in {
      val dobTableRow = yourDetailsTable.select("tr:nth-of-type(4)")

      dobTableRow.select("td:nth-of-type(1)").text() mustBe "Date of birth"
      dobTableRow.select("td:nth-of-type(2)").text() mustBe dateOfBirthString
    }

    "display the telephone number" in {
      val telephoneNumberTableRow = yourDetailsTable.select("tr:nth-of-type(5)")

      telephoneNumberTableRow.select("td:nth-of-type(1)").text() mustBe "Telephone number"
      telephoneNumberTableRow.select("td:nth-of-type(2)").text() mustBe ""
    }

    "display the uk address check" in {
      val ukAddressQuestionTableRow = yourDetailsTable.select("tr:nth-of-type(6)")

      ukAddressQuestionTableRow.select("td:nth-of-type(1)").text() mustBe "Is your address in the UK?"
      ukAddressQuestionTableRow.select("td:nth-of-type(2)").text() mustBe "Yes"
    }

    "display the uk address" in {
      val ukAddressTableRow = yourDetailsTable.select("tr:nth-of-type(7)")

      ukAddressTableRow.select("td:nth-of-type(1)").text() mustBe "UK address"
      ukAddressTableRow.select("td:nth-of-type(2)").text() mustBe "line1 line2 line3 postcode"
    }
  }
  private val nino: Nino = new Generator(new Random).nextNino
  private val dateOfBirth = LocalDate.parse("2017-02-01")
  private val dateOfBirthString: String = dateOfBirth.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))

  private val personDetails = Person(
    Nino(nino.nino),
    "test",
    "tester",
    Some(dateOfBirth),
    Address("line1", "line2", "line3", "postcode", "country")
  )

  val personDetailsTemplate: Html = uk.gov.hmrc.tai.templates.html.PersonDetails(personDetails)
  val doc: Document = Jsoup.parse(personDetailsTemplate.toString())
  val yourDetailsTable: Elements = doc.select("table:nth-of-type(1) > tbody")
}
