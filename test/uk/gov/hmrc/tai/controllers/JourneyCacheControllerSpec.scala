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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.any
import play.api.libs.json.{JsString, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.repositories.deprecated.JourneyCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class JourneyCacheControllerSpec extends BaseSpec {

  private def createSUT(repository: JourneyCacheRepository) =
    new JourneyCacheController(repository, loggedInAuthenticationAuthJourney, cc)

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("X-Session-ID" -> "test")

  "JourneyCacheController" must {

    val testMap = Map("key1" -> "value1", "key2" -> "value2")

    "supply a named journey cache on GET request" in {
      val mockRepository = mock[JourneyCacheRepository]

      when(mockRepository.currentCache(any(), any()))
        .thenReturn(Future.successful(Some(testMap)))

      val sut = createSUT(mockRepository)
      val result = sut.currentCache("testjourney")(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("key1" -> "value1", "key2" -> "value2")
    }

    "supply an individual cache entry on GET request" in {
      val mockRepository = mock[JourneyCacheRepository]

      when(mockRepository.currentCache(any(), any(), any()))
        .thenReturn(Future.successful(Some("value3")))

      val sut = createSUT(mockRepository)
      val result = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
      status(result) mustBe OK
      contentAsJson(result) mustBe JsString("value3")
    }

    "accept and persist a valid POST'ed cache" in {
      val cacheJson = Json.obj("key1" -> "value1", "key2" -> "value2")
      val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), cacheJson)
        .withHeaders("content-type" -> "application/json", "X-Session-ID" -> "test")

      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.cached(any(), any(), any()))
        .thenReturn(Future.successful(testMap))

      val sut = createSUT(mockRepository)
      val result = sut.cached("testjourney")(fakeRequest)
      status(result) mustBe CREATED

      val json = contentAsJson(result)
      json mustBe cacheJson
      json.as[Map[String, String]] mustBe testMap
    }

    "accept and process a DELETE cache flush instruction" in {
      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.flush(any(), any()))
        .thenReturn(Future.successful(true))

      val sut = createSUT(mockRepository)
      val result = sut.flush("testjourney")(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
      status(result) mustBe NO_CONTENT
    }

    "return a 204 no content response" when {

      "a cache is not found for the requested journey" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any()))
          .thenReturn(
            Future.successful(None),
            Future.successful(Some(Map.empty[String, String]))
          )

        val sut = createSUT(mockRepository)
        val result = sut.currentCache("testjourney")(fakeRequest)
        status(result) mustBe NO_CONTENT

        val emptyMapResult = sut.currentCache("testjourney")(fakeRequest)
        status(emptyMapResult) mustBe NO_CONTENT
      }

      "an individual value is not found within an existing cache" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
        status(result) mustBe NO_CONTENT
      }

      "an individual value is found within an existing cache, but is the empty string" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(Some(" ")))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
        status(result) mustBe NO_CONTENT
      }
    }

    "return an 500 internal server error response" when {

      "an internal error is encountered on any request" in {
        val failResult = Future.failed(new HttpException("something broke", BAD_GATEWAY))

        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any()))
          .thenReturn(failResult)
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(failResult)
        when(mockRepository.cached(any(), any(), any()))
          .thenReturn(failResult)
        when(mockRepository.flush(any(), any()))
          .thenReturn(failResult)

        val sut = createSUT(mockRepository)
        val result1 = sut.currentCache("testjourney")(fakeRequest)
        status(result1) mustBe INTERNAL_SERVER_ERROR

        val result2 = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
        status(result2) mustBe INTERNAL_SERVER_ERROR

        val cacheJson = Json.obj("key1" -> "value1", "key2" -> "value2")
        val result3 = sut.cached("testjourney")(
          FakeRequest("POST", "/", FakeHeaders(), cacheJson)
            .withHeaders("content-type" -> "application/json", "X-Session-ID" -> "test")
        )
        status(result3) mustBe INTERNAL_SERVER_ERROR

        val result4 = sut.flush("testjourney")(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
        status(result4) mustBe INTERNAL_SERVER_ERROR
      }
    }
    "supply a named journey cache on GET request *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]

      when(mockRepository.currentCache(any(), any()))
        .thenReturn(Future.successful(Some(testMap)))

      val sut = createSUT(mockRepository)
      val result = sut.currentCache("update-income")(fakeRequest)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.obj("key1" -> "value1", "key2" -> "value2")
    }

    "supply an individual cache entry on GET request *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]

      when(mockRepository.currentCache(any(), any(), any()))
        .thenReturn(Future.successful(Some("value3")))

      val sut = createSUT(mockRepository)
      val result = sut.currentCacheValue("update-income", "key3")(fakeRequest)
      status(result) mustBe OK
      contentAsJson(result) mustBe JsString("value3")
    }

    "accept and persist a valid POST'ed cache *UpdateIncome" in {
      val cacheJson = Json.obj("key1" -> "value1", "key2" -> "value2")
      val fakeRequest = FakeRequest("POST", "/", FakeHeaders(), cacheJson)
        .withHeaders("content-type" -> "application/json", "X-Session-ID" -> "test")

      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.cached(any(), any(), any()))
        .thenReturn(Future.successful(testMap))

      val sut = createSUT(mockRepository)
      val result = sut.cached("update-income")(fakeRequest)
      status(result) mustBe CREATED

      val json = contentAsJson(result)
      json mustBe cacheJson
      json.as[Map[String, String]] mustBe testMap
    }

    "accept and process a DELETE cache flush instruction *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.flushUpdateIncome(any(), any()))
        .thenReturn(Future.successful((): Unit))

      val sut = createSUT(mockRepository)
      val result = sut.flush("update-income")(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
      status(result) mustBe NO_CONTENT
    }

    "accept and process a DELETE cache flush instruction with empId *UpdateIncome" in {
      val mockRepository = mock[JourneyCacheRepository]
      when(mockRepository.flushUpdateIncomeWithEmpId(any(), any(), any()))
        .thenReturn(Future.successful((): Unit))

      val sut = createSUT(mockRepository)
      val result =
        sut.flushWithEmpId("update-income", 1)(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
      status(result) mustBe NO_CONTENT
    }

    "return a 204 no content response *UpdateIncome" when {

      "a cache is not found for the requested journey" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any()))
          .thenReturn(
            Future.successful(None),
            Future.successful(Some(Map.empty[String, String]))
          )

        val sut = createSUT(mockRepository)
        val result = sut.currentCache("update-income")(fakeRequest)
        status(result) mustBe NO_CONTENT

        val emptyMapResult = sut.currentCache("update-income")(fakeRequest)
        status(emptyMapResult) mustBe NO_CONTENT
      }

      "an individual value is not found within an existing cache " in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("update-income", "key3")(fakeRequest)
        status(result) mustBe NO_CONTENT
      }

      "an individual value is found within an existing cache, but is the empty string" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(Some(" ")))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("update-income", "key3")(fakeRequest)
        status(result) mustBe NO_CONTENT
      }
    }

    "return an 500 internal server error response *UpdateIncome" when {

      "an internal error is encountered on any request" in {
        val failResult = Future.failed(new HttpException("something broke", BAD_GATEWAY))

        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any()))
          .thenReturn(failResult)
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(failResult)
        when(mockRepository.cached(any(), any(), any()))
          .thenReturn(failResult)
        when(mockRepository.flushUpdateIncome(any(), any()))
          .thenReturn(failResult)
        when(mockRepository.flushUpdateIncomeWithEmpId(any(), any(), any()))
          .thenReturn(failResult)

        val sut = createSUT(mockRepository)
        val result1 = sut.currentCache("update-income")(fakeRequest)
        status(result1) mustBe INTERNAL_SERVER_ERROR

        val result2 = sut.currentCacheValue("update-income", "key3")(fakeRequest)
        status(result2) mustBe INTERNAL_SERVER_ERROR

        val cacheJson = Json.obj("key1" -> "value1", "key2" -> "value2")
        val result3 = sut.cached("update-income")(
          FakeRequest("POST", "/", FakeHeaders(), cacheJson)
            .withHeaders("content-type" -> "application/json", "X-Session-ID" -> "test")
        )
        status(result3) mustBe INTERNAL_SERVER_ERROR

        val result4 = sut.flush("update-income")(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
        status(result4) mustBe INTERNAL_SERVER_ERROR

        val result5 =
          sut.flushWithEmpId("update-income", 1)(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
        status(result5) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
