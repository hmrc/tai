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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.TaxCodeChangeConnector
import uk.gov.hmrc.tai.connectors.cache.Caching
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.util.MongoConstants

import scala.concurrent.Future

@Singleton
class TaxCodeChangeRepository @Inject()(cache: Caching, taxCodeChangeConnector: TaxCodeChangeConnector)
    extends MongoConstants {

  def taxCodeHistory(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] =
    cache.cacheFromApiV2[TaxCodeHistory](nino, s"TaxCodeRecords${taxYear.year}", taxCodeHistoryFromApi(nino, taxYear))

  private def taxCodeHistoryFromApi(nino: Nino, taxYear: TaxYear)(implicit hc: HeaderCarrier): Future[TaxCodeHistory] =
    taxCodeChangeConnector.taxCodeHistory(nino, taxYear)
}
