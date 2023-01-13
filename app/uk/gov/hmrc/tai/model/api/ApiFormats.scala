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

package uk.gov.hmrc.tai.model.api

import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import uk.gov.hmrc.tai.model.EmploymentUpdate
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.benefits.CompanyCarBenefit
import uk.gov.hmrc.tai.model.domain.income.IncomeSource
import uk.gov.hmrc.tai.model.tai.TaxYear

trait ApiFormats {

  implicit val adjustmentFormat: Format[Adjustment] = Json.format[Adjustment]

  implicit val paymentFrequencyFormat = new Format[PaymentFrequency] {
    override def reads(json: JsValue): JsResult[PaymentFrequency] = json.as[String] match {
      case "Weekly"      => JsSuccess(Weekly)
      case "FortNightly" => JsSuccess(FortNightly)
      case "FourWeekly"  => JsSuccess(FourWeekly)
      case "Monthly"     => JsSuccess(Monthly)
      case "Quarterly"   => JsSuccess(Quarterly)
      case "BiAnnually"  => JsSuccess(BiAnnually)
      case "Annually"    => JsSuccess(Annually)
      case "OneOff"      => JsSuccess(OneOff)
      case "Irregular"   => JsSuccess(Irregular)
      case _             => throw new IllegalArgumentException("Invalid payment frequency")
    }

    override def writes(paymentFrequency: PaymentFrequency): JsValue = JsString(paymentFrequency.toString)
  }

  implicit val paymentFormat: Format[Payment] = Json.format[Payment]

  implicit val formatTaxYear: Format[TaxYear] = new Format[TaxYear] {
    override def reads(json: JsValue): JsSuccess[TaxYear] =
      if (json.validate[Int].isSuccess) {
        JsSuccess(TaxYear(json.as[Int]))
      } else {
        throw new IllegalArgumentException("Invalid tax year")
      }

    override def writes(taxYear: TaxYear): JsNumber = JsNumber(taxYear.year)
  }
  implicit val endOfTaxYearUpdate: Format[EndOfTaxYearUpdate] = Json.format[EndOfTaxYearUpdate]

  implicit val annualAccountFormat: Format[AnnualAccount] = Json.format[AnnualAccount]

  implicit val employmentFormat: Format[Employment] = Json.format[Employment]

  implicit val employmentCollectionFormat: Format[EmploymentCollection] = Json.format[EmploymentCollection]

  implicit val companyCarBenefitSeqWrites = new Writes[Seq[CompanyCarBenefit]] {
    override def writes(o: Seq[CompanyCarBenefit]): JsValue = Json.obj(
      "companyCarBenefits" -> o
    )
  }

  implicit val employmentUpdateFormat: Format[EmploymentUpdate] = Json.format[EmploymentUpdate]

  implicit val bbsiFormat: Format[BankAccount] = Json.format[BankAccount]

  implicit val addressFormat: Format[Address] = Json.format[Address]

  implicit val personFormat: Format[Person] = Json.format[Person]

  implicit val incomeSourceFormat: Format[IncomeSource] = Json.format[IncomeSource]

}
