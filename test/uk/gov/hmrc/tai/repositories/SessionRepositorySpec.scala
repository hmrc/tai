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

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.mockito.{Matchers, Mockito}
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.CacheConnector

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class SessionRepositorySpec extends PlaySpec with MockitoSugar {

  "Session Repository" must {
    "return boolean" when {
      "invalidate the cache" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.removeById(any()))
          .thenReturn(Future.successful(true))

        val sut = createSUT(mockCacheConnector)
        val result = Await.result(sut.invalidateCache()(hcWithSession), 5.seconds)

        result mustBe true
      }
    }

    "throw an exception" when {
      "session is not present" in {
        val mockCacheConnector = mock[CacheConnector]
        when(mockCacheConnector.removeById(any()))
          .thenReturn(Future.successful(true))

        val sut = createSUT(mockCacheConnector)
        val ex = the[RuntimeException] thrownBy Await.result(sut.invalidateCache()(hcWithoutSession), 5.seconds)

        ex.getMessage mustBe "Error while fetching session id"
      }
    }
  }

  private val hcWithSession = HeaderCarrier(sessionId = Some(SessionId("123456")))
  private val hcWithoutSession = HeaderCarrier(sessionId = None)

  private def createSUT(cacheConnector: CacheConnector) =
    new SessionRepository(cacheConnector)
}