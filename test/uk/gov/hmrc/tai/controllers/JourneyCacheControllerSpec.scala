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

package uk.gov.hmrc.tai.controllers

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.tai.repositories.JourneyCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class JourneyCacheControllerSpec extends BaseSpec {

  private def createSUT(repository: JourneyCacheRepository) =
    new JourneyCacheController(repository, loggedInAuthenticationPredicate, cc)

  val fakeRequest = FakeRequest().withHeaders("X-Session-ID" -> "test")

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
        .withHeaders(("content-type" -> "application/json"), ("X-Session-ID" -> "test"))

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

    "return a 404 not found response" when {

      "a cache is not found for the requested journey" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any()))
          .thenReturn(Future.successful(None))
          .thenReturn(Future.successful(Some(Map.empty[String, String])))

        val sut = createSUT(mockRepository)
        val result = sut.currentCache("testjourney")(fakeRequest)
        status(result) mustBe NOT_FOUND

        val emptyMapResult = sut.currentCache("testjourney")(fakeRequest)
        status(emptyMapResult) mustBe NOT_FOUND
      }

      "an individual value is not found within an existing cache" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
        status(result) mustBe NOT_FOUND
      }

      "an individual value is found within an existing cache, but is the empty string" in {
        val mockRepository = mock[JourneyCacheRepository]
        when(mockRepository.currentCache(any(), any(), any()))
          .thenReturn(Future.successful(Some(" ")))

        val sut = createSUT(mockRepository)
        val result = sut.currentCacheValue("testjourney", "key3")(fakeRequest)
        status(result) mustBe NOT_FOUND
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
            .withHeaders(("content-type" -> "application/json"), ("X-Session-ID" -> "test")))
        status(result3) mustBe INTERNAL_SERVER_ERROR

        val result4 = sut.flush("testjourney")(FakeRequest("DELETE", "").withHeaders("X-Session-ID" -> "test"))
        status(result4) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
