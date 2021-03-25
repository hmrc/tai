/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain.taxAdjustments

import play.api.libs.json._

sealed trait TaxAdjustmentType

trait ReliefsGivingBackTax extends TaxAdjustmentType
trait OtherTaxDue extends TaxAdjustmentType
trait AlreadyTaxedAtSource extends TaxAdjustmentType
trait TaxReliefComponent extends TaxAdjustmentType

case object EnterpriseInvestmentSchemeRelief extends ReliefsGivingBackTax
case object ConcessionalRelief extends ReliefsGivingBackTax
case object MaintenancePayments extends ReliefsGivingBackTax
case object MarriedCouplesAllowance extends ReliefsGivingBackTax
case object DoubleTaxationRelief extends ReliefsGivingBackTax

case object ExcessGiftAidTax extends OtherTaxDue
case object ExcessWidowsAndOrphans extends OtherTaxDue
case object PensionPaymentsAdjustment extends OtherTaxDue
case object ChildBenefit extends OtherTaxDue

case object TaxOnBankBSInterest extends AlreadyTaxedAtSource
case object TaxCreditOnUKDividends extends AlreadyTaxedAtSource
case object TaxCreditOnForeignInterest extends AlreadyTaxedAtSource
case object TaxCreditOnForeignIncomeDividends extends AlreadyTaxedAtSource

case object PersonalPensionPayment extends TaxReliefComponent
case object PersonalPensionPaymentRelief extends TaxReliefComponent
case object GiftAidPayments extends TaxReliefComponent
case object GiftAidPaymentsRelief extends TaxReliefComponent

object TaxAdjustmentType {
  implicit val formatTaxAdjustmentType: Format[TaxAdjustmentType] = new Format[TaxAdjustmentType] {
    override def writes(taxAdjustmentType: TaxAdjustmentType): JsValue = JsString(taxAdjustmentType.toString)

    override def reads(json: JsValue): JsResult[TaxAdjustmentType] = ???
  }
}

object ReliefsGivingBackTax {
  implicit val formatReliefsGivingBackTax: Format[ReliefsGivingBackTax] = new Format[ReliefsGivingBackTax] {
    override def writes(taxAdjustmentType: ReliefsGivingBackTax): JsValue = JsString(taxAdjustmentType.toString)

    override def reads(json: JsValue): JsResult[ReliefsGivingBackTax] = ???
  }
}

object OtherTaxDue {
  implicit val formatOtherTaxDue: Format[OtherTaxDue] = new Format[OtherTaxDue] {
    override def writes(taxAdjustmentType: OtherTaxDue): JsValue = JsString(taxAdjustmentType.toString)

    override def reads(json: JsValue): JsResult[OtherTaxDue] = ???
  }
}

object AlreadyTaxedAtSource {
  implicit val formatAlreadyTaxedAtSource: Format[AlreadyTaxedAtSource] = new Format[AlreadyTaxedAtSource] {
    override def writes(taxAdjustmentType: AlreadyTaxedAtSource): JsValue = JsString(taxAdjustmentType.toString)

    override def reads(json: JsValue): JsResult[AlreadyTaxedAtSource] = ???
  }
}

object TaxReliefComponent {
  implicit val formatTaxReliefComponent: Format[TaxReliefComponent] = new Format[TaxReliefComponent] {
    override def reads(json: JsValue): JsResult[TaxReliefComponent] = ???

    override def writes(taxReliefComponent: TaxReliefComponent): JsValue = JsString(taxReliefComponent.toString)
  }
}

case class TaxAdjustmentComponent(taxAdjustmentType: TaxAdjustmentType, taxAdjustmentAmount: BigDecimal)

object TaxAdjustmentComponent {
  implicit val format: Format[TaxAdjustmentComponent] = Json.format[TaxAdjustmentComponent]
}

case class TaxAdjustment(amount: BigDecimal, taxAdjustmentComponents: Seq[TaxAdjustmentComponent])

object TaxAdjustment {
  implicit val format: Format[TaxAdjustment] = Json.format[TaxAdjustment]
}
