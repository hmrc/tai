package uk.gov.hmrc.tai.integration.cache.connectors


import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, TaiCacheRepository}
import uk.gov.hmrc.tai.integration.TaiBaseSpec
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class CacheConnectorItSpec extends TaiBaseSpec("CacheConnectorItSpec") with MongoFormatter with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  val nino = new Generator(Random).nextNino
  val cacheId = CacheId(nino)

  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  private val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  private val atMost = 5 seconds

  private def createSUT(mongoConfig: MongoConfig): CacheConnector = new CacheConnector(new TaiCacheRepository(), mongoConfig) {}

    "Cache Connector" should {
      "insert and read the data from mongodb" when {
        "session data has been passed" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[SessionData](cacheId, sessionData), atMost)
          val cachedData = Await.result(sut.find[SessionData](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "data has been passed" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "session data has been passed without key" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[SessionData](cacheId, sessionData), atMost)
          val cachedData = Await.result(sut.find[SessionData](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "data has been passed without key" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "sequence has been passed" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)), atMost)
          val cachedData = Await.result(sut.findSeq[SessionData](cacheId), atMost)

          data shouldBe cachedData
        }

      }

      "delete the data from cache" when {
        "time to live is over" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)
          Some(data) shouldBe cachedData

          Thread.sleep(120000L)

          val cachedDataAfterTTL = Await.result(sut.find[String](cacheId), atMost)
          cachedDataAfterTTL shouldBe None
        }

        "calling removeById" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)
          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)
          Some(data) shouldBe cachedData

          Await.result(sut.removeById(cacheId), atMost)

          val dataAfterRemove = Await.result(sut.find[String](cacheId), atMost)
          dataAfterRemove shouldBe None
        }
      }

      "return the data from cache" when {
        "Nil is saved in cache" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, Nil), atMost)
          val cachedData = Await.result(sut.findOptSeq[SessionData](cacheId), atMost)

          Some(Nil) shouldBe cachedData
        }

        "sequence is saved in cache" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val data = Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)), atMost)
          val cachedData = Await.result(sut.findOptSeq[SessionData](cacheId), atMost)

          Some(List(sessionData, sessionData)) shouldBe cachedData
        }
      }

      "return None" when {
        "key doesn't exist" in {
          val mockMongo = mock[MongoConfig]
          Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
          val sut = createSUT(mockMongo)

          val idWithNoData = CacheId(new Generator(Random).nextNino)
          val cachedData = Await.result(sut.findOptSeq[SessionData](idWithNoData), atMost)

          cachedData shouldBe None
        }
      }
    }
}
