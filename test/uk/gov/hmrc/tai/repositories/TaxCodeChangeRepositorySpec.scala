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
import org.mockito.ArgumentMatchersSugar.eqTo
import uk.gov.hmrc.mongoFeatureToggles.model.{FeatureFlag, FeatureFlagName}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.connectors.IfConnector
import uk.gov.hmrc.tai.connectors.cache.Caching
import uk.gov.hmrc.tai.connectors.deprecated.TaxCodeChangeFromDesConnector
import uk.gov.hmrc.tai.factory.TaxCodeHistoryFactory
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.admin.TaxCodeHistoryFromDESToggle
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.{TaiCacheRepository, TaxCodeChangeRepository}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class TaxCodeChangeRepositorySpec extends BaseSpec {

  val metrics: Metrics = mock[Metrics]
  val cacheConfig: CacheMetricsConfig = mock[CacheMetricsConfig]
  val taiCacheRepository: TaiCacheRepository = mock[TaiCacheRepository]

  val taxCodeChangeFromDesConnector: TaxCodeChangeFromDesConnector = mock[TaxCodeChangeFromDesConnector]
  val ifConnector: IfConnector = mock[IfConnector]

  val taxCodeHistory: TaxCodeHistory = TaxCodeHistoryFactory.createTaxCodeHistory(nino)

  lazy val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  def createSUT(cache: Caching, taxCodeChangeFromDesConnector: TaxCodeChangeFromDesConnector,
                ifConnector: IfConnector, mockFeatureFlagService: FeatureFlagService) =
    new TaxCodeChangeRepository(cache, taxCodeChangeFromDesConnector, ifConnector, mockFeatureFlagService)

  override def beforeEach(): Unit ={
    super.beforeEach()
    reset(metrics, cacheConfig, taiCacheRepository, taxCodeChangeFromDesConnector, ifConnector)
  }

  "taxCodeHistory" must {
    "return an existing tax code history" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)
      val taxCodeHistory2 = taxCodeHistory.copy(taxCodeRecord = Seq())

      when(taxCodeChangeFromDesConnector.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromDESToggle))).thenReturn(
        Future.successful(FeatureFlag(TaxCodeHistoryFromDESToggle, isEnabled = true))
      )

      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory)))
      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().prev.year}"))(any()))
        .thenReturn(Future.successful(Some(taxCodeHistory2)))

      val SUT = createSUT(cache, taxCodeChangeFromDesConnector, ifConnector, mockFeatureFlagService)

      val result = SUT.taxCodeHistory(nino, TaxYear()).futureValue
      val result2 = SUT.taxCodeHistory(nino, TaxYear().prev).futureValue

      result mustBe taxCodeHistory
      result2 mustBe taxCodeHistory2

      verify(taxCodeChangeFromDesConnector, times(0)).taxCodeHistory(any(), any())(any())
      verify(ifConnector, times(0)).taxCodeHistory(any(), any())(any())

      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any())
      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().prev.year}"))(any())
      verify(taiCacheRepository, times(0)).createOrUpdate[TaxCodeHistory](any(), any(), any())(any())

    }

    "create a new tax code history in the cache and return it" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(taxCodeChangeFromDesConnector.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromDESToggle))).thenReturn(
        Future.successful(FeatureFlag(TaxCodeHistoryFromDESToggle, isEnabled = true))
      )

      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(None))

      when(taiCacheRepository.createOrUpdate[TaxCodeHistory](meq(cacheId), any(), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      val SUT = createSUT(cache, taxCodeChangeFromDesConnector, ifConnector, mockFeatureFlagService)

      val result = SUT.taxCodeHistory(nino, TaxYear()).futureValue

      result mustBe taxCodeHistory

      verify(taxCodeChangeFromDesConnector, times(1)).taxCodeHistory(any(), any())(any())
      verify(ifConnector, times(0)).taxCodeHistory(any(), any())(any())

      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any())
      verify(taiCacheRepository, times(1)).createOrUpdate[TaxCodeHistory](any(), any(), meq(s"TaxCodeRecords${TaxYear().year}"))(any())

    }

    "get the tax code history from the if when the TaxCodeHistoryFromDESToggle is false" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(ifConnector.taxCodeHistory(any(), any())(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      when(mockFeatureFlagService.get(eqTo[FeatureFlagName](TaxCodeHistoryFromDESToggle))).thenReturn(
        Future.successful(FeatureFlag(TaxCodeHistoryFromDESToggle, isEnabled = false))
      )

      when(taiCacheRepository.find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(None))

      when(taiCacheRepository.createOrUpdate[TaxCodeHistory](meq(cacheId), any(), meq(s"TaxCodeRecords${TaxYear().year}"))(any()))
        .thenReturn(Future.successful(taxCodeHistory))

      val SUT = createSUT(cache, taxCodeChangeFromDesConnector, ifConnector, mockFeatureFlagService)

      val result = SUT.taxCodeHistory(nino, TaxYear()).futureValue

      result mustBe taxCodeHistory

      verify(taxCodeChangeFromDesConnector, times(0)).taxCodeHistory(any(), any())(any())
      verify(ifConnector, times(1)).taxCodeHistory(any(), any())(any())
      verify(taiCacheRepository, times(1)).find[TaxCodeHistory](meq(cacheId), meq(s"TaxCodeRecords${TaxYear().year}"))(any())
      verify(taiCacheRepository, times(1)).createOrUpdate[TaxCodeHistory](any(), any(), meq(s"TaxCodeRecords${TaxYear().year}"))(any())

    }
  }
}
