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

package uk.gov.hmrc.tai.model.des

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, JsObject, Json, _}

case class IabdSummary(amount: Double,
                       iabdType: Int,
                       npsDescription: String,
                       employmentId: Int,
                       defaultEstimatedPay: Option[Boolean],
                       estimatedPaySource: Option[Int])

object IabdSummary {

  implicit val reads: Reads[IabdSummary] = (
    (JsPath \ "amount").read[Double] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "npsDescription").read[String] and
      (JsPath \ "employmentId").read[Int] and
      (JsPath \ "defaultEstimatedPay").readNullable[Boolean] and
      (JsPath \ "estimatedPaySource").readNullable[Int]
    ) (IabdSummary.apply _)

  implicit val writes: Writes[IabdSummary] = Json.writes[IabdSummary]
}

trait Iabd {

  def amount: Double
  def iabdType: Int
  def npsDescription: String
  def iabdSummaries: Seq[IabdSummary]
  def sourceAmount: Double

}

case class Allowance(npsDescription: String,
                     amount: Double,
                     iabdType: Int,
                     iabdSummaries: Seq[IabdSummary],
                     sourceAmount: Double) extends Iabd


object Allowance {

  implicit val reads: Reads[Allowance] = (
    (JsPath \ "npsDescription").read[String] and
      (JsPath \ "amount").read[Double] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "iabdSummaries").read[Seq[IabdSummary]] and
      (JsPath \ "sourceAmount").read[Double]
    ) (Allowance.apply _)

  implicit val writes: Writes[Allowance] = Json.writes[Allowance]
}

case class Deduction(npsDescription: String,
                     amount: Double,
                     iabdType: Int,
                     iabdSummaries: Seq[IabdSummary],
                     sourceAmount: Double) extends Iabd

object Deduction {

  implicit val reads: Reads[Deduction] = (
    (JsPath \ "npsDescription").read[String] and
      (JsPath \ "amount").read[Double] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "iabdSummaries").read[Seq[IabdSummary]] and
      (JsPath \ "sourceAmount").read[Double]
    ) (Deduction.apply _)

  implicit val writes: Writes[Deduction] = Json.writes[Deduction]
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





