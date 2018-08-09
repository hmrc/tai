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

import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.domain.taxCodeChange.TaxCodeChange
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future

class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector) extends TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean] = {
    val currentYear = TaxYear()

    taxCodeChangeConnector.taxCodeHistory(nino, currentYear) map {
      _.taxCodeRecord
        .exists(
          _.exists(record => TaxYear(record.p2Date) == currentYear))
    }
  }

  def taxCodeHistory(nino: Nino): Future[Seq[TaxCodeChange]] = {
    val currentYear = TaxYear()
    taxCodeChangeConnector.taxCodeHistory(nino, currentYear) map {
      _.taxCodeRecord match {
        case Some(taxCodeRecord) =>
          taxCodeRecord.map(taxCodeRecord =>
            new TaxCodeChange(taxCodeRecord.taxCode, taxCodeRecord.p2Date, TaxYearResolver.endOfCurrentTaxYear, taxCodeRecord.employerName))
        }
    }
  }

}


@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean]

  def taxCodeHistory(nino: Nino): Future[Seq[TaxCodeChange]]

}
