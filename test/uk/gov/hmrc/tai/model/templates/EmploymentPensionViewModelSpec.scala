/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.templates

import org.joda.time.LocalDate
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, _}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.IFormConstants
import uk.gov.hmrc.tai.util.IFormConstants.{No, Yes}

import scala.util.Random

class EmploymentPensionViewModelSpec extends PlaySpec {

  "EmploymentPensionViewModel 'add employment' apply method" must {
    "generate a view model with date of birth" when {
      "date of birth is present in person" in {
        val addEmployment =
          AddEmployment("employerName", new LocalDate("2018-12-13"), "12345", "Yes", Some("123456789"))
        val sut = EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, employment = addEmployment)
        sut mustBe addEmploymentModel
      }
    }
    "generate a view model without date of birth" when {
      "date of birth is absent from person" in {
        val addEmployment =
          AddEmployment("employerName", new LocalDate("2018-12-13"), "12345", "Yes", Some("123456789"))
        val noDobPerson = person.copy(dateOfBirth = None)
        val sut = EmploymentPensionViewModel(taxYear = TaxYear(2017), person = noDobPerson, employment = addEmployment)
        sut mustBe addEmploymentModel.copy(dateOfBirth = "")
      }
    }
    "generate a view model without phone number" when {
      "phone number is absent from AddEmployment model" in {
        val addEmployment = AddEmployment("employerName", new LocalDate("2018-12-13"), "12345", "No", None)
        val sut = EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, employment = addEmployment)
        sut mustBe addEmploymentModel.copy(telephoneContactAllowed = "No", telephoneNumber = "")
      }
    }
    "generate a view model with the correct yes/no combination" in {
      val addEmployment = AddEmployment("employerName", new LocalDate("2018-12-13"), "12345", "No", None)
      val sut = EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, employment = addEmployment)
      sut.isAdd mustBe Yes
      sut.isEnd mustBe No
      sut.isUpdate mustBe No
    }
  }

  "EmploymentPensionViewModel 'end employment' apply method" must {
    "generate a view model with the correct yes/no combination" in {
      val endEmployment = EndEmployment(new LocalDate("2018-12-13"), "Yes", Some("123456789"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        endEmployment = endEmployment,
        existingEmployment = existingEmployment)
      sut.isAdd mustBe No
      sut.isEnd mustBe Yes
      sut.isUpdate mustBe No
    }

    "generate a view model with the employer name and payroll number present" in {
      val endEmployment = EndEmployment(new LocalDate("2018-12-13"), "Yes", Some("123456789"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        endEmployment = endEmployment,
        existingEmployment = existingEmployment)
      sut.employmentPensionName mustBe "fake employer"
      sut.payrollNumber mustBe "12345"
    }

    "generate a view model with the payroll number set to 'No', where not present on the existing employment record" in {
      val endEmployment = EndEmployment(new LocalDate("2018-12-13"), "Yes", Some("123456789"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        endEmployment = endEmployment,
        existingEmployment = existingEmployment.copy(payrollNumber = None))
      sut.employmentPensionName mustBe "fake employer"
      sut.payrollNumber mustBe "No"
    }
  }

  "EmploymentPensionViewModel incorrect employment apply method" must {
    "generate a view model with the correct yes/no combination" in {
      val incorrectEmployment = IncorrectEmployment("TEST", "Yes", Some("123455"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        incorrectEmployment = incorrectEmployment,
        existingEmployment = existingEmployment)
      sut.isAdd mustBe No
      sut.isEnd mustBe No
      sut.isUpdate mustBe Yes
    }

    "generate a view model with the employer name and payroll number present" in {
      val incorrectEmployment = IncorrectEmployment("TEST", "Yes", Some("123455"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        incorrectEmployment = incorrectEmployment,
        existingEmployment = existingEmployment)
      sut.employmentPensionName mustBe "fake employer"
      sut.payrollNumber mustBe "12345"
    }
  }

  "EmploymentPensionViewModel 'add pension provider' apply method" must {
    "generate a view model with date of birth" when {
      "date of birth is present in person" in {
        val addPensionProvider =
          AddPensionProvider("pension name", new LocalDate("2017-06-09"), "12345", "Yes", Some("123456789"))
        val sut =
          EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, pensionProvider = addPensionProvider)
        sut mustBe addPensionModel
      }
    }

    "generate a view model without date of birth" when {
      "date of birth is absent from person" in {
        val addPensionProvider =
          AddPensionProvider("pension name", new LocalDate("2017-06-09"), "12345", "Yes", Some("123456789"))
        val noDobPerson = person.copy(dateOfBirth = None)
        val sut = EmploymentPensionViewModel(
          taxYear = TaxYear(2017),
          person = noDobPerson,
          pensionProvider = addPensionProvider)
        sut mustBe addPensionModel.copy(dateOfBirth = "")
      }
    }

    "generate a view model without phone number" when {
      "phone number is absent from AddEmployment model" in {
        val addPensionProvider = AddPensionProvider("pension name", new LocalDate("2017-06-09"), "12345", "No", None)
        val sut =
          EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, pensionProvider = addPensionProvider)
        sut mustBe addPensionModel.copy(telephoneContactAllowed = "No", telephoneNumber = "")
      }
    }

    "generate a view model with the correct yes/no combination" in {
      val addPensionProvider = AddPensionProvider("pension name", new LocalDate("2017-06-09"), "12345", "No", None)
      val sut =
        EmploymentPensionViewModel(taxYear = TaxYear(2017), person = person, pensionProvider = addPensionProvider)
      sut.isAdd mustBe Yes
      sut.isEnd mustBe No
      sut.isUpdate mustBe No
    }
  }

  "EmploymentPensionViewModel incorrect employment for year apply method" must {
    "generate a view model with the correct details" in {
      val incorrectEmployment = IncorrectEmployment("TEST", "Yes", Some("123455"))
      val year = TaxYear(2017)
      val sut = EmploymentPensionViewModel(taxYear = year, person = person, incorrectEmployment = incorrectEmployment)

      sut mustBe EmploymentPensionViewModel(
        taxYearRange =
          s"${year.start.toString(IFormConstants.DateFormat)} to ${year.end.toString(IFormConstants.DateFormat)}",
        nino = person.nino.nino,
        firstName = person.firstName,
        lastName = person.surname,
        dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
        telephoneContactAllowed = incorrectEmployment.telephoneContactAllowed,
        telephoneNumber = incorrectEmployment.telephoneNumber.getOrElse(""),
        addressLine1 = person.address.line1,
        addressLine2 = person.address.line2,
        addressLine3 = person.address.line3,
        postcode = person.address.postcode,
        isAdd = No,
        isUpdate = Yes,
        isEnd = No,
        employmentPensionName = "",
        payrollNumber = "",
        startDate = "",
        endDate = "",
        whatYouToldUs = incorrectEmployment.whatYouToldUs
      )
    }
  }

  "EmploymentPensionViewModel incorrect pension apply method" must {
    "generate a view model with the correct yes/no combination" in {
      val incorrectPensionProvider = IncorrectPensionProvider("TEST", "Yes", Some("123455"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        incorrectPensionProvider = incorrectPensionProvider,
        existingEmployment = existingEmployment)
      sut.isAdd mustBe No
      sut.isEnd mustBe No
      sut.isUpdate mustBe Yes
    }

    "generate a view model with the employer name and payroll number present" in {
      val incorrectPensionProvider = IncorrectPensionProvider("TEST", "Yes", Some("123455"))
      val sut = EmploymentPensionViewModel(
        taxYear = TaxYear(2017),
        person = person,
        incorrectPensionProvider = incorrectPensionProvider,
        existingEmployment = existingEmployment)
      sut.employmentPensionName mustBe "fake employer"
      sut.payrollNumber mustBe "12345"
    }
  }

  private val nino = new Generator(new Random()).nextNino
  private val person = Person(
    nino,
    "firstname",
    "lastname",
    Some(new LocalDate("1982-04-03")),
    Address("address line 1", "address line 2", "address line 3", "postcode", "UK"))
  private val addEmploymentModel = EmploymentPensionViewModel(
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
    "employerName",
    "12345",
    "13 December 2018",
    "",
    ""
  )

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

  private val existingEmployment = Employment(
    "fake employer",
    Some("12345"),
    LocalDate.parse("2017-04-04"),
    None,
    "33",
    "44",
    1,
    Some(100),
    false,
    false)

}
