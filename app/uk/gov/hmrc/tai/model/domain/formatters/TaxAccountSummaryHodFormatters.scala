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

package uk.gov.hmrc.tai.model.domain.formatters

import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain.taxAdjustments._

import scala.annotation.tailrec

trait TaxAccountSummaryHodFormatters extends TaxOnOtherIncomeFormatters with ReliefsGivingBackTaxFormatters
  with OtherTaxDueFormatters with AlreadyTaxedAtSourceFormatters {

  val taxAccountSummaryReads = new Reads[BigDecimal] {
    override def reads(json: JsValue): JsResult[BigDecimal] = {
      val taxOnOtherIncome = json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) map(_.tax) getOrElse BigDecimal(0)
      val totalLiabilityTax = (json \ "totalLiability" \ "totalLiability").asOpt[BigDecimal].getOrElse(BigDecimal(0))

      JsSuccess(totalLiabilityTax - taxOnOtherIncome)
    }
  }

  val taxOnOtherIncomeRead = new Reads[Option[BigDecimal]] {
    override def reads(json: JsValue): JsResult[Option[BigDecimal]] = {
      JsSuccess(json.as[Option[TaxOnOtherIncome]](taxOnOtherIncomeReads) map(_.tax))
    }
  }

  val taxAdjustmentComponentReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] = {
      val reliefs = json.as[Seq[TaxAdjustmentComponent]](reliefsGivingBackTaxReads)
      val otherTaxDues = json.as[Seq[TaxAdjustmentComponent]](otherTaxDueReads)
      val alreadyTaxedAtSources = json.as[Seq[TaxAdjustmentComponent]](alreadyTaxedAtSourceReads)
      JsSuccess(reliefs ++ otherTaxDues ++ alreadyTaxedAtSources)
    }
  }
}

trait TaxOnOtherIncomeFormatters extends BaseTaxAccountHodFormatters {
  private val NonCodedIncome = 19

  val taxOnOtherIncomeReads = new Reads[Option[TaxOnOtherIncome]] {
    override def reads(json: JsValue): JsResult[Option[TaxOnOtherIncome]] = {
      val iabdSummaries = totalLiabilityIabds(json, "totalIncome", Seq("nonSavings"))
      val nonCodedIncomeAmount = iabdSummaries.find(_.componentType == NonCodedIncome).map(_.amount)

      @tailrec
      def calculateTaxOnOtherIncome(incomeAndRateBands: Seq[RateBand], nonCodedIncome: BigDecimal, total: BigDecimal = 0) : BigDecimal = {
        incomeAndRateBands match {
          case Nil => total
          case xs if nonCodedIncome > xs.head.income =>
            val newTotal = xs.head.income * (xs.head.rate / 100)
            calculateTaxOnOtherIncome(xs.tail, nonCodedIncome - xs.head.income, total + newTotal)
          case xs if nonCodedIncome <= xs.head.income =>
            val newTotal = nonCodedIncome * (xs.head.rate / 100)
            total + newTotal
        }
      }


      (nonCodedIncomeAmount, incomeAndRateBands(json)) match {
        case (None, _) => JsSuccess(None)
        case (Some(_), Nil) => JsSuccess(None)
        case (Some(amount), incomeAndRateBands) =>
          val remainingTaxOnOtherIncome = calculateTaxOnOtherIncome(incomeAndRateBands, amount)
          JsSuccess(Some(TaxOnOtherIncome(remainingTaxOnOtherIncome)))
      }

    }
  }

  private def incomeAndRateBands(json: JsValue): Seq[RateBand] = {
    val bands = (json \ "totalLiability" \ "nonSavings" \ "taxBands").asOpt[JsArray]
    val details = bands.map(_.value.collect {
      case js if (js \ "income").asOpt[BigDecimal].isDefined =>
        RateBand((js \ "income").as[BigDecimal], (js \ "rate").as[BigDecimal])
    })

    details match {
      case Some(rateBands) => rateBands.sortBy(- _.rate)
      case None => Seq.empty[RateBand]
    }
  }
}

