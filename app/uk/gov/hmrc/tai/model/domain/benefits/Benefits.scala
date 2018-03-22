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

package uk.gov.hmrc.tai.model.domain.benefits

import org.joda.time.LocalDate
import play.api.libs.json.Json
import uk.gov.hmrc.tai.model.domain.{BenefitComponentType, TaxComponentType}

case class CompanyCar(carSeqNo:Int,
                      makeModel:String,
                      hasActiveFuelBenefit: Boolean,
                      dateMadeAvailable: Option[LocalDate],
                      dateActiveFuelBenefitMadeAvailable: Option[LocalDate],
                      dateWithdrawn: Option[LocalDate])

object CompanyCar{
  implicit val formats = Json.format[CompanyCar]
}

case class CompanyCarBenefit(employmentSeqNo:Int,
                             grossAmount:BigDecimal,
                             companyCars: Seq[CompanyCar],
                             version:Option[Int] = None)

object CompanyCarBenefit{
  implicit val formats = Json.format[CompanyCarBenefit]
}

case class GenericBenefit(benefitType:BenefitComponentType, employmentId:Option[Int], amount:BigDecimal)

object GenericBenefit{
  implicit val formats = Json.format[GenericBenefit]
}

case class Benefits(companyCarBenefits: Seq[CompanyCarBenefit], otherBenefits: Seq[GenericBenefit])

object Benefits{
  implicit val formats = Json.format[Benefits]
}

case class WithdrawCarAndFuel(version: Int, carWithdrawDate: LocalDate, fuelWithdrawDate: Option[LocalDate])

object WithdrawCarAndFuel{
  implicit val formats = Json.format[WithdrawCarAndFuel]
}

case class RemoveCompanyBenefit(benefitType: String,
                                whatYouToldUs: String,
                                stopDate: String,
                                valueOfBenefit: Option[String],
                                contactByPhone: String,
                                phoneNumber: Option[String])

object RemoveCompanyBenefit {
  implicit val formats = Json.format[RemoveCompanyBenefit]
}