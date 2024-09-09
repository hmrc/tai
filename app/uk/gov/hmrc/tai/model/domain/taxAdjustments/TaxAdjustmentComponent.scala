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

  def flattenTaxAdjustmentComponents(components: Option[TaxAdjustmentComponent]*): Seq[TaxAdjustmentComponent] =
    components.toSeq.flatten

  def readTaxAdjustmentComponent(
    json: JsValue,
    taxAdjustmentTypeInJson: String,
    taxAdjustmentType: TaxAdjustmentType
  ): Option[TaxAdjustmentComponent] =
    (json \ taxAdjustmentTypeInJson).asOpt[BigDecimal].collect { case amount =>
      TaxAdjustmentComponent(taxAdjustmentType, amount)
    }

  val taxReliefFormattersReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] =
      (json \ "totalLiability" \ "basicRateExtensions").asOpt[JsObject] match {
        case Some(js) =>
          val personalPensionPayment = readTaxAdjustmentComponent(js, "personalPensionPayment", PersonalPensionPayment)
          val personalPensionPaymentRelief =
            readTaxAdjustmentComponent(js, "personalPensionPaymentRelief", PersonalPensionPaymentRelief)
          val giftAidPaymentsRelief = readTaxAdjustmentComponent(js, "giftAidPaymentsRelief", GiftAidPaymentsRelief)
          val giftAidPayments = readTaxAdjustmentComponent(js, "giftAidPayments", GiftAidPayments)

          JsSuccess(
            flattenTaxAdjustmentComponents(
              personalPensionPayment,
              personalPensionPaymentRelief,
              giftAidPaymentsRelief,
              giftAidPayments
            )
          )
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
  }

  val alreadyTaxedAtSourceReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] =
      (json \ "totalLiability" \ "alreadyTaxedAtSource").asOpt[JsObject] match {
        case Some(js) =>
          val taxOnBankInterest = readTaxAdjustmentComponent(js, "taxOnBankBSInterest", TaxOnBankBSInterest)
          val taxOnUkDividends = readTaxAdjustmentComponent(js, "taxCreditOnUKDividends", TaxCreditOnUKDividends)
          val taxOnForeignInterest =
            readTaxAdjustmentComponent(js, "taxCreditOnForeignInterest", TaxCreditOnForeignInterest)
          val taxOnForeignDividends =
            readTaxAdjustmentComponent(js, "taxCreditOnForeignIncomeDividends", TaxCreditOnForeignIncomeDividends)

          JsSuccess(
            flattenTaxAdjustmentComponents(
              taxOnBankInterest,
              taxOnUkDividends,
              taxOnForeignInterest,
              taxOnForeignDividends
            )
          )
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
  }

  val otherTaxDueReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] =
      (json \ "totalLiability" \ "otherTaxDue").asOpt[JsObject] match {
        case Some(js) =>
          val excessGiftAidTax = readTaxAdjustmentComponent(js, "excessGiftAidTax", ExcessGiftAidTax)
          val excessWidowsAndOrphans = readTaxAdjustmentComponent(js, "excessWidowsAndOrphans", ExcessWidowsAndOrphans)
          val pensionPaymentsAdjustment =
            readTaxAdjustmentComponent(js, "pensionPaymentsAdjustment", PensionPaymentsAdjustment)
          val childBenefit = readTaxAdjustmentComponent(js, "childBenefit", ChildBenefit)

          JsSuccess(
            flattenTaxAdjustmentComponents(
              excessGiftAidTax,
              excessWidowsAndOrphans,
              pensionPaymentsAdjustment,
              childBenefit
            )
          )
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
  }

  val reliefsGivingBackTaxReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] =
      (json \ "totalLiability" \ "reliefsGivingBackTax").asOpt[JsObject] match {
        case Some(js) =>
          val enterpriseInvestment =
            readTaxAdjustmentComponent(js, "enterpriseInvestmentSchemeRelief", EnterpriseInvestmentSchemeRelief)
          val concession = readTaxAdjustmentComponent(js, "concessionalRelief", ConcessionalRelief)
          val maintenancePayments = readTaxAdjustmentComponent(js, "maintenancePayments", MaintenancePayments)
          val marriedCouplesAllowance =
            readTaxAdjustmentComponent(js, "marriedCouplesAllowance", MarriedCouplesAllowance)
          val doubleTaxation = readTaxAdjustmentComponent(js, "doubleTaxationRelief", DoubleTaxationRelief)

          JsSuccess(
            flattenTaxAdjustmentComponents(
              enterpriseInvestment,
              concession,
              maintenancePayments,
              marriedCouplesAllowance,
              doubleTaxation
            )
          )
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
  }

  // TODO: DDCNL-9376 Need version of tax-account toggled on
  val taxAdjustmentComponentReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] = {
      val reliefsGivingBackComponents = json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads)
      val otherTaxDues = json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads)
      val alreadyTaxedAtSources = json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads)
      val taxReliefComponent = json.as[Seq[TaxAdjustmentComponent]](taxReliefFormattersReads)
      JsSuccess(reliefsGivingBackComponents ++ otherTaxDues ++ alreadyTaxedAtSources ++ taxReliefComponent)
    }
  }
}

case class TaxAdjustment(amount: BigDecimal, taxAdjustmentComponents: Seq[TaxAdjustmentComponent])

object TaxAdjustment {
  implicit val format: Format[TaxAdjustment] = Json.format[TaxAdjustment]
}
