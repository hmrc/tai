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

package uk.gov.hmrc.tai.integration.cache.connectors


import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Injecting
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.{CacheConnector, CacheId, TaiCacheRepository}
import uk.gov.hmrc.tai.model.domain.{Address, Person, PersonFormatter}
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.model.{SessionData, TaxSummaryDetails}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class CacheConnectorItSpec extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MongoFormatter with MockitoSugar with ScalaFutures with Injecting {

  override def fakeApplication = GuiceApplicationBuilder()
    .configure(
      "tai.cache.expiryInSeconds" -> 10
    )
    .build()

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  val nino = new Generator(Random).nextNino
  val cacheId = CacheId(nino)

  private val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  private val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  private val atMost = 5 seconds

  lazy val configuration: Configuration = app.injector.instanceOf[Configuration]
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  lazy val mockReactiveMongo: ReactiveMongoComponent = inject[ReactiveMongoComponent]

  val mockMongo: MongoConfig = mock[MongoConfig]
  Mockito.when(mockMongo.mongoEncryptionEnabled).thenReturn(true)

  private lazy val sut: CacheConnector = new CacheConnector(new TaiCacheRepository(mockReactiveMongo, mockMongo), mockMongo, configuration)

    "Cache Connector" must {
      "insert and read the data from mongodb" when {
        "session data has been passed" in {
          val data = sut.createOrUpdate[SessionData](cacheId, sessionData).futureValue
          val cachedData = sut.find[SessionData](cacheId).futureValue

          Some(data) mustBe cachedData
        }

        "data has been passed" in {
          val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
          val cachedData = sut.find[String](cacheId).futureValue

          Some(data) mustBe cachedData
        }

        "session data has been passed without key" in {
          val data = sut.createOrUpdate[SessionData](cacheId, sessionData).futureValue
          val cachedData = sut.find[SessionData](cacheId).futureValue

          Some(data) mustBe cachedData
        }

        "data has been passed without key" in {

          val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
          val cachedData = sut.find[String](cacheId).futureValue

          Some(data) mustBe cachedData

        }

        "sequence has been passed" in {
          val data = sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)).futureValue
          val cachedData = sut.findSeq[SessionData](cacheId).futureValue

          data mustBe cachedData
        }

        "saved and returned json is valid" in {
          val data = sut.createOrUpdate[Person](cacheId, Person(nino, "Name", "Surname", None, Address("", "", "", "", ""), false, false))(PersonFormatter.personMongoFormat).futureValue
          val cachedData = sut.find[Person](cacheId)(PersonFormatter.personMongoFormat).futureValue
          cachedData mustBe Some(data)
        }

      }

      "delete the data from cache" when {
        "time to live is over" in {
          val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
          val cachedData = sut.find[String](cacheId).futureValue
          Some(data) mustBe cachedData

          Thread.sleep(100000L)

          val cachedDataAfterTTL = sut.find[String](cacheId).futureValue
          cachedDataAfterTTL mustBe None
        }

        "calling removeById" in {
         val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
          val cachedData = sut.find[String](cacheId).futureValue
          Some(data) mustBe cachedData

          sut.removeById(cacheId).futureValue

          val dataAfterRemove = sut.find[String](cacheId).futureValue
          dataAfterRemove mustBe None
        }
      }

      "return the data from cache" when {
        "Nil is saved in cache" in {
          sut.createOrUpdate[Seq[SessionData]](cacheId, Nil).futureValue
          val cachedData = sut.findOptSeq[SessionData](cacheId).futureValue

          Some(Nil) mustBe cachedData
        }

        "sequence is saved in cache" in {
          sut.createOrUpdate[Seq[SessionData]](cacheId, List(sessionData, sessionData)).futureValue
          val cachedData = sut.findOptSeq[SessionData](cacheId).futureValue

          Some(List(sessionData, sessionData)) mustBe cachedData
        }
      }

      "return None" when {

        "returned json is invalid" in {
          val badJson = Json.parse("""
                                     | {
                                     |  "invalid": "key"
                                     | }
                                     |""".stripMargin).toString
          sut.createOrUpdate[String](cacheId, badJson).futureValue
          val cachedData = sut.find[Person](cacheId)(PersonFormatter.personHodRead).futureValue
          cachedData mustBe None
        }

        "cache id doesn't exist" in {
          val idWithNoData = CacheId(new Generator(Random).nextNino)
          val cachedData = sut.findOptSeq[SessionData](idWithNoData).futureValue

          cachedData mustBe None
        }
      }
    }
}
