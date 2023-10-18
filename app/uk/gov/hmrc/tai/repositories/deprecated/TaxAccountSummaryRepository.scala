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

package uk.gov.hmrc.tai.repositories.deprecated

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.formatters.TaxAccountSummaryHodFormatters
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.taxAdjustments.{AlreadyTaxedAtSource, OtherTaxDue, ReliefsGivingBackTax, TaxAdjustment, TaxAdjustmentComponent, TaxReliefComponent}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxAccountSummaryRepository @Inject()(
  taxAccountRepository: TaxAccountRepository,
  codingComponentRepository: CodingComponentRepository)(implicit ec: ExecutionContext)
    extends TaxAccountSummaryHodFormatters {

  def taxAccountSummary(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[BigDecimal] = {
    val componentTypesCanAffectTotalEst: Seq[TaxComponentType] =
      Seq(UnderPaymentFromPreviousYear, OutstandingDebt, EstimatedTaxYouOweThisYear)

    for {
      totalTax <- taxAccountRepository.taxAccount(nino, year) map (_.as[BigDecimal](taxAccountSummaryReads))
      componentsCanAffectTotal <- codingComponentRepository
                                   .codingComponents(nino, year)
                                   .map(
                                     _.filter(
                                       c => componentTypesCanAffectTotalEst.contains(c.componentType)
                                     ))
    } yield totalTax + componentsCanAffectTotal.map(_.inputAmount.getOrElse(BigDecimal(0))).sum
  }

  def reliefsGivingBackTaxComponents(nino: Nino, year: TaxYear)(
    implicit hc: HeaderCarrier): Future[Option[TaxAdjustment]] = {
    val reliefsGivingBackTaxComponents = taxAdjustmentComponents(nino, year).map {
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
      if (reliefComponents.nonEmpty)
        Some(TaxAdjustment(reliefComponents.map(_.taxAdjustmentAmount).sum, reliefComponents))
      else None
  }

  def otherTaxDueComponents(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Option[TaxAdjustment]] = {
    val otherTaxDueComponents = taxAdjustmentComponents(nino, year).map {
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
      if (otherTaxComponents.nonEmpty)
        Some(TaxAdjustment(otherTaxComponents.map(_.taxAdjustmentAmount).sum, otherTaxComponents))
      else None
  }

  def alreadyTaxedAtSourceComponents(nino: Nino, year: TaxYear)(
    implicit hc: HeaderCarrier): Future[Option[TaxAdjustment]] = {
    val alreadyTaxedSourcesComponents = taxAdjustmentComponents(nino, year).map {
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
      if (alreadyTaxedAtSourceComponents.nonEmpty)
        Some(
          TaxAdjustment(alreadyTaxedAtSourceComponents.map(_.taxAdjustmentAmount).sum, alreadyTaxedAtSourceComponents))
      else None
  }

  def taxReliefComponents(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Option[TaxAdjustment]] = {
    lazy val taxReliefsComponentsFuture = taxAdjustmentComponents(nino, year).map {
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
      taxReliefComponents <- taxReliefsComponentsFuture
      codingComponents    <- codingComponentRepository.codingComponents(nino, year)
    } yield {
      val giftAidPayments = codingComponents.find(_.componentType == GiftAidPayments).flatMap(_.inputAmount)
      val components = giftAidPayments.collect {
        case amount => taxReliefComponents :+ TaxAdjustmentComponent(taxAdjustments.GiftAidPayments, amount)
      } getOrElse taxReliefComponents

      if (components.nonEmpty) Some(TaxAdjustment(components.map(_.taxAdjustmentAmount).sum, components)) else None
    }
  }

  def taxAdjustmentComponents(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Option[TaxAdjustment]] = {
    val taxAdjustmentComponents = taxAccountRepository
      .taxAccount(nino, year) map (_.as[Seq[TaxAdjustmentComponent]](taxAdjustmentComponentReads))

    for {
      taxAdjustments <- taxAdjustmentComponents
    } yield {
      if (taxAdjustments.nonEmpty) Some(TaxAdjustment(taxAdjustments.map(_.taxAdjustmentAmount).sum, taxAdjustments))
      else None
    }
  }

  def taxOnOtherIncome(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Option[BigDecimal]] =
    taxAccountRepository.taxAccount(nino, year) map (_.as[Option[BigDecimal]](taxOnOtherIncomeRead))
}
