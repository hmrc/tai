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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class TAHIabdSummary(amount: Double,
                           iabdType: Int,
                           npsDescription: String,
                           employmentId: Int,
                           defaultEstimatedPay: Option[Boolean],
                           estimatedPaySource: Int)

object TAHIabdSummary {

  implicit val formatsIabdSummary = (
      (JsPath \ "amount").read[Double] and
      (JsPath \ "type").read[Int] and
      (JsPath \ "npsDescription").read[String] and
      (JsPath \ "employmentId").read[Int] and
      (JsPath \ "defaultEstimatedPay").readNullable[Boolean] and
      (JsPath \ "estimatedPaySource").read[Int]
    )(TAHIabdSummary.apply _)

  implicit val formatSeq = Json.reads[Seq[TAHIabdSummary]]

}

trait Iabd {

  def amount: Double

  def iabdType: Int

  def npsDescription: String

  def iabdSummaries: Seq[TAHIabdSummary]

  def sourceAmount: Double

}

case class Allowance(npsDescription: String,
                     amount: Double,
                     iabdType: Int,
                     iabdSummaries: Seq[TAHIabdSummary],
                     sourceAmount: Double) //extends Iabd


object Allowance {

//  implicit val formats: Format[Allowance] = Json.format[Allowance]

//  implicit val formats = new Reads[Allowance] {
//    def reads(js: JsValue): JsSuccess[Allowance] = JsSuccess(Allowance(
//      (js \ "npsDescription").as[String],
//      (js \ "amount").as[Double],
//      (js \ "type").as[Int],
//      (js \ "iabdSummaries").as[Seq[TAHIabdSummary]],
//      (js \ "sourceAmount").as[Double]
//    ))

    implicit val formats = (
      (JsPath \ "npsDescription").read[String] and
        (JsPath \ "amount").read[Double] and
        (JsPath \ "type").read[Int] and
        (JsPath \ "iabdSummaries").read[Seq[TAHIabdSummary]] and
        (JsPath \ "sourceAmount").read[Double]
      )(Allowance.apply _)


}

case class Deduction(npsDescription: String,
                     amount: Double,
                     iabdType: Int,
                     iabdSummaries: Seq[TAHIabdSummary],
                     sourceAmount: Double) extends Iabd


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
//                         deductions: Seq[Deduction],
                         payAndTax: JsObject)

object IncomeSources {

  implicit val formats: Format[IncomeSources] = Json.format[IncomeSources]

}





