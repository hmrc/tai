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
import play.api.libs.json.{JsValue, Reads}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.admin.HipToggleTaxAccount
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.calculation.{CodingComponent, CodingComponentHipToggleOff, CodingComponentHipToggleOn}
import uk.gov.hmrc.tai.model.domain.taxAdjustments.TaxAdjustmentComponent.taxAdjustmentComponentHipToggleOffReads
//import uk.gov.hmrc.tai.model.domain.taxAdjustments.{GiftAidPayments, TaxAdjustment, _}
import uk.gov.hmrc.tai.model.domain.taxAdjustments.{AlreadyTaxedAtSource, OtherTaxDue, ReliefsGivingBackTax, TaxAdjustment, TaxAdjustmentComponent, TaxReliefComponent}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

// TODO: DDCNL-9376: Need to add toggle service
@Singleton
class TaxAccountHelper @Inject() (taxAccountConnector: TaxAccountConnector, featureFlagService: FeatureFlagService)(
  implicit ec: ExecutionContext
) {

  private def getReads[A](readsToggleOff: Reads[A], readsToggleOn: Reads[A]): Future[Reads[A]] =
    featureFlagService.get(HipToggleTaxAccount).map { flag =>
      if (flag.isEnabled) {
        readsToggleOn
      } else {
        readsToggleOff
      }
    }

  def totalEstimatedTax(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[BigDecimal] = {
    val componentTypesCanAffectTotalEst: Seq[TaxComponentType] =
      Seq(UnderPaymentFromPreviousYear, OutstandingDebt, EstimatedTaxYouOweThisYear)
    (for {
      readsTaxAccountSummary <- getReads(
                                  TaxOnOtherIncomeHipToggleOff.taxAccountSummaryReads,
                                  TaxOnOtherIncomeHipToggleOn.taxAccountSummaryReads
                                )
      readsCodingComponent <- getReads(
                                CodingComponentHipToggleOff.codingComponentReads,
                                CodingComponentHipToggleOn.codingComponentReads
                              )
    } yield taxAccountConnector
      .taxAccount(nino, year)
      .flatMap { taxAccount =>
        val totalTax = taxAccount.as[BigDecimal](readsTaxAccountSummary)

        println("\nDEBUG1:" + totalTax)

        val componentsCanAffectTotal = taxAccount
          .as[Seq[CodingComponent]](readsCodingComponent)
          .filter(c => componentTypesCanAffectTotalEst.contains(c.componentType))

        println("\nDEBUG2:" + componentsCanAffectTotal)

        Future(totalTax + componentsCanAffectTotal.map(_.inputAmount.getOrElse(BigDecimal(0))).sum)
      }).flatten

  }

  // TODO: DDCNL-9376 Use toggle service
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

  // TODO: DDCNL-9376 Use toggle service
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

  // TODO: DDCNL-9376 Use toggle service
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

  // TODO: DDCNL-9376 Use toggle service
  def taxOnOtherIncome(taxAccountDetails: Future[JsValue]): Future[Option[BigDecimal]] =
    getReads(
      TaxOnOtherIncomeHipToggleOff.taxOnOtherIncomeTaxValueReads,
      TaxOnOtherIncomeHipToggleOn.taxOnOtherIncomeTaxValueReads
    ).flatMap { taxOnOtherIncomeReads =>
      taxAccountDetails.map(_.as[Option[BigDecimal]](taxOnOtherIncomeReads))
    }

  // TODO: DDCNL-9376 Use toggle service
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

    for {
      readsCodingComponent <- getReads(
                                CodingComponentHipToggleOff.codingComponentReads,
                                CodingComponentHipToggleOn.codingComponentReads
                              )
      codingComponents    <- taxAccountDetails.map(_.as[Seq[CodingComponent]](readsCodingComponent))
      taxReliefComponents <- taxReliefsComponentsFuture
    } yield {
      val giftAidPayments = codingComponents.find(_.componentType == GiftAidPayments).flatMap(_.inputAmount)
      val components = giftAidPayments.collect { case amount =>
        taxReliefComponents :+ TaxAdjustmentComponent(taxAdjustments.GiftAidPayments, amount)
      } getOrElse taxReliefComponents

      if (components.nonEmpty) Some(TaxAdjustment(components.map(_.taxAdjustmentAmount).sum, components)) else None
    }
  }

  // TODO: DDCNL-9376 Use toggle service
  private[helper] def taxAdjustmentComponents(taxAccountDetails: Future[JsValue]): Future[Option[TaxAdjustment]] =
    for {
      readsTaxAdjustmentComponent <-
        getReads(taxAdjustmentComponentHipToggleOffReads, taxAdjustmentComponentHipToggleOffReads)
      taxAdjustments <-
        taxAccountDetails.map(_.as[Seq[TaxAdjustmentComponent]](readsTaxAdjustmentComponent))
    } yield
      if (taxAdjustments.nonEmpty) {
        Some(TaxAdjustment(taxAdjustments.map(_.taxAdjustmentAmount).sum, taxAdjustments))
      } else {
        None
      }

}
