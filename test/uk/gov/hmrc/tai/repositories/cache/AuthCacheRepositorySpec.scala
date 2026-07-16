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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.tai.config.{CryptoProvider, MongoConfig}
import uk.gov.hmrc.tai.model.CachedAuthRetrievals
import uk.gov.hmrc.tai.util.BaseSpec

class AuthCacheRepositorySpec extends BaseSpec with MongoSupport {

  override implicit lazy val hc: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId("request-id")))

  private lazy val repository = new AuthCacheRepository(
    inject[MongoConfig],
    inject[CryptoProvider],
    mongoComponent
  )

  "AuthCacheRepository" must {

    "put and get session data" in {
      val key = DataKey[DummyData]("key1")
      val value = DummyData("test-data")

      repository.putSession(key, value).futureValue

      repository.getFromSession(key).futureValue mustBe Some(value)
    }

    "put and get CachedAuthRetrievals successfully" in {
      val authRetrievals = CachedAuthRetrievals("AA123456A")

      val key = DataKey[CachedAuthRetrievals]("auth-retrievals")

      repository.putSession(key, authRetrievals).futureValue

      repository.getFromSession(key).futureValue mustBe Some(authRetrievals)
    }

    "delete a key from session" in {
      val key = DataKey[DummyData]("key2")
      val value = DummyData("test-data")

      repository.putSession(key, value).futureValue

      repository.deleteFromSession(key).futureValue

      repository.getFromSession(key).futureValue mustBe None
    }

    "delete all session keys" in {
      val key1 = DataKey[DummyData]("k1")
      val key2 = DataKey[DummyData]("k2")

      repository.putSession(key1, DummyData("v1")).futureValue
      repository.putSession(key2, DummyData("v2")).futureValue

      repository.deleteAllFromSession.futureValue

      repository.getFromSession(key1).futureValue mustBe None
      repository.getFromSession(key2).futureValue mustBe None
    }

    "use request id as the cache id" in {
      val key = DataKey[DummyData]("key3")
      val value = DummyData("request-id-data")

      repository.putSession(key, value).futureValue

      repository.getFromSession(key).futureValue mustBe Some(value)
    }
  }

  case class DummyData(value: String)

  object DummyData {
    implicit val format: OFormat[DummyData] =
      Json.format[DummyData]
  }
}
