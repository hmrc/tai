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
import uk.gov.hmrc.tai.model.domain.benefits.RemoveCompanyBenefit
import uk.gov.hmrc.tai.util.IFormConstants
import uk.gov.hmrc.tai.util.IFormConstants.{No, Yes}

case class RemoveCompanyBenefitViewModel (
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
                                           companyBenefitName: String,
                                           amountReceived: String,
                                           endDate: String,
                                           whatYouToldUs: String
                                         )
object RemoveCompanyBenefitViewModel {

  def apply(person: Person, removeCompanyBenefit: RemoveCompanyBenefit): RemoveCompanyBenefitViewModel = {

    RemoveCompanyBenefitViewModel(
      nino = person.nino.nino,
      firstName = person.firstName,
      lastName = person.surname,
      dateOfBirth = person.dateOfBirth.map(_.toString(IFormConstants.DateFormat)).getOrElse(""),
      telephoneContactAllowed = removeCompanyBenefit.contactByPhone,
      telephoneNumber = removeCompanyBenefit.phoneNumber.getOrElse(""),
      addressLine1 = person.address.line1,
      addressLine2 = person.address.line2,
      addressLine3 = person.address.line3,
      postcode = person.address.postcode,
      isAdd = No,
      isUpdate = No,
      isEnd = Yes,
      companyBenefitName = removeCompanyBenefit.benefitType,
      amountReceived = removeCompanyBenefit.valueOfBenefit.getOrElse(""),
      endDate = removeCompanyBenefit.stopDate,
      whatYouToldUs = removeCompanyBenefit.whatYouToldUs
    )
  }
}
