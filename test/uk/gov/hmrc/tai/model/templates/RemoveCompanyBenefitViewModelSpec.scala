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
import uk.gov.hmrc.tai.model.domain.{Address, Person}
import uk.gov.hmrc.tai.model.domain.benefits.RemoveCompanyBenefit
import uk.gov.hmrc.tai.util.IFormConstants.{No, Yes}

import scala.util.Random

class RemoveCompanyBenefitViewModelSpec extends PlaySpec {

  "RemoveCompanyBenefitViewModel 'RemoveCompanyBenefit' apply method" must {
    "generate a view model with date of birth" when {
      "date of birth is present in person" in {
        val removeCompanyBenefit =
          RemoveCompanyBenefit("Mileage", "On or after 6 April 2017", Some("10030"), "Yes", Some("1234567889"))
        val sut = RemoveCompanyBenefitViewModel(person = person, removeCompanyBenefit = removeCompanyBenefit)
        sut mustBe removeCompanyBenefitModel
      }
    }
    "generate a view model with the correct yes/no combination" in {
      val removeCompanyBenefit =
        RemoveCompanyBenefit("Mileage", "On or after 6 April 2017", Some("10030"), "Yes", Some("0201223223"))
      val sut = RemoveCompanyBenefitViewModel(person = person, removeCompanyBenefit = removeCompanyBenefit)
      sut.isAdd mustBe No
      sut.isEnd mustBe Yes
      sut.isUpdate mustBe No
    }
    "generate a view model with 'benefit name' 'what you told us' 'stop date' 'amount received' 'phone contact allowed' and 'phone number' when all of them were provided" in {
      val removeCompanyBenefit =
        RemoveCompanyBenefit("Mileage", "On or after 6 April 2017", Some("10030"), "Yes", Some("0201223223"))
      val sut = RemoveCompanyBenefitViewModel(person = person, removeCompanyBenefit = removeCompanyBenefit)
      sut.companyBenefitName mustBe "Mileage"
      sut.endDate mustBe "On or after 6 April 2017"
      sut.amountReceived mustBe "10030"
      sut.telephoneContactAllowed mustBe "Yes"
      sut.telephoneNumber mustBe "0201223223"
    }
    "generate a view model with 'benefit name' 'what you told us' 'stop date' 'phone contact allowed' and 'phone number' when amount is not provided" in {
      val removeCompanyBenefit = RemoveCompanyBenefit("Mileage", "Before 6 April 2017", None, "Yes", Some("0201223223"))
      val sut = RemoveCompanyBenefitViewModel(person = person, removeCompanyBenefit = removeCompanyBenefit)
      sut.companyBenefitName mustBe "Mileage"
      sut.endDate mustBe "Before 6 April 2017"
      sut.telephoneContactAllowed mustBe "Yes"
      sut.telephoneNumber mustBe "0201223223"
    }
    "generate a view model with 'benefit name' 'what you told us' 'stop date' 'amount received' 'phone contact allowed' when phone number is not provided" in {
      val removeCompanyBenefit = RemoveCompanyBenefit("Mileage", "Before 6 April 2017", Some("11000"), "No", None)
      val sut = RemoveCompanyBenefitViewModel(person = person, removeCompanyBenefit = removeCompanyBenefit)
      sut.companyBenefitName mustBe "Mileage"
      sut.endDate mustBe "Before 6 April 2017"
      sut.amountReceived mustBe "11000"
      sut.telephoneContactAllowed mustBe "No"
    }
  }

  private val nino = new Generator(new Random()).nextNino

  private val person = Person(
    nino,
    "firstname",
    "lastname",
    Some(new LocalDate("1984-04-03")),
    Address("addressLine1", "addressLine2", "addressLine3", "postcode", "UK"))

  private val removeCompanyBenefitModel = RemoveCompanyBenefitViewModel(
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

}
