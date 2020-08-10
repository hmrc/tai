/*
 * Copyright 2020 HM Revenue & Customs
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

import org.mockito.Matchers.{any, eq => Meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Play
import play.api.libs.json.{JsString, Json}
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.crypto.json.JsonEncryptor
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class CacheConnectorSpec
    extends PlaySpec with MockitoSugar with FakeTaiPlayApplication with MongoFormatter with BeforeAndAfterEach
    with MockAuthenticationPredicate {

  lazy implicit val compositeSymmetricCrypto: CompositeSymmetricCrypto = new ApplicationCrypto(
    Play.current.configuration.underlying).JsonCrypto

  val cacheIdValue = cacheId.value
  val emptyKey = ""

  val databaseUpdate = Future.successful(mock[DatabaseUpdate[Cache]])
  val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  val mongoKey = "key1"
  val atMost = 5 seconds

  val cacheRepository = mock[CacheMongoRepository]
  val taiCacheRepository = mock[TaiCacheRepository]

  def createSUT(mongoConfig: MongoConfig = mock[MongoConfig], metrics: Metrics = mock[Metrics]) = {

    when(taiCacheRepository.repo).thenReturn(cacheRepository)

    new CacheConnector(taiCacheRepository, mongoConfig)
  }

  override protected def beforeEach(): Unit =
    reset(cacheRepository)

  "Cache Connector" should {
    "save the data in cache" when {
      "provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdate(cacheId, "DATA", emptyKey), atMost)

        data mustBe "DATA"
      }

      "encryption is enabled and provided with string data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdate(cacheId, "DATA", emptyKey), atMost)

        data mustBe "DATA"
      }

      "provided with int data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdate(cacheId, 10, emptyKey), atMost)

        data mustBe 10
      }

      "provided with session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdate(cacheId, sessionData, emptyKey), atMost)

        data mustBe sessionData
      }

      "provided with a sequence of a particular type" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey), atMost)

        data mustBe stringSeq
      }

      "provided with a sequence of a particular type and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val stringSeq = List("one", "two", "three")
        when(cacheRepository.createOrUpdate(any(), any(), any())).thenReturn(databaseUpdate)

        val data = Await.result(sut.createOrUpdateSeq[String](cacheId, stringSeq, emptyKey), atMost)

        data mustBe stringSeq
      }

    }

    "retrieve the data from cache" when {
      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-DATA" -> "DATA")))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.find[String](cacheId), atMost)

        data mustBe Some("DATA")

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[String]()
        val encryptedData = Json.toJson(Protected("DATA"))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-DATA" -> encryptedData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.find[String](cacheId), atMost)

        data mustBe Some("DATA")

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(None))

        val data = Await.result(sut.find[String](cacheId), atMost)

        data mustBe None

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(None))

        val data = Await.result(sut.find[String](cacheId), atMost)

        data mustBe None

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }
    }

    "retrieve the session data from cache" when {

      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> sessionData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.find[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe Some(sessionData)

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is present in the cache but with wrong type conversion" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-DATA" -> sessionData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.find[String](cacheId, "TAI-DATA"), atMost)

        data mustBe None

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }
    }

    "retrieve the sequence of session data from cache" when {

      "id is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache =
          Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> List(sessionData, sessionData))))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe List(sessionData, sessionData)

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> encryptedData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe List(sessionData, sessionData)

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is present in the cache but with wrong type conversion" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache =
          Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-DATA" -> List(sessionData, sessionData))))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findSeq[String](cacheId, "TAI-DATA"), atMost)

        data mustBe Nil

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is present in the cache but with wrong type conversion and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> encryptedData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findSeq[String](cacheId, "TAI-DATA"), atMost)

        data mustBe Nil

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(None))

        val data = Await.result(sut.findSeq[String](cacheId, "TAI-DATA"), atMost)

        data mustBe Nil

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }

      "id is not present in the cache and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(None))

        val data = Await.result(sut.findSeq[String](cacheId, "TAI-DATA"), atMost)

        data mustBe Nil

        verify(cacheRepository, times(1)).findById(Meq(Id(cacheIdValue)), any())(any())
      }
    }

    "remove the session data from cache" when {
      "remove has been called with id" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.removeById(any(), any())(any()))
          .thenReturn(Future.successful(DefaultWriteResult(ok = true, 0, Nil, None, None, None)))

        val result = Await.result(sut.removeById(cacheId), atMost)

        result mustBe true
        verify(cacheRepository, times(1)).removeById(Meq(Id(cacheIdValue)), any())(any())
      }
    }

    "throw the error" when {
      "failed to remove the session data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val writeErrors = Seq(WriteError(0, 0, "Failed"))
        val eventualWriteResult =
          Future.successful(DefaultWriteResult(ok = false, 0, writeErrors, None, None, Some("Failed")))
        when(cacheRepository.removeById(any(), any())(any())).thenReturn(eventualWriteResult)

        val ex = the[RuntimeException] thrownBy Await.result(sut.removeById(cacheId), atMost)
        ex.getMessage mustBe "Failed"
      }
    }
  }

  "findOptSeq" must {
    "return some sequence" when {
      "cache returns sequence" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache =
          Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> List(sessionData, sessionData))))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe Some(List(sessionData, sessionData))

      }

      "cache returns sequence and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> encryptedData)))))

        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe Some(List(sessionData, sessionData))

      }

      "cache returns Nil" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> Nil)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe Some(Nil)
      }

      "cache returns Nil and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List.empty[SessionData]))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> encryptedData)))))

        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-SESSION"), atMost)

        data mustBe Some(Nil)
      }

    }

    "return none" when {
      "cache doesn't have key" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val eventualSomeCache =
          Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> List(sessionData, sessionData))))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-DATA"), atMost)

        data mustBe None
      }

      "cache doesn't have key and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonEncryptor = new JsonEncryptor[List[SessionData]]()
        val encryptedData = Json.toJson(Protected(List(sessionData, sessionData)))(jsonEncryptor)
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(Json.toJson(Map("TAI-SESSION" -> encryptedData)))))
        when(cacheRepository.findById(any(), any())(any())).thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findOptSeq[SessionData](cacheId, "TAI-DATA"), atMost)

        data mustBe None
      }
    }
  }

  "createOrUpdate" must {
    "save json and return json response" when {
      "provided with json data" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val jsonData = Json.obj("amount" -> 123)
        when(cacheRepository.createOrUpdate(Meq(Id(cacheIdValue)), Meq("KeyName"), Meq(jsonData)))
          .thenReturn(databaseUpdate)

        val result = Await.result(sut.createOrUpdateJson(cacheId, jsonData, "KeyName"), atMost)

        result mustBe jsonData
      }

      "provided with json data and encryption is enabled" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongoConfig)
        val jsonData = Json.obj("amount" -> 123)
        when(cacheRepository.createOrUpdate(Meq(Id(cacheIdValue)), Meq("KeyName"), any()))
          .thenReturn(databaseUpdate)

        val result = Await.result(sut.createOrUpdateJson(cacheId, jsonData, "KeyName"), atMost)

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
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(json)))
        when(cacheRepository.findById(Meq(Id(cacheIdValue)), any())(any()))
          .thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findJson(cacheId, mongoKey), atMost)

        data mustBe Some(JsString("DATA"))
      }
    }

    "retrieve None" when {
      "id is not present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        when(cacheRepository.findById(Meq(Id(cacheIdValue)), any())(any())).thenReturn(Future.successful(None))

        val data = Await.result(sut.findJson(cacheId, mongoKey), atMost)

        data mustBe None
      }
    }

    "retrieve None" when {
      "key is present in the cache" in {
        val mockMongoConfig = mock[MongoConfig]
        when(mockMongoConfig.mongoEncryptionEnabled).thenReturn(false)
        val sut = createSUT(mockMongoConfig)
        val json = Json.obj("wrong-key" -> "DATA")
        val eventualSomeCache = Some(Cache(Id(cacheIdValue), Some(json)))
        when(cacheRepository.findById(Meq(Id(cacheIdValue)), any())(any()))
          .thenReturn(Future.successful(eventualSomeCache))

        val data = Await.result(sut.findJson(cacheId, mongoKey), atMost)

        data mustBe None
      }
    }
  }
}
