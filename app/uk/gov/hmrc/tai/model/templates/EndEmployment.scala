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

package uk.gov.hmrc.tai.model.templates

import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.IFormConstants
import uk.gov.hmrc.tai.util.IFormConstants.{No, Yes}


case class EmploymentPensionViewModel(taxYearRange: String,
                                      nino: String,
                                      firstName: String,
                                      lastName: String,
                                      dateOfBirth: String,
                                      telephoneContactAllowed: String,
                                      telephoneNumber: String,
                                      addressLine1: String,
                                      addressLine2: String,
                                      addressLine3: String,
                                      postcode: String,
                                      isAdd: String,
                                      isUpdate: String,
                                      isEnd: String,
                                      employmentPensionName: String,
                                      payrollNumber: String,
                                      startDate: String,
                                      endDate: String,
                                      whatYouToldUs: String)

object EmploymentPensionViewModel {

  def apply(taxYear: TaxYear, person: Person, employment: AddEmployment): EmploymentPensionViewModel = {

    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
      nino = person.nino.nino,
      firstName = person.firstName,
      lastName = person.surname,
      dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
      telephoneContactAllowed = employment.telephoneContactAllowed,
      telephoneNumber = employment.telephoneNumber.getOrElse(""),
      addressLine1 = person.address.line1,
      addressLine2 = person.address.line2,
      addressLine3 = person.address.line3,
      postcode = person.address.postcode,
      isAdd = Yes,
      isUpdate = No,
      isEnd = No,
      employmentPensionName = employment.employerName,
      payrollNumber = employment.payrollNumber,
      startDate = employment.startDate.toString(IFormConstants.DateFormat),
      endDate = "",
      whatYouToldUs = ""
    )
  }

  def apply(taxYear: TaxYear, person: Person, endEmployment: EndEmployment, existingEmployment: Employment): EmploymentPensionViewModel = {

    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
      nino = person.nino.nino,
      firstName = person.firstName,
      lastName = person.surname,
      dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
      telephoneContactAllowed = endEmployment.telephoneContactAllowed,
      telephoneNumber = endEmployment.telephoneNumber.getOrElse(""),
      addressLine1 = person.address.line1,
      addressLine2 = person.address.line2,
      addressLine3 = person.address.line3,
      postcode = person.address.postcode,
      isAdd = No,
      isUpdate = No,
      isEnd = Yes,
      employmentPensionName = existingEmployment.name,
      payrollNumber = existingEmployment.payrollNumber.getOrElse("No"),
      startDate = "",
      endDate = endEmployment.endDate.toString(IFormConstants.DateFormat),
      whatYouToldUs = ""
    )
  }

  def apply(taxYear: TaxYear, person: Person, incorrectEmployment: IncorrectEmployment, existingEmployment: Employment): EmploymentPensionViewModel = {

    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
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
      employmentPensionName = existingEmployment.name,
      payrollNumber = existingEmployment.payrollNumber.getOrElse("No"),
      startDate = "",
      endDate = "",
      whatYouToldUs = incorrectEmployment.whatYouToldUs
    )
  }

  def apply(taxYear: TaxYear, person: Person, pensionProvider: AddPensionProvider): EmploymentPensionViewModel = {

    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
      nino = person.nino.nino,
      firstName = person.firstName,
      lastName = person.surname,
      dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
      telephoneContactAllowed = pensionProvider.telephoneContactAllowed,
      telephoneNumber = pensionProvider.telephoneNumber.getOrElse(""),
      addressLine1 = person.address.line1,
      addressLine2 = person.address.line2,
      addressLine3 = person.address.line3,
      postcode = person.address.postcode,
      isAdd = Yes,
      isUpdate = No,
      isEnd = No,
      employmentPensionName = pensionProvider.pensionProviderName,
      payrollNumber = pensionProvider.pensionNumber,
      startDate = pensionProvider.startDate.toString(IFormConstants.DateFormat),
      endDate = "",
      whatYouToldUs = ""
    )
  }

  def apply(taxYear: TaxYear, person: Person, incorrectPensionProvider: IncorrectPensionProvider, existingEmployment: Employment):
            EmploymentPensionViewModel = {

    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
      nino = person.nino.nino,
      firstName = person.firstName,
      lastName = person.surname,
      dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
      telephoneContactAllowed = incorrectPensionProvider.telephoneContactAllowed,
      telephoneNumber = incorrectPensionProvider.telephoneNumber.getOrElse(""),
      addressLine1 = person.address.line1,
      addressLine2 = person.address.line2,
      addressLine3 = person.address.line3,
      postcode = person.address.postcode,
      isAdd = No,
      isUpdate = Yes,
      isEnd = No,
      employmentPensionName = existingEmployment.name,
      payrollNumber = existingEmployment.payrollNumber.getOrElse("No"),
      startDate = "",
      endDate = "",
      whatYouToldUs = incorrectPensionProvider.whatYouToldUs
    )
  }

  def apply(taxYear: TaxYear, person: Person, incorrectEmployment: IncorrectEmployment): EmploymentPensionViewModel =
    EmploymentPensionViewModel(
      taxYearRange = s"${taxYear.start.toString(IFormConstants.DateFormat)} to ${taxYear.end.toString(IFormConstants.DateFormat)}",
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