trait ReliefsGivingBackTaxFormatters extends CommonFormatters {
  val reliefsGivingBackTaxReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] = {
      (json \ "totalLiability" \ "reliefsGivingBackTax").asOpt[JsObject] match {
        case Some(js) =>
          val enterpriseInvestment = readTaxAdjustmentComponent(js, "enterpriseInvestmentSchemeRelief", EnterpriseInvestmentSchemeRelief)
          val concession = readTaxAdjustmentComponent(js, "concessionalRelief", ConcessionalRelief)
          val maintenancePayments = readTaxAdjustmentComponent(js, "maintenancePayments", MaintenancePayments)
          val marriedCouplesAllowance = readTaxAdjustmentComponent(js, "marriedCouplesAllowance", MarriedCouplesAllowance)
          val doubleTaxation = readTaxAdjustmentComponent(js, "doubleTaxationRelief", DoubleTaxationRelief)

          JsSuccess(flattenTaxAdjustmentComponents(enterpriseInvestment, concession, maintenancePayments, marriedCouplesAllowance, doubleTaxation))
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
    }
  }
}

trait OtherTaxDueFormatters extends CommonFormatters {
  val otherTaxDueReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] = {
      (json \ "totalLiability" \ "otherTaxDue").asOpt[JsObject] match {
        case Some(js) =>
          val excessGiftAidTax = readTaxAdjustmentComponent(js, "excessGiftAidTax", ExcessGiftAidTax)
          val excessWidowsAndOrphans = readTaxAdjustmentComponent(js, "excessWidowsAndOrphans", ExcessWidowsAndOrphans)
          val pensionPaymentsAdjustment = readTaxAdjustmentComponent(js, "pensionPaymentsAdjustment", PensionPaymentsAdjustment)
          val childBenefit = readTaxAdjustmentComponent(js, "childBenefit", ChildBenefit)

          JsSuccess(flattenTaxAdjustmentComponents(excessGiftAidTax, excessWidowsAndOrphans, pensionPaymentsAdjustment, childBenefit))
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
    }
  }
}

trait AlreadyTaxedAtSourceFormatters extends CommonFormatters {
  val alreadyTaxedAtSourceReads = new Reads[Seq[TaxAdjustmentComponent]] {
    override def reads(json: JsValue): JsResult[Seq[TaxAdjustmentComponent]] = {
      (json \ "totalLiability" \ "alreadyTaxedAtSource").asOpt[JsObject] match {
        case Some(js) =>
          val taxOnBankInterest = readTaxAdjustmentComponent(js, "taxOnBankBSInterest", TaxOnBankBSInterest)
          val taxOnUkDividends = readTaxAdjustmentComponent(js, "taxCreditOnUKDividends", TaxCreditOnUKDividends)
          val taxOnForeignInterest = readTaxAdjustmentComponent(js, "taxCreditOnForeignInterest", TaxCreditOnForeignInterest)
          val taxOnForeignDividends = readTaxAdjustmentComponent(js, "taxCreditOnForeignIncomeDividends", TaxCreditOnForeignIncomeDividends)

          JsSuccess(flattenTaxAdjustmentComponents(taxOnBankInterest, taxOnUkDividends, taxOnForeignInterest, taxOnForeignDividends))
        case _ => JsSuccess(Seq.empty[TaxAdjustmentComponent])
      }
    }
  }
}

sealed trait CommonFormatters {
  def flattenTaxAdjustmentComponents(components: Option[TaxAdjustmentComponent]*): Seq[TaxAdjustmentComponent] = {
    components.toSeq.flatten
  }

  def readTaxAdjustmentComponent(json: JsValue, taxAdjustmentTypeInJson: String, taxAdjustmentType: TaxAdjustmentType): Option[TaxAdjustmentComponent] = {
    (json \ taxAdjustmentTypeInJson).asOpt[BigDecimal].collect {
      case amount => TaxAdjustmentComponent(taxAdjustmentType, amount)
    }
  }
}

private case class RateBand(income: BigDecimal, rate: BigDecimal)

case class TaxOnOtherIncome(tax: BigDecimal)
