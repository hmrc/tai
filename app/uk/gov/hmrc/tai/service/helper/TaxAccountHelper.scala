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

package uk.gov.hmrc.tai.service.helper

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.TaxAccountHodFormatters
import uk.gov.hmrc.tai.model.domain.formatters.TaxAccountSummaryHodFormatters
import uk.gov.hmrc.tai.model.domain.taxAdjustments.{AlreadyTaxedAtSource, OtherTaxDue, ReliefsGivingBackTax, TaxAdjustment, TaxAdjustmentComponent, TaxReliefComponent}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxAccountHelper @Inject() ()(implicit ec: ExecutionContext)
    extends TaxAccountSummaryHodFormatters with TaxAccountHodFormatters {

  def reliefsGivingBackTaxComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] = {
    val reliefsGivingBackTaxComponents = taxAdjustmentComponents(taxAccountDetails).map {
      case Some(taxAdjustment) =>
        taxAdjustment.taxAdjustmentComponents.filter {
          _.taxAdjustmentType match {
            case _: ReliefsGivingBackTax => true
            case _                       => false
          }
        }
      case None => Seq.empty[TaxAdjustmentComponent]
    }

    for {
      reliefComponents <- reliefsGivingBackTaxComponents
    } yield
      if (reliefComponents.nonEmpty) {
        Some(TaxAdjustment(reliefComponents.map(_.taxAdjustmentAmount).sum, reliefComponents))
      } else {
        None
      }
  }

  def otherTaxDueComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] = {
    val otherTaxDueComponents = taxAdjustmentComponents(taxAccountDetails).map {
      case Some(taxAdjustment) =>
        taxAdjustment.taxAdjustmentComponents.filter {
          _.taxAdjustmentType match {
            case _: OtherTaxDue => true
            case _              => false
          }
        }
      case None => Seq.empty[TaxAdjustmentComponent]
    }

    for {
      otherTaxComponents <- otherTaxDueComponents
    } yield
      if (otherTaxComponents.nonEmpty) {
        Some(TaxAdjustment(otherTaxComponents.map(_.taxAdjustmentAmount).sum, otherTaxComponents))
      } else {
        None
      }
  }

  def alreadyTaxedAtSourceComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] = {
    val alreadyTaxedSourcesComponents = taxAdjustmentComponents(taxAccountDetails).map {
      case Some(taxAdjustment) =>
        taxAdjustment.taxAdjustmentComponents.filter {
          _.taxAdjustmentType match {
            case _: AlreadyTaxedAtSource => true
            case _                       => false
          }
        }
      case None => Seq.empty[TaxAdjustmentComponent]
    }

    for {
      alreadyTaxedAtSourceComponents <- alreadyTaxedSourcesComponents
    } yield
      if (alreadyTaxedAtSourceComponents.nonEmpty) {
        Some(
          TaxAdjustment(alreadyTaxedAtSourceComponents.map(_.taxAdjustmentAmount).sum, alreadyTaxedAtSourceComponents)
        )
      } else {
        None
      }
  }

  def taxReliefComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] = {
    lazy val taxReliefsComponentsFuture = taxAdjustmentComponents(taxAccountDetails).map {
      case Some(taxAdjustment) =>
        taxAdjustment.taxAdjustmentComponents.filter {
          _.taxAdjustmentType match {
            case _: TaxReliefComponent => true
            case _                     => false
          }
        }
      case None => Seq.empty[TaxAdjustmentComponent]
    }

    lazy val codingComponentFuture = taxAccountDetails.map(
      _.as[Seq[CodingComponent]](codingComponentReads)
    )

    for {
      taxReliefComponents <- taxReliefsComponentsFuture
      codingComponents    <- codingComponentFuture
    } yield {
      val giftAidPayments = codingComponents.find(_.componentType == GiftAidPayments).flatMap(_.inputAmount)
      val components = giftAidPayments.collect { case amount =>
        taxReliefComponents :+ TaxAdjustmentComponent(taxAdjustments.GiftAidPayments, amount)
      } getOrElse taxReliefComponents

      if (components.nonEmpty) Some(TaxAdjustment(components.map(_.taxAdjustmentAmount).sum, components)) else None
    }
  }

  def taxAdjustmentComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] = {

    val taxAdjustmentComponents = taxAccountDetails.map(
      _.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads)
    )

    for {
      taxAdjustments <- taxAdjustmentComponents
    } yield
      if (taxAdjustments.nonEmpty) {
        Some(TaxAdjustment(taxAdjustments.map(_.taxAdjustmentAmount).sum, taxAdjustments))
      } else {
        None
      }
  }
}
