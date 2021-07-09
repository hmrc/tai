/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.integration.cache.connectors


import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, TaiCacheRepository}
import uk.gov.hmrc.tai.integration.TaiBaseSpec
import uk.gov.hmrc.tai.model.domain.{Address, Person, PersonFormatter}
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.Random

class CacheConnectorItSpec extends TaiBaseSpec("CacheConnectorItSpec") with MongoFormatter with MockitoSugar with GuiceOneAppPerSuite {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  val nino = new Generator(Random).nextNino
  val cacheId = CacheId(nino)

  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  private val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  private val atMost = 5 seconds

  lazy val configuration: Configuration = app.injector.instanceOf[Configuration]
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val mockMongo: MongoConfig = mock[MongoConfig]
  Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)

  private lazy val sut: CacheConnector = new CacheConnector(new TaiCacheRepository(), mockMongo, configuration) {}

    "Cache Connector" should {
      "insert and read the data from mongodb" when {
        "session data has been passed" in {
          val data = Await.result(sut.createOrUpdate[SessionData](cacheId, sessionData), atMost)
          val cachedData = Await.result(sut.find[SessionData](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "data has been passed" in {
          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "session data has been passed without key" in {
          val data = Await.result(sut.createOrUpdate[SessionData](cacheId, sessionData), atMost)
          val cachedData = Await.result(sut.find[SessionData](cacheId), atMost)

          Some(data) shouldBe cachedData
        }

        "data has been passed without key" in {
          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)
          Some(data) shouldBe cachedData
        }

        "sequence has been passed" in {
          val data = Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)), atMost)
          val cachedData = Await.result(sut.findSeq[SessionData](cacheId), atMost)

          data shouldBe cachedData
        }

        "saved and returned json is valid" in {
          val data = Await.result(sut.createOrUpdate[Person](cacheId, Person(nino, "Name", "Surname", None, Address("", "", "", "", ""), false, false))(PersonFormatter.personMongoFormat), atMost)
          val cachedData = Await.result(sut.find[Person](cacheId)(PersonFormatter.personMongoFormat), atMost)
          cachedData shouldBe Some(data)
        }

      }

      "delete the data from cache" when {
        "time to live is over" in {
          val data = Await.result(sut.createOrUpdate[String](cacheId, "DATA"), atMost)
          val cachedData = Await.result(sut.find[String](cacheId), atMost)
          Some(data) shouldBe cachedData

          Thread.sleep(120000L)

          val cachedDataAfterTTL = Await.result(sut.find[String](cacheId), atMost)
          cachedDataAfterTTL shouldBe None
        }

        "calling removeById" in {
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
          Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, Nil), atMost)
          val cachedData = Await.result(sut.findOptSeq[SessionData](cacheId), atMost)

          Some(Nil) shouldBe cachedData
        }

        "sequence is saved in cache" in {
          Await.result(sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)), atMost)
          val cachedData = Await.result(sut.findOptSeq[SessionData](cacheId), atMost)

          Some(List(sessionData, sessionData)) shouldBe cachedData
        }
      }

      "return None" when {

        "returned json is invalid" in {
          val badJson = Json.parse("""
                                     | {
                                     |  "invalid": "key"
                                     | }
                                     |""".stripMargin).toString
          Await.result(sut.createOrUpdate[String](cacheId, badJson), atMost)
          val cachedData = Await.result(sut.find[Person](cacheId)(PersonFormatter.personHodRead), atMost)
          cachedData shouldBe None
        }

        "cache id doesn't exist" in {
          val idWithNoData = CacheId(new Generator(Random).nextNino)
          val cachedData = Await.result(sut.findOptSeq[SessionData](idWithNoData), atMost)

          cachedData shouldBe None
        }
      }
    }
}
