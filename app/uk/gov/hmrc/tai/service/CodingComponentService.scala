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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.TaxAccountConnector
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent
import uk.gov.hmrc.tai.model.hip.reads.CodingComponentHipReads
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CodingComponentService @Inject() (
  taxAccountConnector: TaxAccountConnector
)(implicit ec: ExecutionContext) {

  def codingComponents(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[CodingComponent]] =
    taxAccountConnector
      .taxAccount(nino, year)
      .map(_.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads))

  def codingComponentsForTaxCodeId(nino: Nino, taxCodeId: Int)(implicit
    hc: HeaderCarrier
  ): Future[Seq[CodingComponent]] =
    taxAccountConnector
      .taxAccountHistory(nino = nino, iocdSeqNo = taxCodeId)
      .map(_.as[Seq[CodingComponent]](CodingComponentHipReads.codingComponentReads))
}
