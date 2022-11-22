/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito._
import org.scalatest.concurrent.IntegrationPatience
import play.api.Configuration
import play.api.libs.json.{JsObject, JsString, Json}
import reactivemongo.api.commands.WriteError
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.crypto.json.JsonEncryptor
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.cache.{CacheItem, MongoCacheRepository}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

class CacheConnectorSpec extends BaseSpec with MongoFormatter with IntegrationPatience {

  implicit lazy val configuration: Configuration = inject[Configuration]

  lazy implicit val compositeSymmetricCrypto
    : CompositeSymmetricCrypto = new ApplicationCrypto(configuration.underlying).JsonCrypto

  val cacheIdValue: String = cacheId.value
  val emptyKey = ""

  private val emptyCacheItem: Future[CacheItem] =
    Future.successful(CacheItem("id", JsObject.empty, Instant.now, Instant.now))
  private val someCacheItem: Future[Option[CacheItem]] =
    Future.successful(Some(CacheItem("id", Json.obj(("TAI-DATA" -> "DATA")), Instant.now, Instant.now)))
  val someCache: Option[Cache] =
    Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-DATA" -> "DATA")))))

  val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  val mongoKey = "key1"
  val atMost = 5 seconds

  private val cacheItem: Option[CacheItem] =
    Some(
      CacheItem(
        cacheIdValue,
        Json.obj("TAI-DATA" -> "DATA"),
        createdAt = java.time.Instant.now,
        modifiedAt = java.time.Instant.now))

  private val taiCacheRepository = mock[TaiCacheRepository]
  private val taiCacheRepositoryUpdateIncome = mock[TaiCacheRepositoryUpdateIncome]

  def createSUT(mongoConfig: MongoConfig = mock[MongoConfig], metrics: Metrics = mock[Metrics]) =
    new CacheConnector(taiCacheRepository, taiCacheRepositoryUpdateIncome, mongoConfig, configuration)

  override protected def beforeEach(): Unit = {
    reset(taiCacheRepository)
    reset(taiCacheRepositoryUpdateIncome)
  }

  "TaiCacheRepository" must {

    lazy val sut = inject[TaiCacheRepository]

    "have the correct collection name" in {
      sut.collectionName mustBe "TAI"
    }

    "use mongoFormats from Cache" in {
      sut.domainFormat mustBe MongoCacheRepository.format
    }
  }

  "TaiCacheRepositoryUpdateIncome" must {

    lazy val sut: TaiCacheRepositoryUpdateIncome = inject[TaiCacheRepositoryUpdateIncome]

    "have the correct collection name" in {
      sut.collectionName mustBe "TaiUpdateIncome"
    }

    "use mongoFormats from Cache" in {
      sut.domainFormat mustBe MongoCacheRepository.format
    }
    "have 2 day ttl" in {
      //172800
    }
  }

  "Cache Connector" must {
    "save the data in cache" when {
      "provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.save[String](any[String]())(any(), any[String]())(any()))
          .thenReturn(emptyCacheItem)

        val data = sut.createOrUpdate(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "encryption is enabled and provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdate(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "provided with int data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdate(cacheId, 10, emptyKey).futureValue

        data mustBe 10
      }

      "provided with session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdate(cacheId, sessionData, emptyKey).futureValue

        data mustBe sessionData
      }

      "provided with a sequence of a particular type" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey).futureValue

