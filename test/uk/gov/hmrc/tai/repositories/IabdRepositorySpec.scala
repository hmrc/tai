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
import play.api.libs.json.Json
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.connectors.cache.{Caching, IabdConnector}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.response.{HodUpdateFailure, HodUpdateSuccess}
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.cache.TaiCacheRepository
import uk.gov.hmrc.tai.util.{BaseSpec, MongoConstants}

import java.time.LocalDate
import scala.concurrent.Future

class IabdRepositorySpec extends BaseSpec with MongoConstants {

  val taiCacheRepository: TaiCacheRepository = mock[TaiCacheRepository]
  val metrics: Metrics = mock[Metrics]
  val cacheConfig: CacheMetricsConfig = mock[CacheMetricsConfig]
  val iabdConnector: IabdConnector = mock[IabdConnector]

  val iabdDetails1FromApi: IabdDetails =
    IabdDetails(Some(nino.withoutSuffix), None, Some(15), Some(10), None, Some(LocalDate.of(2017, 4, 10)))

  val iabdDetails2FromApi: IabdDetails =
    IabdDetails(Some(nino.withoutSuffix), Some(1), Some(15), Some(27), None, Some(LocalDate.of(2017, 4, 10)))

  val iabdDetailsAfterFormat: IabdDetails = IabdDetails(Some(nino.withoutSuffix), Some(1), Some(15), Some(27), None, None)
  private val jsonAfterFormat = Json.arr(
    Json.obj(
      "nino" -> nino.withoutSuffix,
      "employmentSequenceNumber" -> 1,
      "source" -> 15,
      "type" -> 27
    )
  )

  def createTestCache(cache: Caching, iabdConnector: IabdConnector) = new IabdRepository(cache, iabdConnector)


  "iabds" must {
    "return exception " when {
      "Not found is received from iabds" in {
        val cache = new Caching(taiCacheRepository, metrics, cacheConfig)
        when(iabdConnector.iabds(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.failed(new NotFoundException("iabds not found")))
        when(taiCacheRepository.findJson(any(), meq(s"$IabdMongoKey${TaxYear().year}")))
          .thenReturn(Future.successful(Some(Json.obj("error" -> "NOT_FOUND"))))

        val sut = createTestCache(cache, iabdConnector)
        val result = sut.iabds(nino, TaxYear())

        whenReady(result.failed) { ex =>
          ex mustBe a[NotFoundException]
        }
      }
    }
    "return json" when {
      "data is present in cache" in {

        val cache = new Caching(taiCacheRepository, metrics, cacheConfig)
        when(iabdConnector.iabds(meq(nino), meq(TaxYear()))(any()))
          .thenReturn(Future.successful(Seq(iabdDetailsAfterFormat)))
        when(taiCacheRepository.findJson(any(), meq(s"$IabdMongoKey${TaxYear().year}")))
          .thenReturn(Future.successful(Some(jsonAfterFormat)))

        val sut = createTestCache(cache, iabdConnector)

        val result = sut.iabds(nino, TaxYear()).futureValue
        result mustBe jsonAfterFormat
      }

      "data is not present in cache" in {
        val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

        when(taiCacheRepository.findJson(any(), meq(s"$IabdMongoKey${TaxYear().year}")))
          .thenReturn(Future.successful(None))
        when(taiCacheRepository.createOrUpdateJson(any(), any(), any())).thenReturn(Future.successful(jsonAfterFormat))
        when(iabdConnector.iabds(any(), any())(any())).thenReturn(Future.successful(Seq(iabdDetails1FromApi, iabdDetails2FromApi)))

        val sut = createTestCache(cache, iabdConnector)

        val result = sut.iabds(nino, TaxYear()).futureValue

        result mustBe jsonAfterFormat
      }
    }
  }

  "updateTaxCodeAmount" must {
    "update tax code amount" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(iabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateSuccess))

      val sut = createTestCache(cache, iabdConnector)

      val result = sut.updateTaxCodeAmount(nino, TaxYear(), 1, 1, NewEstimatedPay.code, 12345).futureValue

      result mustBe HodUpdateSuccess

    }

    "return an error status when amount can't be updated" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(iabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateFailure))

      val sut = createTestCache(cache, iabdConnector)

      val result = sut.updateTaxCodeAmount(nino, TaxYear(), 1, 1, NewEstimatedPay.code, 12345).futureValue

      result mustBe HodUpdateFailure

    }

    "update income" in {

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(iabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateSuccess))

      val sut = createTestCache(cache, iabdConnector)

      val result = sut.updateTaxCodeAmount(nino, TaxYear(), 1, 1, NewEstimatedPay.code, 12345).futureValue

      result mustBe HodUpdateSuccess
    }

    "return an error status when income can't be updated" in {
      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(iabdConnector.updateTaxCodeAmount(any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(Future.successful(HodUpdateFailure))

      val sut = createTestCache(cache, iabdConnector)

      val result = sut.updateTaxCodeAmount(nino, TaxYear(), 1, 1, NewEstimatedPay.code, 12345).futureValue

      result mustBe HodUpdateFailure
    }
  }
}
