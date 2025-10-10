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
import org.mockito.Mockito.when
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.tai.repositories.deprecated.SessionRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class SessionControllerSpec extends BaseSpec {

  private def createSUT(sessionRepository: SessionRepository) =
    new SessionController(sessionRepository, loggedInAuthenticationAuthJourney, cc)

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("X-Session-ID" -> "test")

  "Session Controller" must {

    "return Accepted" when {
      "invalidate the cache" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(any()))
          .thenReturn(Future.successful(true))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCache(fakeRequest)

        status(result) mustBe ACCEPTED
      }

      "invalidate the cache with nino" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(any()))
          .thenReturn(Future.successful(true))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCacheWithNino(nino)(fakeRequest)

        status(result) mustBe ACCEPTED
      }
    }

    "return Internal Server Error" when {
      "not able to invalidate the cache" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(any()))
          .thenReturn(Future.successful(false))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCache(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "not able to invalidate the cache with nino" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(any()))
          .thenReturn(Future.successful(false))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCacheWithNino(nino)(fakeRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

}
