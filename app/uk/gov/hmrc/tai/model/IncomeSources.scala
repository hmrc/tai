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

package uk.gov.hmrc.tai.model

import play.api.libs.json.{Json, Format, JsObject}

case class TAHIabdSummary(amount: Double,
                           `type`: Int,
                           npsDescription: String,
                           employmentId: Int,
                           defaultEstimatedPay: Option[Boolean],
                           estimatedPaySource: Option[Int])

object TAHIabdSummary {

  implicit val formatSeq: Format[TAHIabdSummary] = Json.format[TAHIabdSummary]

}

trait Iabd {

  def amount: Double
  def `type`: Int
  def npsDescription: String
  def iabdSummaries: Seq[TAHIabdSummary]
  def sourceAmount: Double

}

case class Allowance(npsDescription: String,
                     amount: Double,
                     `type`: Int,
                     iabdSummaries: Seq[TAHIabdSummary],
                     sourceAmount: Double) extends Iabd


object Allowance {

  implicit val format: Format[Allowance] = Json.format[Allowance]

}

case class Deduction(npsDescription: String,
                     amount: Double,
                     `type`: Int,
                     iabdSummaries: Seq[TAHIabdSummary],
                     sourceAmount: Double) extends Iabd

object Deduction {

  implicit val format: Format[Deduction] = Json.format[Deduction]

}

case class IncomeSources(employmentId: Int,
                         employmentType: Int,
                         employmentStatus: Int,
                         employmentTaxDistrictNumber: Int,
                         employmentPayeRef: String,
                         pensionIndicator: Boolean,
                         otherIncomeSourceIndicator: Boolean,
                         jsaIndicator: Boolean,
                         name: String,
                         taxCode: String,
                         basisOperation: Int,
                         potentialUnderpayment: Option[Int],
                         totalInYearAdjustment: Int,
                         inYearAdjustmentIntoCY: Int,
                         inYearAdjustmentIntoCYPlusOne: Int,
                         inYearAdjustmentFromPreviousYear: Int,
                         actualPUPCodedInCYPlusOneTaxYear: Int,
                         allowances: Seq[Allowance],
                         deductions: Seq[Deduction],
                         payAndTax: JsObject)

object IncomeSources {

  implicit val formats: Format[IncomeSources] = Json.format[IncomeSources]

}