        data mustBe stringSeq
      }

      "provided with a sequence of a particular type and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey).futureValue

        data mustBe stringSeq
      }

      //update-income
      "encryption is disabled and provided with string data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateIncome(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "encryption is enabled and provided with string data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateIncome(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "provided with int data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateIncome(cacheId, 10, emptyKey).futureValue

        data mustBe 10
      }

      "provided with session data *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.save(any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val data = sut.createOrUpdateIncome(cacheId, sessionData, emptyKey).futureValue

        data mustBe sessionData
      }
    }

    "retrieve the data from cache" when {
      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-DATA" -> "DATA"),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.find[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[String]()
        val encryptedData = Json.toJson(Protected("DATA"))(jsonEncryptor)
        val someCacheItemFuture: Future[Option[CacheItem]] =
          Future.successful(Some(CacheItem("id", Json.obj("TAI-DATA" -> encryptedData), Instant.now, Instant.now)))
        when(taiCacheRepository.findById(any())).thenReturn(someCacheItemFuture)

        val data = sut.find[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "encryption is disabled and id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "encryption is enabled and id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItemWrongKey =
          Future.successful(Some(CacheItem("id", Json.obj("WRONG_KEY" -> "DATA"), Instant.now, Instant.now)))
        when(taiCacheRepository.findById(anyString())).thenReturn(cacheItemWrongKey)

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[String]()
        val encryptedData = Json.toJson(Protected("DATA"))(jsonEncryptor)
        val cacheItemWrongKey =
          Future.successful(Some(CacheItem("id", Json.obj("WRONG_KEY" -> encryptedData), Instant.now, Instant.now)))

        when(taiCacheRepository.findById(anyString())).thenReturn(cacheItemWrongKey)

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      //update-income
      "id is present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.findById(anyString())).thenReturn(someCacheItem)
        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe Some("DATA")
        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled *UpdateIncome" in {

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[String]()
        val encryptedData = Json.toJson(Protected("DATA"))(jsonEncryptor)
        val someCacheItemFuture: Future[Option[CacheItem]] =
          Future.successful(Some(CacheItem("id", Json.obj("TAI-DATA" -> encryptedData), Instant.now, Instant.now)))
        when(taiCacheRepositoryUpdateIncome.findById(anyString())).thenReturn(someCacheItemFuture)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache and encryption is enabled *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepositoryUpdateIncome.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findUpdateIncome[String](cacheId).futureValue
        data mustBe None
        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItemWrongKey =
          Future.successful(Some(CacheItem("id", Json.obj("WRONG_KEY" -> "DATA"), Instant.now, Instant.now)))
        when(taiCacheRepositoryUpdateIncome.findById(any())).thenReturn(cacheItemWrongKey)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache and encryption is enabled *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[String]()
        val encryptedData = Json.toJson(Protected("DATA"))(jsonEncryptor)
        val cacheItemWrongKey =
          Future.successful(Some(CacheItem("id", Json.obj("WRONG_KEY" -> "DATA"), Instant.now, Instant.now)))

        when(taiCacheRepositoryUpdateIncome.findById(any())).thenReturn(cacheItemWrongKey)

        val data = sut.findUpdateIncome[String](cacheId).futureValue

        data mustBe None

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }
    }

    "retrieve the session data from cache" when {
      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> sessionData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.find[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(sessionData)

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-DATA" -> sessionData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.find[String](cacheId, "TAI-DATA").futureValue

        data mustBe None

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      //updateIncome

      "id is present in the cache *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> sessionData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))
        when(taiCacheRepositoryUpdateIncome.findById(any()))
          .thenReturn(Future.successful(someCacheItem))

        val data = sut.findUpdateIncome[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(sessionData)

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion *UpdateIncome" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-DATA" -> sessionData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))
        when(taiCacheRepositoryUpdateIncome.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findUpdateIncome[String](cacheId, "TAI-DATA").futureValue

        data mustBe None

        verify(taiCacheRepositoryUpdateIncome, times(1)).findById(cacheIdValue)
      }
    }

    "retrieve the sequence of session data from cache" when {

      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> List(sessionData, sessionData)),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findSeq[SessionData](cacheId, "TAI-SESSION").futureValue
        data mustBe List(sessionData, sessionData)
        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)

        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> encryptedData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findSeq[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe List(sessionData, sessionData)

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> List(sessionData, sessionData)),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> encryptedData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiCacheRepository, times(1)).findById(cacheIdValue)
      }
    }

    "remove the session data from cache" when {
      "remove has been called with id" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.deleteEntity(any())).thenReturn(Future.successful())

        val result = sut.removeById(cacheId).futureValue

        result mustBe true
        verify(taiCacheRepository, times(1)).deleteEntity(any())
      }
    }

    "throw the error" when {
      "failed to remove the session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val writeErrors = Seq(WriteError(0, 0, "Failed"))
        when(taiCacheRepository.deleteEntity(any()))
          .thenReturn(Future.failed(new RuntimeException(writeErrors.head.errmsg)))

        val result = sut.removeById(cacheId).failed.futureValue

        result mustBe a[RuntimeException]

        result.getMessage mustBe "Failed"
      }
    }
  }

  "findOptSeq" must {
    "return some sequence" when {
      "cache returns sequence" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> List(sessionData, sessionData)),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(List(sessionData, sessionData))

      }

      "cache returns sequence and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> encryptedData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(List(sessionData, sessionData))
      }

      "cache returns Nil" in {

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> List.empty[String]),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(Nil)
      }

      "cache returns Nil and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List.empty[SessionData]))(jsonEncryptor)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> encryptedData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(Nil)
      }

    }

    "return none" when {
      "cache doesn't have key" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> List(sessionData, sessionData)),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-DATA").futureValue

        data mustBe None
      }

      "cache doesn't have key and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val someCacheItem: Option[CacheItem] =
          Some(
            CacheItem(
              cacheIdValue,
              Json.obj("TAI-SESSION" -> encryptedData),
              createdAt = java.time.Instant.now,
              modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findOptSeq[SessionData](cacheId, "TAI-DATA").futureValue

        data mustBe None
      }
    }
  }

  "save" must {
    "save json and return json response" when {
      "provided with json data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val jsonData = Json.obj("amount" -> 123)
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val result = sut.createOrUpdateJson(cacheId, jsonData, "KeyName").futureValue

        result mustBe jsonData
      }

      "provided with json data and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonData = Json.obj("amount" -> 123)
        when(taiCacheRepository.save[String](any())(any(), any())(any())).thenReturn(emptyCacheItem)

        val result = sut.createOrUpdateJson(cacheId, jsonData, "KeyName").futureValue

        result mustBe jsonData
      }
    }
  }

  "findJson" must {
    "retrieve the json from cache" when {
      "id and key are present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val json = Json.obj(mongoKey -> "DATA")
        val someCacheItem: Option[CacheItem] =
          Some(CacheItem(cacheIdValue, json, createdAt = java.time.Instant.now, modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe Some(JsString("DATA"))
      }
    }

    "retrieve None" when {
      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe None
      }
    }

    "retrieve None" when {
      "key is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val json = Json.obj("wrong-key" -> "DATA")
        val someCacheItem: Option[CacheItem] =
          Some(CacheItem(cacheIdValue, json, createdAt = java.time.Instant.now, modifiedAt = java.time.Instant.now))

        when(taiCacheRepository.findById(any())).thenReturn(Future.successful(someCacheItem))

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe None
      }
    }
  }
}
