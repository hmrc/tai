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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.TaxFreeAmountComparison
import uk.gov.hmrc.tai.model.api.TaxCodeSummary
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxFreeAmountComparisonService @Inject() (
  taxCodeChangeService: TaxCodeChangeServiceImpl,
  codingComponentService: CodingComponentService
)(implicit
  ec: ExecutionContext
) extends Logging {

  def taxFreeAmountComparison(nino: Nino)(implicit hc: HeaderCarrier): Future[TaxFreeAmountComparison] = {
    lazy val currentComponents: Future[Seq[CodingComponent]] = codingComponentService.codingComponents(nino, TaxYear())
    lazy val previousComponents: Future[Seq[CodingComponent]] = getPreviousComponents(nino)

    for {
      current: Seq[CodingComponent]  <- currentComponents
      previous: Seq[CodingComponent] <- previousComponents
    } yield TaxFreeAmountComparison(previous, current)
  }

  private def getPreviousComponents(nino: Nino)(implicit hc: HeaderCarrier): Future[Seq[CodingComponent]] =
    previousPrimaryTaxCodeRecord(nino).flatMap {
      case Some(record) => codingComponentService.codingComponentsForTaxCodeId(nino, record.taxCodeId)
      case None         => Future.successful(Seq.empty[CodingComponent])
    }

  private def previousPrimaryTaxCodeRecord(nino: Nino)(implicit hc: HeaderCarrier): Future[Option[TaxCodeSummary]] =
    taxCodeChangeService.taxCodeChange(nino).map(taxCodeChange => taxCodeChange.primaryPreviousRecord)

}
