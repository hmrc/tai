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

package uk.gov.hmrc.tai.repositories.cache

import org.mockito.ArgumentMatchers.{any, anyString}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.tai.config.{MongoConfig, SensitiveT}
import uk.gov.hmrc.tai.connectors.cache.TaiUpdateIncomeCacheConnector
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}
import uk.gov.hmrc.tai.repositories.deprecated.TaiUpdateIncomeCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant
import scala.concurrent.Future

class TaiUpdateIncomeCacheRepositorySpec extends BaseSpec with MongoFormatter with IntegrationPatience {

  implicit lazy val configuration: Configuration = inject[Configuration]

  implicit lazy val symmetricCryptoFactory: Encrypter with Decrypter =
    new ApplicationCrypto(configuration.underlying).JsonCrypto

  val mongoKey = "key1"
  val emptyKey = ""
  val cacheIdValue: String = cacheId.value

  private val cacheItemWithoutData =
    Future.successful(CacheItem("id", JsObject.empty, Instant.now, Instant.now))

  private val sessionData =
    SessionData(nino = nino.nino, taxSummaryDetailsCY = TaxSummaryDetails(nino = nino.nino, version = 0))

  private val taiUpdateIncomeRepository = mock[TaiUpdateIncomeCacheConnector]

  private def createSUTUpdateIncome(mongoConfig: MongoConfig = mock[MongoConfig]) =
    new TaiUpdateIncomeCacheRepository(taiUpdateIncomeRepository, mongoConfig, configuration)

  private def setCacheItem(id: String = cacheIdValue, data: JsObject) =
    Future.successful(
      Option(CacheItem(id, data, createdAt = java.time.Instant.now, modifiedAt = java.time.Instant.now)))

  override protected def beforeEach(): Unit = {
    reset(taiUpdateIncomeRepository)
    when(taiUpdateIncomeRepository.save(any())(any(), any())(any())).thenReturn(cacheItemWithoutData)
  }

  "Cache Connector" must {
    "save the data in cache" when {
      "encryption is disabled and provided with string data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)

        val data = sut.createOrUpdateIncome(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "encryption is enabled and provided with string data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUTUpdateIncome(mockMongoConfig)

        val data = sut.createOrUpdateIncome(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "provided with int data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)

        val data = sut.createOrUpdateIncome(cacheId, 10, emptyKey).futureValue

        data mustBe 10
      }

      "provided with session data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)

        val data = sut.createOrUpdateIncome(cacheId, sessionData, emptyKey).futureValue

        data mustBe sessionData
      }
    }

    "retrieve the data from cache" when {
      "id is present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val cacheItem = setCacheItem("id", Json.obj("TAI-DATA" -> "DATA"))
        when(taiUpdateIncomeRepository.findById(anyString())).thenReturn(cacheItem)
        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe Some("DATA")
        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled *UpdateIncome" in {

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[String, SensitiveT[String]]
        val encryptedData = encrypter.writes(SensitiveT("DATA"))
        val cacheItem = setCacheItem("id", Json.obj("TAI-DATA" -> encryptedData))
        when(taiUpdateIncomeRepository.findById(anyString())).thenReturn(cacheItem)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        when(taiUpdateIncomeRepository.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache and encryption is enabled *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        when(taiUpdateIncomeRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findUpdateIncome[String](cacheId).futureValue
        data mustBe None
        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val cacheItemWrongKey = setCacheItem("id", Json.obj("WRONG_KEY" -> "DATA"))
        when(taiUpdateIncomeRepository.findById(any())).thenReturn(cacheItemWrongKey)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache and encryption is enabled *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[String, SensitiveT[String]]
        val encryptedData = encrypter.writes(SensitiveT("DATA"))
        val cacheItemWrongKey =
          setCacheItem("id", Json.obj("WRONG_KEY" -> encryptedData))
        when(taiUpdateIncomeRepository.findById(any())).thenReturn(cacheItemWrongKey)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }
    }

    "retrieve the session data from cache" when {
      "id is present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> sessionData))
        when(taiUpdateIncomeRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findUpdateIncome[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(sessionData)

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUTUpdateIncome(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-DATA" -> sessionData))
        when(taiUpdateIncomeRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findUpdateIncome[String](cacheId, "TAI-DATA").futureValue

        data mustBe None

        verify(taiUpdateIncomeRepository, times(1)).findById(cacheIdValue)
      }
    }
  }
}
