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

package uk.gov.hmrc.tai.model.domain.formatters

import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import uk.gov.hmrc.tai.model.domain.benefits.{CompanyCar, CompanyCarBenefit, WithdrawCarAndFuel}

trait CompanyCarBenefitFormatters {

  def companyCarBenefitReads = new Reads[CompanyCarBenefit] {
    override def reads(json: JsValue): JsResult[CompanyCarBenefit] = {
      val empSeqNo = (json \ "employmentSequenceNumber").as[Int]
      val grossAmount = (json \ "grossAmount").as[BigDecimal]
      val carDetails = (json \ "carDetails").as[Seq[CompanyCar]](Reads.seq(companyCarReads))
      JsSuccess(CompanyCarBenefit(empSeqNo, grossAmount, carDetails))
    }
  }

  def companyCarReads = new Reads[CompanyCar] {
    override def reads(json: JsValue): JsResult[CompanyCar] = {
      val makeModel = (json \ "makeModel").as[String]
      val carSeqNo = (json \ "carSequenceNumber").as[Int]
      val dateMadeAvailable = (json \ "dateMadeAvailable").asOpt[LocalDate]
      val dateWithdrawn = (json \ "dateWithdrawn").asOpt[LocalDate]
      val fuelBenefit = json \ "fuelBenefit"

      val hasActiveFuelBenefit = fuelBenefit match {
        case JsDefined(fuel) => {
          val dateWithdrawn = (fuel \ "dateWithdrawn").asOpt[LocalDate]
          dateWithdrawn.isEmpty
        }
        case _ => false
      }

      val dateFuelBenefitMadeAvailable =
        if (hasActiveFuelBenefit) (fuelBenefit \ "dateMadeAvailable").asOpt[LocalDate] else None

      JsSuccess(
        CompanyCar(
          carSeqNo,
          makeModel,
          hasActiveFuelBenefit,
          dateMadeAvailable,
          dateFuelBenefitMadeAvailable,
          dateWithdrawn))
    }
  }

  val companyCarRemoveWrites: Writes[WithdrawCarAndFuel] = (
    (JsPath \ "version").write[Int] and
      (JsPath \ "removeCarAndFuel" \ "car" \ "withdrawDate").write[LocalDate] and
      (JsPath \ "removeCarAndFuel" \ "fuel" \ "withdrawDate").writeNullable[LocalDate]
  )(unlift(WithdrawCarAndFuel.unapply))

}
