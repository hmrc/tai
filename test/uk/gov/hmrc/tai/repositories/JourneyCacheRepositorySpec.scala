/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mockito.Matchers
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.tai.connectors.CacheConnector
import uk.gov.hmrc.tai.controllers.FakeTaiPlayApplication

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import uk.gov.hmrc.http.HeaderCarrier

class JourneyCacheRepositorySpec extends PlaySpec with MockitoSugar with FakeTaiPlayApplication{

  "JourneyCacheRepository" must {

    val testCache = Map("key1" -> "value1", "key2" -> "value2")

    "persist a named journey cache, and return the updated cache" when {

      "no existing cache is present for the specified journey" in {
        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(None))

        val sut = createSUT(mockConnector)
        Await.result(sut.cached("testJourney", testCache)(hc), 5 seconds) mustBe testCache
        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](any(),  Matchers.eq(testCache), Matchers.eq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey" in {
        val existingCache = Map("key3" -> "value3")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        Await.result(sut.cached("testJourney", testCache)(hc), 5 seconds) mustBe
          Map("key1" -> "value1", "key2" -> "value2", "key3" -> "value3")
      }

      "an existing cache is present for the named journey, and the supplied cache replaces one of the existing values" in {
        val existingCache = Map("key3" -> "value3")
        val newCache = Map("key1" -> "value1", "key3" -> "revised")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        Await.result(sut.cached("testJourney", newCache)(hc), 5 seconds) mustBe
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
        Await.result(sut.cached("testJourney", "key3", "value3")(hc), 5 seconds) mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(), Matchers.eq(expectedMap), Matchers.eq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "value4", "key5" -> "value5")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        Await.result(sut.cached("testJourney", "key5", "value5")(hc), 5 seconds) mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(), Matchers.eq(expectedMap), Matchers.eq("testJourney" + sut.JourneyCacheSuffix))(any())
      }

      "an existing cache is present for the named journey, and the supplied value replaces one of the existing values" in {
        val existingCache = Map("key3" -> "value3", "key4" -> "value4")
        val expectedMap = Map("key3" -> "value3", "key4" -> "updated")

        val mockConnector = echoProgrammed(mock[CacheConnector])
        when(mockConnector.find[Map[String, String]](any(), any())(any()))
          .thenReturn(Future.successful(Some(existingCache)))

        val sut = createSUT(mockConnector)
        Await.result(sut.cached("testJourney", "key4", "updated")(hc), 5 seconds) mustBe expectedMap

        verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
          any(), Matchers.eq(expectedMap), Matchers.eq("testJourney" + sut.JourneyCacheSuffix))(any())
      }
    }

    "retrive an existing cache, by journey name" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.find[Map[String, String]](any(), Matchers.eq("exists_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))
      when(mockConnector.find[Map[String, String]](any(), Matchers.eq("doesntexist_journey_cache"))(any()))
        .thenReturn(Future.successful(None))

      val sut = createSUT(mockConnector)
      Await.result(sut.currentCache("exists")(hc), 5 seconds) mustBe Some(existingCache)
      Await.result(sut.currentCache("doesntexist")(hc), 5 seconds) mustBe None
    }

    "retrive an individual cached value, by journey name and key" in {
      val existingCache = Map("key3" -> "value3", "key4" -> "value4")

      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.find[Map[String, String]](any(), Matchers.eq("exists_journey_cache"))(any()))
        .thenReturn(Future.successful(Some(existingCache)))
      when(mockConnector.find[Map[String, String]](any(), Matchers.eq("doesntexist_journey_cache"))(any()))
        .thenReturn(Future.successful(None))

      val sut = createSUT(mockConnector)
      Await.result(sut.currentCache("exists", "key3")(hc), 5 seconds) mustBe Some("value3")
      Await.result(sut.currentCache("exists", "key5")(hc), 5 seconds) mustBe None
      Await.result(sut.currentCache("doesntexist", "nochance")(hc), 5 seconds) mustBe None
    }

    "delete a named journey cache" in {
      val mockConnector = echoProgrammed(mock[CacheConnector])
      when(mockConnector.createOrUpdate[Map[String, String]](any(), any(), any())(any()))
        .thenReturn(Future.successful(Map.empty[String, String]))

      val sut = createSUT(mockConnector)
      Await.result(sut.flush("testJourney")(hc), 5 seconds)

      verify(mockConnector, times(1)).createOrUpdate[Map[String, String]](
        any(), Matchers.eq(Map.empty[String, String]), Matchers.eq("testJourney" + sut.JourneyCacheSuffix))(any())
    }
  }


  private def createSUT(cacheConnector: CacheConnector) = new JourneyCacheRepository(cacheConnector) {
    override def sessionId(implicit hc: HeaderCarrier): String = "fake_session_id"
  }

  private def echoProgrammed(mock: CacheConnector): CacheConnector = {
    when(mock.createOrUpdate[Map[String, String]](any(), any(), any())(any())).thenAnswer(
      new Answer[Future[Map[String, String]]]() {
        override def answer(invocation: InvocationOnMock): Future[Map[String, String]] = {
          val suppliedMap: Map[String, String] = invocation.getArguments()(1).asInstanceOf[Map[String, String]]
          Future.successful(suppliedMap)
        }
      }
    )
    mock
  }

  private implicit val hc = HeaderCarrier()
}