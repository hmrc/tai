/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.cache.AsyncCacheApi
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Injecting
import play.api.{Application, Configuration}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.mongo.{CurrentTimestampSupport, MongoComponent, TimestampSupport}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.connectors.cache.{CacheId, TaiCacheConnector}
import uk.gov.hmrc.tai.integration.utils.FakeAsyncCacheApi
import uk.gov.hmrc.tai.model.domain.{Address, Person, PersonFormatter}
import uk.gov.hmrc.tai.repositories.deprecated.TaiCacheRepository

import scala.concurrent.ExecutionContext
import scala.util.Random

class TaiCacheRepositoryItSpec
    extends AnyWordSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ScalaFutures with Injecting {

  lazy val fakeAsyncCacheApi = new FakeAsyncCacheApi()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "tai.cache.expiryInSeconds" -> 10
      )
      .overrides(
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )
      .build()

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId("testSession")))

  val nino: Nino = new Generator(Random).nextNino
  val cacheId: CacheId = CacheId(nino)

  lazy val configuration: Configuration = app.injector.instanceOf[Configuration]
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  lazy val mongoComponent: MongoComponent = inject[MongoComponent]

  val mockConfig: MongoConfig = mock[MongoConfig]
  val timestampSupport: TimestampSupport = new CurrentTimestampSupport()

  Mockito.when(mockConfig.mongoEncryptionEnabled).thenReturn(true)

  private lazy val sut: TaiCacheRepository = new TaiCacheRepository(
    new TaiCacheConnector(mongoComponent, mockConfig, timestampSupport),
    mockConfig,
    configuration
  )

  "Cache Connector" must {
    "insert and read the data from mongodb" when {
      "data has been passed" in {
        val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
        val cachedData = sut.find[String](cacheId).futureValue

        Some(data) mustBe cachedData
      }

      "data has been passed without key" in {

        val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
        val cachedData = sut.find[String](cacheId).futureValue

        Some(data) mustBe cachedData

      }

      "saved and returned json is valid" in {
        val data = sut
          .createOrUpdate[Person](cacheId, Person(nino, "Name", "Surname", None, Address("", "", "", "", "")))(
            PersonFormatter.personMongoFormat
          )
          .futureValue
        val cachedData = sut.find[Person](cacheId)(PersonFormatter.personMongoFormat).futureValue
        cachedData mustBe Some(data)
      }
    }

    "delete the data from cache" when {
      "calling removeById" in {
        val data = sut.createOrUpdate[String](cacheId, "DATA").futureValue
        val cachedData = sut.find[String](cacheId).futureValue
        Some(data) mustBe cachedData

        sut.removeById(cacheId).futureValue

        val dataAfterRemove = sut.find[String](cacheId).futureValue
        dataAfterRemove mustBe None
      }
    }

    "return None" when {

      "returned json is invalid" in {
        val badJson = Json
          .parse("""
                   | {
                   |  "invalid": "key"
                   | }
                   |""".stripMargin)
          .toString
        sut.createOrUpdate[String](cacheId, badJson).futureValue
        val cachedData = sut.find[Person](cacheId)(PersonFormatter.personHodRead).futureValue
        cachedData mustBe None
      }

      "cache id doesn't exist" in {
        val idWithNoData = CacheId(new Generator(Random).nextNino)
        val cachedData = sut.findOptSeq[String](idWithNoData).futureValue

        cachedData mustBe None
      }
    }
  }
}
