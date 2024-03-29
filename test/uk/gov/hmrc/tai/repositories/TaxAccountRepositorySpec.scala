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
import uk.gov.hmrc.tai.config.CacheMetricsConfig
import uk.gov.hmrc.tai.connectors.cache.Caching
import uk.gov.hmrc.tai.connectors.deprecated.TaxAccountConnector
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.deprecated.{TaiCacheRepository, TaxAccountRepository}
import uk.gov.hmrc.tai.util.{BaseSpec, MongoConstants}

import scala.concurrent.Future

class TaxAccountRepositorySpec extends BaseSpec with MongoConstants {

  val metrics: Metrics = mock[Metrics]
  val cacheConfig: CacheMetricsConfig = mock[CacheMetricsConfig]
  val taiCacheRepository: TaiCacheRepository = mock[TaiCacheRepository]

  val taxAccountConnector: TaxAccountConnector = mock[TaxAccountConnector]

  private val taxAccountJsonResponse = Json.obj(
    "taxYear" -> 2017,
    "totalLiability" -> Json.obj("untaxedInterest" -> Json.obj("totalTaxableIncome" -> 123)),
    "incomeSources" -> Json.arr(
      Json.obj("employmentId" -> 1, "taxCode" -> "1150L", "name" -> "Employer1", "basisOperation" -> 1),
      Json.obj("employmentId" -> 2, "taxCode" -> "1100L", "name" -> "Employer2", "basisOperation" -> 2)
    )
  )

  def createSUT(cache: Caching, taxAccountConnector: TaxAccountConnector) =
    new TaxAccountRepository(cache, taxAccountConnector)

    "taxAccount" must {
      "return Tax Account as Json in the response" when {
        "tax account is in cache" in {
          val taxYear = TaxYear(2017)

          val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

          when(taiCacheRepository.findJson(meq(cacheId), meq(s"$TaxAccountBaseKey${taxYear.year}")))
            .thenReturn(Future.successful(Some(taxAccountJsonResponse)))

          val sut = createSUT(cache, taxAccountConnector)

          val result = sut.taxAccount(nino, taxYear).futureValue

          result mustBe taxAccountJsonResponse
        }
      }
    }

    "tax account is NOT in cache" in {
      val taxYear = TaxYear(2017)

      val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

      when(taiCacheRepository.findJson(meq(cacheId), meq(s"$TaxAccountBaseKey${taxYear.year}")))
        .thenReturn(Future.successful(None))

      when(
        taiCacheRepository
          .createOrUpdateJson(meq(cacheId), meq(taxAccountJsonResponse), meq(s"$TaxAccountBaseKey${taxYear.year}")))
        .thenReturn(Future.successful(taxAccountJsonResponse))

      when(taxAccountConnector.taxAccount(meq(nino), meq(taxYear))(any()))
        .thenReturn(Future.successful(taxAccountJsonResponse))

      val sut = createSUT(cache, taxAccountConnector)
      val result = sut.taxAccount(nino, taxYear).futureValue

      result mustBe taxAccountJsonResponse
    }

    "taxAccountForTaxCodeId" must {
      "return json from the taxAccountHistoryConnector" in {
        val taxCodeId = 1

        val cache = new Caching(taiCacheRepository, metrics, cacheConfig)

        val sut = createSUT(cache, taxAccountConnector)

        val jsonResponse = Future.successful(Json.obj())

        when(taxAccountConnector.taxAccountHistory(meq(nino), meq(taxCodeId))(any()))
          .thenReturn(jsonResponse)

        val result = sut.taxAccountForTaxCodeId(nino, taxCodeId)

        result mustEqual jsonResponse
      }
    }
}
