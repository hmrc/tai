/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.hip.reads

import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.tai.model.domain.taxAdjustments.*

object TaxAdjustmentComponentHipReads {

  private def readTaxAdjustmentComponent(
    js: JsValue,
    jsonField: String,
    componentType: TaxAdjustmentType
  ): Option[TaxAdjustmentComponent] =
    (js \ jsonField).asOpt[BigDecimal].map(amount => TaxAdjustmentComponent(componentType, amount))

  private def extractComponents(
    jsonPath: JsPath,
    fieldMappings: Seq[(String, TaxAdjustmentType)]
  ): Reads[Seq[TaxAdjustmentComponent]] =
    jsonPath.readNullable[JsObject].map { jsOpt =>
      jsOpt.toSeq.flatMap { js =>
        fieldMappings.flatMap { case (jsonField, componentType) =>
          readTaxAdjustmentComponent(js, jsonField, componentType)
        }
      }
    }

  private val taxReliefFormattersReads: Reads[Seq[TaxAdjustmentComponent]] = extractComponents(
    __ \ "totalLiabilityDetails" \ "basicRateExtensionsDetails",
    Seq(
      "personalPensionPayment"       -> PersonalPensionPayment,
      "personalPensionPaymentRelief" -> PersonalPensionPaymentRelief,
      "giftAidPaymentsRelief"        -> GiftAidPaymentsRelief,
      "giftAidPayment"               -> GiftAidPayments
    )
  )

  private val alreadyTaxedAtSourceReads: Reads[Seq[TaxAdjustmentComponent]] = extractComponents(
    __ \ "totalLiabilityDetails" \ "alreadyTaxedAtSourceDetails",
    Seq(
      "taxOnBankBSInterest"               -> TaxOnBankBSInterest,
      "taxCreditOnUKDividends"            -> TaxCreditOnUKDividends,
      "taxCreditOnForeignInterest"        -> TaxCreditOnForeignInterest,
      "taxCreditOnForeignIncomeDividends" -> TaxCreditOnForeignIncomeDividends
    )
  )

  private val otherTaxDueReads: Reads[Seq[TaxAdjustmentComponent]] = extractComponents(
    __ \ "totalLiabilityDetails" \ "otherTaxDueDetails",
    Seq(
      "excessGiftAidTax"          -> ExcessGiftAidTax,
      "excessWidowsAndOrphans"    -> ExcessWidowsAndOrphans,
      "pensionPaymentsAdjustment" -> PensionPaymentsAdjustment,
      "childBenefit"              -> ChildBenefit
    )
  )

  private val reliefsGivingBackTaxReads: Reads[Seq[TaxAdjustmentComponent]] = extractComponents(
    __ \ "totalLiabilityDetails" \ "reliefsGivingBackTaxDetails",
    Seq(
      "enterpriseInvestmentSchemeRelief" -> EnterpriseInvestmentSchemeRelief,
      "concessionalRelief"               -> ConcessionalRelief,
      "maintenancePayments"              -> MaintenancePayments,
      "marriedCouplesAllowance"          -> MarriedCouplesAllowance,
      "doubleTaxationRelief"             -> DoubleTaxationRelief
    )
  )

  val taxAdjustmentComponentReads: Reads[Seq[TaxAdjustmentComponent]] = (
    reliefsGivingBackTaxReads and
      otherTaxDueReads and
      alreadyTaxedAtSourceReads and
      taxReliefFormattersReads
  ) { (reliefsGivingBack, otherTax, alreadyTaxed, taxReliefs) =>
    reliefsGivingBack ++ otherTax ++ alreadyTaxed ++ taxReliefs
  }
}
