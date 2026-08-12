/*
 * Copyright 2026 HM Revenue & Customs
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

import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId, SessionId}
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.{CacheIdType, DataKey}
import uk.gov.hmrc.mongo.logging.ObservableFutureImplicits.ObservableFuture
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.tai.config.CryptoProvider
import uk.gov.hmrc.tai.model.CachedAuthRetrievals
import uk.gov.hmrc.tai.util.BaseSpec

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

class GenericCacheRepositorySpec extends BaseSpec with MongoSupport {

  override implicit lazy val hc: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId("request-id")))

  class GenericCacheRepositoryImpl(
    cacheId: CacheIdType[HeaderCarrier] = RequestCacheId
  ) extends GenericCacheRepository[HeaderCarrier](
        mongoComponent = mongoComponent,
        collectionName = "collectionName",
        crypto = inject[CryptoProvider].get,
        ttl = Duration(60, TimeUnit.SECONDS),
        timestampSupport = new CurrentTimestampSupport(),
        cacheIdType = cacheId
      )

  private val repository: GenericCacheRepository[HeaderCarrier] = new GenericCacheRepositoryImpl

  "GenericCacheRepository" must {
    "put and get session data" in {
      val key = DataKey[DummyData]("key1")
      val value = DummyData("test-data")

      repository.put(hc, key, value).futureValue

      repository.get(hc, key).futureValue mustBe Some(value)
    }

    "put and get CachedAuthRetrievals successfully" in {
      val authRetrievals = CachedAuthRetrievals("AA123456A")
      val key = DataKey[CachedAuthRetrievals]("key3")

      repository.put(hc, key, authRetrievals).futureValue

      repository.get(hc, key).futureValue mustBe Some(authRetrievals)
    }

    "delete a key" in {
      val key = DataKey[DummyData]("key2")
      val value = DummyData("test-data")

      repository.put(hc, key, value).futureValue

      repository.delete(hc, key).futureValue

      repository.get(hc, key).futureValue mustBe None
    }

    "delete all keys" in {
      val key1 = DataKey[DummyData]("k1")
      val key2 = DataKey[DummyData]("k2")

      repository.put(hc, key1, DummyData("v1")).futureValue
      repository.put(hc, key2, DummyData("v2")).futureValue

      repository.deleteAll(hc).futureValue

      repository.get(hc, key1).futureValue mustBe None
      repository.get(hc, key2).futureValue mustBe None
    }

    "return None when invalid data is found" in {
      val key = DataKey[CachedAuthRetrievals]("key3")

      mongoComponent.database
        .getCollection("collectionName")
        .findOneAndUpdate(
          filter = Filters.equal("_id", "request-id"),
          update = Updates.combine(
            Updates.set("data." + key.unwrap, Codecs.toBson("garbage")),
            Updates.set("modifiedDetails.lastUpdated", new CurrentTimestampSupport().timestamp()),
            Updates.setOnInsert("_id", "request-id"),
            Updates.setOnInsert("modifiedDetails.createdAt", new CurrentTimestampSupport().timestamp())
          ),
          options = FindOneAndUpdateOptions()
            .upsert(true)
            .returnDocument(ReturnDocument.AFTER)
        )
        .toFuture()
        .futureValue

      repository.get(hc, key).futureValue mustBe None
    }

    "cache is using request id" in {
      val requestHc =
        HeaderCarrier(requestId = Some(RequestId("request-id")))

      val key = DataKey[DummyData]("key1")
      val value = DummyData("test-data")

      repository.put(requestHc, key, value).futureValue

      repository.get(requestHc, key).futureValue mustBe Some(value)
    }
  }

  case class DummyData(value: String)

  object DummyData {
    implicit val format: OFormat[DummyData] =
      Json.format[DummyData]
  }
}
