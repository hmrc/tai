/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.tai.model.api.TaxCodeRecordWithEndDate
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


  private def getPreviousComponents(nino: Nino)(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    previousPrimaryTaxCodeRecord(nino).flatMap((id: Option[TaxCodeRecordWithEndDate]) => {
      id match {
        case Some(record) => previousCodingComponentForId(nino, record.taxCodeId)
        case None => Future.successful(Seq.empty[CodingComponent])
      }
    })
  }

  private def previousPrimaryTaxCodeRecord(nino: Nino)(implicit hc:HeaderCarrier): Future[Option[TaxCodeRecordWithEndDate]] = {
    taxCodeChangeService.taxCodeChange(nino).map(taxCodeChange => {
      taxCodeChange.previous.find(taxCodeRecord => taxCodeRecord.primary)
    })
  }

  private def previousCodingComponentForId(nino: Nino, taxCodeId: Int)(implicit hc:HeaderCarrier): Future[Seq[CodingComponent]] = {
    codingComponentService.codingComponentsForTaxCodeId(nino, taxCodeId)
  }

}
