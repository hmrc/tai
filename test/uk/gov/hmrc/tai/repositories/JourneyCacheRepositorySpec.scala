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

package uk.gov.hmrc.tai.repositories

import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import uk.gov.hmrc.tai.connectors.CacheConnector
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class JourneyCacheRepositorySpec extends BaseSpec {

  private def createSUT(cacheConnector: CacheConnector) = new JourneyCacheRepository(cacheConnector)

  private def echoProgrammed(mock: CacheConnector): CacheConnector = {
    when(mock.createOrUpdate[Map[String, String]](any(), any(), any())(any())).thenAnswer(
      new Answer[Future[Map[String, String]]]() {
        override def answer(invocation: InvocationOnMock): Future[Map[String, String]] = {
          val suppliedMap: Map[String, String] = invocation.getArguments()(1).asInstanceOf[Map[String, String]]
          Future.successful(suppliedMap)
        }
      }
    )
    when(mock.createOrUpdateIncome[Map[String, String]](any(), any(), any())(any())).thenAnswer(
      new Answer[Future[Map[String, String]]]() {
        override def answer(invocation: InvocationOnMock): Future[Map[String, String]] = {
          val suppliedMap: Map[String, String] = invocation.getArguments()(1).asInstanceOf[Map[String, String]]
          Future.successful(suppliedMap)
        }
      }
    )
    mock
  }

  val testCache = Map("key1" -> "value1", "key2" -> "value2")

  "JourneyCacheRepository" must {

    "persist a named journey cache, and return the updated cache" when {

      "no existing cache is present for the specified journey" in {
        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", testCache).futureValue mustBe testCache
        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(),
          meq(testCache),
          meq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey" in {
        val existingCache = Map("key3" -> "value3")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", testCache).futureValue mustBe
          Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")
      }

      "an existing cache is present for the named journey, and the supplied cache replaces one of the existing values" in {
        val existingCache = Map("key3" -> "value3")
        val newCache = Map("key1" -> "value1", "key3" -> "revised")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", newCache).futureValue mustBe
          Map("key1" -> "value1", "key3" -> "revised")
      }
    }

    "persist an individual value, and return the updated cache" when {

      "no existing cache is present for the specified journey" in {
        val expectedMap = Map("key3" -> "value3")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", "key3", "value3").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "value4", "key5" -> "value5")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", "key5", "value5").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey, and the supplied value replaces one of the existing values" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "updated")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "testJourney", "key4", "updated").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("testJourney" + sut.JourneyCacheSuffix))(any())
      }
    }

    "retrive an existing cache, by journey name" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.find[Map[String, String]](any(), meq("exists_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))
      when(mockConnector.find[Map[String, String]](any(), meq("doesntexist_journey_cache"))(any()))
        .thenReturn(Future.successful(None))

      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "exists").futureValue mustBe Some(existingCache)
      sut.currentCache(cacheId, "doesntexist").futureValue mustBe None
    }

    "retrive an individual cached value, by journey name and key" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.find[Map[String, String]](any(), meq("exists_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))
      when(mockConnector.find[Map[String, String]](any(), meq("doesntexist_journey_cache"))(any()))
        .thenReturn(Future.successful(None))

      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "exists", "key3").futureValue mustBe Some("value3")
      sut.currentCache(cacheId, "exists", "key5").futureValue mustBe None
      sut.currentCache(cacheId, "doesntexist", "nochance").futureValue mustBe None
    }

    "delete a named journey cache" in {
      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.createOrUpdate[Map[String, String]](any(), any(), any())(any()))
        .thenReturn(Future.successful(Map.empty[String, String]))

      val sut = createSUT(mockConnector)
      sut.flush(cacheId, "testJourney").futureValue

      verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
        any(),
        meq(Map.empty[String, String]),
        meq("testJourney" + sut.JourneyCacheSuffix))(any())
    }


    //update income

    "persist a named journey cache, and return the updated cache *UpdateIncome" when {

      "no existing cache is present for the specified journey *UpdateIncome" in {
        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", testCache).futureValue mustBe testCache
        verify(mockConnector, times(1)).createOrUpdateIncome[Map[String, String]](
          any(),
          meq(testCache),
          meq("update-income" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey *UpdateIncome" in {
        val existingCache = Map("key3" -> "value3")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", testCache).futureValue mustBe
          Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")
      }

      "an existing cache is present for the named journey, and the supplied cache replaces one of the existing values *UpdateIncome" in {
        val existingCache = Map("key3" -> "value3")
        val newCache = Map("key1" -> "value1", "key3" -> "revised")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", newCache).futureValue mustBe
          Map("key1" -> "value1", "key3" -> "revised")
      }
    }

    "persist an individual value, and return the updated cache *UpdateIncome" when {

      "no existing cache is present for the specified journey *UpdateIncome" in {
        val expectedMap = Map("key3" -> "value3")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", "key3", "value3").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdateIncome[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("update-income" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey *UpdateIncome" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "value4", "key5" -> "value5")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", "key5", "value5").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdateIncome[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("update-income" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey, and the supplied value replaces one of the existing values *UpdateIncome" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "updated")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.findUpdateIncome[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        sut.cached(cacheId, "update-income", "key4", "updated").futureValue mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdateIncome[Map[String, String]](
          any(),
          meq(expectedMap),
          meq("update-income" + sut.JourneyCacheSuffix))(any())
      }
    }

    "retrieve an existing cache, by journey name *UpdateIncome" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.findUpdateIncome[Map[String, String]](any(), meq("update-income_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))


      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "update-income").futureValue mustBe Some(existingCache)
    }

    "return none for empty cache, by journey name *UpdateIncome" in {

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.findUpdateIncome[Map[String, String]](any(), meq("update-income_journey_cache"))(any()))
        .thenReturn(Future.successful(None))


      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "update-income").futureValue mustBe None
    }


    "retrive an individual cached value, by journey name and key *UpdateIncome" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.findUpdateIncome[Map[String, String]](any(), meq("update-income_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))


      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "update-income", "key3").futureValue mustBe Some("value3")

    }

    "return none for an individual cached value, by journey name and key *UpdateIncome" in {

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.findUpdateIncome[Map[String, String]](any(), meq("update-income_journey_cache"))(any()))
        .thenReturn(Future.successful(None))


      val sut = createSUT(mockConnector)
      sut.currentCache(cacheId, "update-income", "key5").futureValue mustBe None
      sut.currentCache(cacheId, "update-income", "nochance").futureValue mustBe None
    }


    "delete a named journey cache *UpdateIncome" in {
      //TODO: Failing test, mock not being called properly due to SUT. Need to add few more tests e.g. flushUpdateIncome
      val mockConnector = echoProgrammed(mock[CacheConnector])
      val sut = createSUT(mockConnector)

      when(mockConnector.createOrUpdateIncome[Map[String, String]](any(), any(), any())(any()))
        .thenReturn(Future.successful(Map.empty[String, String]))
      when(sut.currentCache(cacheIdNoSession, "update-income")) thenReturn Future.successful(Some(Map.empty[String, String]))

      sut.flushUpdateIncome(cacheIdNoSession, "update-income").futureValue

      verify(mockConnector, times(1)).createOrUpdateIncome[Map[String, String]](
        any(),
        meq(Map.empty[String, String]),
        meq("update-income" + sut.JourneyCacheSuffix))(any())
    }
  }
}
