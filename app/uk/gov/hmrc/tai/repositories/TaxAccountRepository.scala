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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.model.domain.response.HodUpdateResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.MongoConstants

import scala.concurrent.Future

@Singleton
class TaxAccountRepository @Inject()(cache: Caching, taxAccountConnector: TaxAccountConnector) extends MongoConstants {

  def taxAccount(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    cache.cacheFromApi(nino, s"$TaxAccountBaseKey${taxYear.year}", taxAccountFromApi(nino: Nino, taxYear: TaxYear))

  def taxAccountForTaxCodeId(nino: Nino, taxCodeId: Int)(implicit hc: HeaderCarrier): Future[JsValue] =
    taxAccountConnector.taxAccountHistory(nino = nino, iocdSeqNo = taxCodeId)

  private def taxAccountFromApi(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[JsValue] =
    taxAccountConnector.taxAccount(nino, taxYear)

  def updateTaxCodeAmount(nino: Nino, taxYear: TaxYear, version: Int, employmentId: Int, iabdType: Int, amount: Int)(
    implicit hc: HeaderCarrier): Future[HodUpdateResponse] =
    taxAccountConnector.updateTaxCodeAmount(nino, taxYear, employmentId, version, iabdType, amount)
}
