/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.connectors.cache

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.tai.connectors.ConnectorBaseSpec
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository

import scala.concurrent.Future

class CachingConnectorSpec extends ConnectorBaseSpec {

  lazy val mockSessionCacheRepository: TaiSessionCacheRepository  = mock[TaiSessionCacheRepository]

  def connector: CachingConnector = new CachingConnector(mockSessionCacheRepository)

  private class FakeConnector() {
    def fakeMethod() = Future.successful("underlying method value")

    def fakeEitherT(either: Boolean): EitherT[Future, String, String] = {
      if(either) EitherT.rightT("underlying method value right")
      else EitherT.leftT("underlying method value left")
    }
  }

  lazy private val mockConnector = mock[FakeConnector]

  override def beforeEach() = {
    super.beforeEach()
    reset(mockSessionCacheRepository, mockConnector)
  }

  "CachingConnector" when {
    "calling cache" must {
      "call the cache repository and not underlying method if value present" in {

        when(mockSessionCacheRepository.getFromSession[String](DataKey(any()))(any(), any())).thenReturn(Future.successful(Some("cached value")))
        when(mockSessionCacheRepository.putSession[String](DataKey(any()), any())(any(), any(), any())).thenReturn(Future.successful(("", "")))
        when(mockConnector.fakeMethod()).thenReturn(Future.successful(""))

        val result = connector.cache[String]("key")(mockConnector.fakeMethod()).futureValue

        result mustBe "cached value"
        verify(mockSessionCacheRepository, times(1)).getFromSession[String](DataKey(any()))(any(), any())
        verify(mockSessionCacheRepository, times(0)).putSession[String](DataKey(any()), any())(any(), any(), any())
        verify(mockConnector, times(0)).fakeMethod()
      }
      "call underlying method and store value in the cache if no value is present already" in {
        when(mockSessionCacheRepository.getFromSession[String](DataKey(any()))(any(), any())).thenReturn(Future.successful(None))
        when(mockSessionCacheRepository.putSession[String](DataKey(any()), any())(any(), any(), any())).thenReturn(Future.successful(("", "")))
        when(mockConnector.fakeMethod()).thenReturn(Future.successful("underlying method value"))

        val result = connector.cache[String]("key")(mockConnector.fakeMethod()).futureValue

        result mustBe "underlying method value"

        verify(mockSessionCacheRepository, times(1)).getFromSession[String](DataKey(any()))(any(), any())
        verify(mockSessionCacheRepository, times(1)).putSession[String](DataKey(any()), any())(any(), any(), any())
        verify(mockConnector, times(1)).fakeMethod()
      }
    }

    "calling cacheEitherT" must {
      "return a right and not call underlying method" in {
        when(mockSessionCacheRepository.getFromSession[String](DataKey(any()))(any(), any())).thenReturn(Future.successful(Some("cached value")))
        when(mockSessionCacheRepository.putSession[String](DataKey(any()), any())(any(), any(), any())).thenReturn(Future.successful(("", "")))
        when(mockConnector.fakeEitherT(any())).thenReturn(EitherT.rightT("underlying method value"))

        val result = connector.cacheEitherT[String, String]("key")(mockConnector.fakeEitherT(true)).value.futureValue

        result mustBe Right("cached value")

        verify(mockSessionCacheRepository, times(1)).getFromSession[String](DataKey(any()))(any(), any())
        verify(mockSessionCacheRepository, times(0)).putSession[String](DataKey(any()), any())(any(), any(), any())
        verify(mockConnector, times(0)).fakeEitherT(any())
      }
      "return a right from underlying method and put method value in cache" in {
        when(mockSessionCacheRepository.getFromSession[String](DataKey(any()))(any(), any())).thenReturn(Future.successful(None))
        when(mockSessionCacheRepository.putSession[String](DataKey(any()), any())(any(), any(), any())).thenReturn(Future.successful(("", "")))
        when(mockConnector.fakeEitherT(any())).thenReturn(EitherT.rightT("underlying method value"))

        val result = connector.cacheEitherT[String, String]("key")(mockConnector.fakeEitherT(true)).value.futureValue

        result mustBe Right("underlying method value")

        verify(mockSessionCacheRepository, times(1)).getFromSession[String](DataKey(any()))(any(), any())
        verify(mockSessionCacheRepository, times(1)).putSession[String](DataKey(any()), any())(any(), any(), any())
        verify(mockConnector, times(1)).fakeEitherT(any())
      }
    }
  }

}
