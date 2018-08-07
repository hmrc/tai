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
import org.joda.time.format.DateTimeFormat
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.TaiConstants

import scala.concurrent.Future

class TaxCodeChangeServiceImpl @Inject()(taxCodeChangeConnector: TaxCodeChangeConnector) extends TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean] = {
    val currentYear = TaxYear()

    taxCodeHistory(nino) map {
      _.taxCodeRecord
        .exists(
          _.exists(record => TaxYear(record.p2Date) == currentYear))
    }
  }

  def taxCodeHistory(nino: Nino): Future[TaxCodeHistory] = {
    val currentYear = TaxYear()
    taxCodeChangeConnector.taxCodeHistory(nino, currentYear)
  }

}


@ImplementedBy(classOf[TaxCodeChangeServiceImpl])
trait TaxCodeChangeService {

  def hasTaxCodeChanged(nino: Nino): Future[Boolean]

  def taxCodeHistory(nino: Nino): Future[TaxCodeHistory]

}
