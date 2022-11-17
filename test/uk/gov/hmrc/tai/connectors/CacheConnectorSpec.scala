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

import org.mockito.ArgumentMatchers.{any, anyString, eq => Meq}
import org.mockito.Mockito._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Configuration
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json.{JsObject, JsString, JsValue, Json, Writes}
import reactivemongo.api.commands.{DefaultWriteResult, WriteError}
import uk.gov.hmrc.cache.model.{Cache, Id}
import uk.gov.hmrc.crypto.json.JsonEncryptor
import uk.gov.hmrc.crypto.{ApplicationCrypto, CompositeSymmetricCrypto, Protected}
import uk.gov.hmrc.mongo.DatabaseUpdate
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}
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

  val cacheIdValue = cacheId.value
  val emptyKey = ""

  val databaseUpdate = Future.successful(mock[DatabaseUpdate[Cache]])
  //val cacheItemFuture: Future[CacheItem] = Future.successful(mock[CacheItem])
  private val cacheItemFuture: Future[CacheItem] =
    Future.successful(CacheItem("id", JsObject.empty, Instant.now, Instant.now))
  private val futureSomeCacheItem: Future[Some[CacheItem]] =
    Future.successful(Some(CacheItem("id", JsObject.empty, Instant.now, Instant.now)))
  //val futureSomeCacheItem: Future[Option[CacheItem]] = Future.successful(Some(mock[CacheItem]))

  val taxSummaryDetails = TaxSummaryDetails(nino = nino.nino, version = 0)
  val sessionData = SessionData(nino = nino.nino, taxSummaryDetailsCY = taxSummaryDetails)
  val mongoKey = "key1"
  val atMost = 5 seconds

  val taiCacheRepository = mock[TaiCacheRepository]
  val taiCacheRepositoryUpdateIncome = mock[TaiCacheRepositoryUpdateIncome]

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
      sut.domainFormat mustBe Cache.mongoFormats
    }
  }

  "TaiCacheRepositoryUpdateIncome" must {

    lazy val sut = inject[TaiCacheRepositoryUpdateIncome]

    "have the correct collection name" in {
      sut.collectionName mustBe "TaiUpdateIncome"
    }

    "use mongoFormats from Cache" in {
      sut.domainFormat mustBe Cache.mongoFormats
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
        when(taiCacheRepository.put[String](any[String]())(any[DataKey[String]](), any[String]())(any()))
          .thenReturn(cacheItemFuture)

        val data = sut.createOrUpdate(cacheId, "DATA", emptyKey).futureValue

        data mustBe "DATA"
      }
    }
  }

}
