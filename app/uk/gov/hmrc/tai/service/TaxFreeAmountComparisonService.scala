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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.tai.model.TaxFreeAmountComparison
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class TaxFreeAmountComparisonService @Inject()(taxCodeChangeService: TaxCodeChangeService,
                                               codingComponentService: CodingComponentService) {

  def taxFreeAmountComparison(nino: Nino)(implicit hc:HeaderCarrier): Future[TaxFreeAmountComparison] = {

    val currentComponents: Future[Seq[CodingComponent]] = codingComponentService.codingComponents(nino, TaxYear())
    val previousComponents: Future[Seq[CodingComponent]] = getPreviousComponents(nino)

    (for {
      current <- currentComponents
      previous <- previousComponents
    } yield {
      TaxFreeAmountComparison(previous, current)
    }).recoverWith {
      case e: Exception => Future.failed(new RuntimeException("Could not generate TaxFreeAmountComparison - " + e.getMessage))
    }
  }

  def getPreviousComponents(nino: Nino)(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    previousTaxCodeChangeIds(nino).flatMap(ids => buildPreviousCodingComponentsFromIds(nino, ids))
  }

  def previousTaxCodeChangeIds(nino: Nino)(implicit hc:HeaderCarrier): Future[Seq[Int]] = {
    taxCodeChangeService.taxCodeChange(nino).map(_.previous.map(_.taxCodeId))
  }

  def buildPreviousCodingComponentsFromIds(nino: Nino, taxCodeIds: Seq[Int])(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    Future.sequence(taxCodeIds.map(taxCodeId => {
      val response: Future[Seq[CodingComponent]] = codingComponentService.codingComponentsForTaxCodeId(nino, taxCodeId)

      response
    })).map(_.flatten).recoverWith {
      case e: Exception => Future.failed(new RuntimeException("Could not retrieve all previous coding components - " + e.getMessage))
    }
  }
}
