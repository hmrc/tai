/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors.cache

import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, JsObject, Reads, Writes}
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.auth.MicroserviceAuthorisedFunctions
import uk.gov.hmrc.tai.connectors._
import uk.gov.hmrc.tai.factory.TaxCodeHistoryFactory
import uk.gov.hmrc.tai.model.TaxCodeHistory
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository
import uk.gov.hmrc.tai.service.{LockService, SensitiveFormatService}

import scala.concurrent.Future

class CachingTaxCodeHistoryConnectorSpec extends ConnectorBaseSpec {

  private val mockEncryptionService = mock[SensitiveFormatService]

  lazy val mockSessionCacheRepository: TaiSessionCacheRepository = mock[TaiSessionCacheRepository]
  lazy val mockDefaultTaxCodeHistoryConnector = mock[DefaultTaxCodeHistoryConnector]

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .disable[uk.gov.hmrc.tai.modules.LocalGuiceModule]
    .overrides(
      bind[TaiSessionCacheRepository].toInstance(mockSessionCacheRepository),
      bind[FeatureFlagService].toInstance(mockFeatureFlagService),
      bind[TaxCodeHistoryConnector].to[CachingTaxCodeHistoryConnector],
      bind[TaxCodeHistoryConnector].qualifiedWith("default").toInstance(mockDefaultTaxCodeHistoryConnector),
      bind[RtiConnector].to[DefaultRtiConnector],
      bind[IabdConnector].to[DefaultIabdConnector],
      bind[EmploymentDetailsConnector].to[DefaultEmploymentDetailsConnector],
      bind[TaxAccountConnector].to[DefaultTaxAccountConnector],
      bind[AuthorisedFunctions].to[MicroserviceAuthorisedFunctions],
      bind[LockService].toInstance(spy(new FakeLockService)),
      bind[SensitiveFormatService].toInstance(mockEncryptionService)
    )
    .build()

  private val taxYear = TaxYear()

  val cachingTaxCodeHistoryConnector = app.injector.instanceOf[CachingTaxCodeHistoryConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSessionCacheRepository, mockDefaultTaxCodeHistoryConnector, mockEncryptionService)
    when(mockEncryptionService.sensitiveFormatFromReadsWrites[JsObject])
      .thenAnswer((reads: Reads[JsObject], writes: Writes[JsObject]) => Format(reads, writes))
  }

  "taxCodeHistory" when {
    "no value is present in the cache" must {
      "call the underlying connector and cache the result" in {
        when(mockSessionCacheRepository.getFromSession[TaxCodeHistory](DataKey(any()))(any(), any()))
          .thenReturn(Future.successful(None))

        when(mockSessionCacheRepository.putSession[TaxCodeHistory](DataKey(any()), any())(any(), any(), any()))
          .thenReturn(Future.successful(("", "")))

        when(mockDefaultTaxCodeHistoryConnector.taxCodeHistory(any(), any())(any()))
          .thenReturn(Future.successful(TaxCodeHistoryFactory.createTaxCodeHistory(nino)))

        val result = cachingTaxCodeHistoryConnector.taxCodeHistory(nino, taxYear).futureValue

        result mustBe TaxCodeHistoryFactory.createTaxCodeHistory(nino)
        verify(mockDefaultTaxCodeHistoryConnector, times(1)).taxCodeHistory(any(), any())(any())
        verify(mockSessionCacheRepository, times(1)).getFromSession[TaxCodeHistory](DataKey(any()))(any(), any())
        verify(mockSessionCacheRepository, times(1))
          .putSession[TaxCodeHistory](DataKey(any()), any())(any(), any(), any())
        verify(mockEncryptionService, times(1)).sensitiveFormatFromReadsWrites(any(), any())
      }
    }
    "there is a value present in the cache" must {
      "return the cached value and make no calls through the default connector" in {
        when(mockSessionCacheRepository.getFromSession[TaxCodeHistory](DataKey(any[String]()))(any(), any()))
          .thenReturn(Future.successful(Some(TaxCodeHistoryFactory.createTaxCodeHistory(nino))))

        when(mockDefaultTaxCodeHistoryConnector.taxCodeHistory(any(), any())(any()))
          .thenReturn(null)

        val result = cachingTaxCodeHistoryConnector.taxCodeHistory(nino, taxYear).futureValue

        result mustBe TaxCodeHistoryFactory.createTaxCodeHistory(nino)
        verify(mockSessionCacheRepository, times(1)).getFromSession[TaxCodeHistory](DataKey(any()))(any(), any())
        verify(mockDefaultTaxCodeHistoryConnector, times(0)).taxCodeHistory(any(), any())(any())
        verify(mockSessionCacheRepository, times(0))
          .putSession[TaxCodeHistory](DataKey(any()), any())(any(), any(), any())
      }
    }
  }
}
