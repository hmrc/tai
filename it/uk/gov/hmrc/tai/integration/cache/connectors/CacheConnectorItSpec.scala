package uk.gov.hmrc.tai.integration.cache.connectors

import org.mockito.Mockito
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.cache.model.Cache
import uk.gov.hmrc.cache.repository.CacheRepository
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.CacheConnector
import uk.gov.hmrc.tai.integration.TaiBaseSpec
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.ExecutionContext.Implicits.global

class CacheConnectorItSpec extends TaiBaseSpec("CacheConnectorItSpec") with MongoFormatter with MockitoSugar {

  "Cache Connector" should {
    "insert and read the data from mongodb" when {
      "session data has been passed" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[SessionData]("KEY-1", sessionData, "TAI-SESSION"), atMost)
        val cachedData = Await.result(sut.find[SessionData]("KEY-1", "TAI-SESSION"), atMost)

        Some(data) shouldBe cachedData
      }

      "data has been passed" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[String]("KEY-2", "DATA", "TAI-SESSION"), atMost)
        val cachedData = Await.result(sut.find[String]("KEY-2", "TAI-SESSION"), atMost)

        Some(data) shouldBe cachedData
      }


      "session data has been passed without key" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[SessionData]("12345", sessionData), atMost)
        val cachedData = Await.result(sut.find[SessionData]("12345"), atMost)

        Some(data) shouldBe cachedData
      }

      "data has been passed without key" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[String]("123", "DATA"), atMost)
        val cachedData = Await.result(sut.find[String]("123"), atMost)

        Some(data) shouldBe cachedData
      }

      "sequence has been passed" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[Seq[SessionData]]("KEY-1", List(sessionData,sessionData), "TAI-SESSION"), atMost)
        val cachedData = Await.result(sut.findSeq[SessionData]("KEY-1", "TAI-SESSION"), atMost)

        data shouldBe cachedData
      }


    }

    "delete the data from cache" when {
      "time to live is over" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[String]("TTLKey", "DATA"), atMost)
        val cachedData = Await.result(sut.find[String]("TTLKey"), atMost)
        Some(data) shouldBe cachedData

        Thread.sleep(120000L)

        val cachedDataAfterTTL = Await.result(sut.find[String]("TTLKey"), atMost)
        cachedDataAfterTTL shouldBe None
      }

      "calling removeById" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)
        val data = Await.result(sut.createOrUpdate[String]("TTLKey", "DATA"), atMost)
        val cachedData = Await.result(sut.find[String]("TTLKey"), atMost)
        Some(data) shouldBe cachedData

        Await.result(sut.removeById("TTLKey"), atMost)

        val dataAfterRemove = Await.result(sut.find[String]("TTLKey"), atMost)
        dataAfterRemove shouldBe None
      }
    }

    "return the data from cache" when {
      "Nil is saved in cache" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[Seq[SessionData]]("KEY-1", Nil, "TAI-SESSION"), atMost)
        val cachedData = Await.result(sut.findOptSeq[SessionData]("KEY-1", "TAI-SESSION"), atMost)

        Some(Nil) shouldBe cachedData
      }

      "sequence is saved in cache" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val data = Await.result(sut.createOrUpdate[Seq[SessionData]]("KEY-1", List(sessionData,sessionData), "TAI-SESSION"), atMost)
        val cachedData = Await.result(sut.findOptSeq[SessionData]("KEY-1", "TAI-SESSION"), atMost)

        Some(List(sessionData,sessionData)) shouldBe cachedData
      }

    }

    "return None" when {
      "key doesn't exist" in {
        val mockMongo = mock[MongoConfig]
        Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)
        val sut = createSUT(mockMongo)

        val cachedData = Await.result(sut.findOptSeq[SessionData]("KEY-1-2-3", "TAI-SESSION"), atMost)

        cachedData shouldBe None
      }
    }

  }
  private val nino: Nino = new Generator().nextNino
  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  private val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  private val atMost = 5 seconds

  private def createSUT(mongoConfig: MongoConfig): CacheConnector = new CacheConnector(mongoConfig) {
    val expireAfter: Long = 5
    override val cacheRepository: CacheRepository = CacheRepository("TAI-IT-TEST", expireAfter, Cache.mongoFormats)
  }

}
