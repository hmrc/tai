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

package uk.gov.hmrc.tai.repositories.deprecated

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.concurrent.IntegrationPatience
import play.api.Configuration
import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.crypto.json.JsonEncryption
import uk.gov.hmrc.crypto.{ApplicationCrypto, Decrypter, Encrypter}
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.tai.config.{MongoConfig, SensitiveT}
import uk.gov.hmrc.tai.connectors.cache.TaiCacheConnector
import uk.gov.hmrc.tai.util.BaseSpec

import java.time.Instant
import scala.concurrent.Future

class TaiCacheRepositorySpec extends BaseSpec with IntegrationPatience {

  implicit lazy val configuration: Configuration = inject[Configuration]

  implicit lazy val symmetricCryptoFactory: Encrypter with Decrypter =
    new ApplicationCrypto(configuration.underlying).JsonCrypto

  val mongoKey = "key1"
  val emptyKey = ""
  val cacheIdValue: String = cacheId.value

  private val cacheItemWithoutData =
    Future.successful(CacheItem("id", JsObject.empty, Instant.now, Instant.now))

  private val sessionData = "SessionData"

  private val taiRepository = mock[TaiCacheConnector]

  private def createSUT(mongoConfig: MongoConfig = mock[MongoConfig]) =
    new TaiCacheRepository(taiRepository, mongoConfig, configuration)

  private def setCacheItem(id: String = cacheIdValue, data: JsObject) =
    Future.successful(
      Option(CacheItem(id, data, createdAt = java.time.Instant.now, modifiedAt = java.time.Instant.now))
    )

  override protected def beforeEach(): Unit = {
    reset(taiRepository)
    when(taiRepository.save[String](any())(any(), any())(any())).thenReturn(cacheItemWithoutData)
    ()
  }

  "createOrUpdate" must {
    "save the data in cache" when {
      "provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val data = sut.createOrUpdate(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "encryption is enabled and provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val data = sut.createOrUpdate(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }

      "provided with int data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val data = sut.createOrUpdate(cacheId, 10, emptyKey).futureValue

        data mustBe 10
      }

      "provided with session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val data = sut.createOrUpdate(cacheId, sessionData, emptyKey).futureValue

        data mustBe sessionData
      }

      "provided with a sequence of a particular type" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        val data = sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey).futureValue

        data mustBe stringSeq
      }

      "provided with a sequence of a particular type and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        val data = sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey).futureValue

        data mustBe stringSeq
      }
    }
  }

  "find" must {

    "retrieve the data from cache" when {
      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-DATA" -> "DATA"))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.find[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[String, SensitiveT[String]]
        val encryptedData = encrypter.writes(SensitiveT("DATA"))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-DATA" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.find[String](cacheId).futureValue

        data mustBe Some("DATA")

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "encryption is disabled and id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "encryption is enabled and id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.findById(anyString())).thenReturn(Future.successful(None))

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItemWrongKey = setCacheItem("id", Json.obj("WRONG_KEY" -> "DATA"))
        when(taiRepository.findById(anyString())).thenReturn(cacheItemWrongKey)

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "key is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[String, SensitiveT[String]]
        val encryptedData = encrypter.writes(SensitiveT("DATA"))
        val cacheItemWrongKey = setCacheItem("id", Json.obj("WRONG_KEY" -> encryptedData))
        when(taiRepository.findById(anyString())).thenReturn(cacheItemWrongKey)

        val data = sut.find[String](cacheId).futureValue

        data mustBe None

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }
    }

    "retrieve the session data from cache" when {
      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> sessionData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.find[String](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(sessionData)

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }
    }

    "retrieve the sequence of session data from cache" when {

      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> List(sessionData, sessionData)))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findSeq[String](cacheId, "TAI-SESSION").futureValue
        data mustBe List(sessionData, sessionData)
        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[List[String], SensitiveT[List[String]]]
        val encryptedData = encrypter.writes(SensitiveT(List(sessionData, sessionData)))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findSeq[String](cacheId, "TAI-SESSION").futureValue

        data mustBe List(sessionData, sessionData)

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> List(sessionData, sessionData)))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is present in the cache but with wrong type conversion and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[List[String], SensitiveT[List[String]]]
        val encryptedData = encrypter.writes(SensitiveT(List(sessionData, sessionData)))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }

      "id is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe Nil

        verify(taiRepository, times(1)).findById(cacheIdValue)
      }
    }
  }

  "removeById" must {

    "remove the session data from cache" when {
      "remove has been called with id" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.deleteEntity(any())).thenReturn(Future.successful((): Unit))

        val result = sut.removeById(cacheId).futureValue

        result mustBe true
        verify(taiRepository, times(1)).deleteEntity(any())
      }
    }

    "throw the error" when {
      "failed to remove the session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.deleteEntity(any()))
          .thenReturn(Future.failed(new RuntimeException("Failed")))

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
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> List(sessionData, sessionData)))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(List(sessionData, sessionData))

      }

      "cache returns sequence and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[List[String], SensitiveT[List[String]]]
        val encryptedData = encrypter.writes(SensitiveT(List(sessionData, sessionData)))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(List(sessionData, sessionData))
      }

      "cache returns Nil" in {

        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> List.empty[String]))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(Nil)
      }

      "cache returns Nil and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[List[String], SensitiveT[List[String]]]
        val encryptedData = encrypter.writes(SensitiveT(List.empty[String]))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-SESSION").futureValue

        data mustBe Some(Nil)
      }

    }

    "return none" when {
      "cache doesn't have key" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> List(sessionData, sessionData)))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-DATA").futureValue

        data mustBe None
      }

      "cache doesn't have key and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val encrypter = JsonEncryption.sensitiveEncrypter[List[String], SensitiveT[List[String]]]
        val encryptedData = encrypter.writes(SensitiveT(List(sessionData, sessionData)))
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("TAI-SESSION" -> encryptedData))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findOptSeq[String](cacheId, "TAI-DATA").futureValue

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

        val result = sut.createOrUpdateJson(cacheId, jsonData, "KeyName").futureValue

        result mustBe jsonData
      }

      "provided with json data and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonData = Json.obj("amount" -> 123)

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
        val cacheItem = setCacheItem(cacheIdValue, Json.obj(mongoKey -> "DATA"))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe Some(JsString("DATA"))
      }
    }

    "retrieve None" when {
      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(taiRepository.findById(any())).thenReturn(Future.successful(None))

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe None
      }
    }

    "retrieve None" when {
      "key is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val cacheItem = setCacheItem(cacheIdValue, Json.obj("wrong-key" -> "DATA"))
        when(taiRepository.findById(any())).thenReturn(cacheItem)

        val data = sut.findJson(cacheId, mongoKey).futureValue

        data mustBe None
      }
    }
  }
}
