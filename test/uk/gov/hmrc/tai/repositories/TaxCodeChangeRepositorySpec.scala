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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.connectors.cache.Caching
import uk.gov.hmrc.tai.factory.TaxCodeHistoryFactory
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec
import scala.concurrent.Future

class TaxCodeChangeRepositorySpec extends BaseSpec {

  val metrics = mock[Metrics]
  val cacheConfig = mock[CacheMetricsConfig]
  val taiCacheRepository = mock[TaiCacheRepository]

  val taxCodeChangeConnector = mock[TaxCodeChangeConnector]
  val taxCodeHistory = TaxCodeHistoryFactory.createTaxCodeHistory(nino)

  def createSUT(cache: Caching, taxCodeChangeConnector: TaxCodeChangeConnector) =
    new TaxCodeChangeRepository(cache, taxCodeChangeConnector)

  "taxCodeHistory" must {
    "return an existing tax code history" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)
      val taxCodeHistory2 = taxCodeHistory.copy(taxCodeRecord = Seq())

      when(taxCodeChangeConnector.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory)))
      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().prev.year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory2)))

      val SUT = createSUT(cache, taxCodeChangeConnector)

      val result = SUT.taxCodeHistory(nino, TaxYear()).futureValue
      val result2 = SUT.taxCodeHistory(nino, TaxYear().prev).futureValue

      result mustBe taxCodeHistory
      result2 mustBe taxCodeHistory2

      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any())
      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().prev.year}"))(any())

    }
    "create a new tax code history in the cache " in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)
      val taxCodeHistory2 = taxCodeHistory.copy(taxCodeRecord = Seq())

      when(taxCodeChangeConnector.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory)))
      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().prev.year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory2)))

      val SUT = createSUT(cache, taxCodeChangeConnector)

      val result = SUT.taxCodeHistory(nino, TaxYear()).futureValue
      val result2 = SUT.taxCodeHistory(nino, TaxYear().prev).futureValue

      result mustBe taxCodeHistory
      result2 mustBe taxCodeHistory2
    }
  }
}
