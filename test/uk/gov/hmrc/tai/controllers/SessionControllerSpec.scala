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

package uk.gov.hmrc.tai.controllers

import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.CacheId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate
import uk.gov.hmrc.tai.repositories.SessionRepository

import scala.concurrent.Future
import scala.util.Random

class SessionControllerSpec extends PlaySpec with MockitoSugar with MockAuthenticationPredicate {

  "Session Controller" must {

    "return Accepted" when {
      "invalidate the cache" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(cacheId)(Matchers.any()))
          .thenReturn(Future.successful(true))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCache()(FakeRequest())

        status(result) mustBe ACCEPTED
      }
    }

    "return Internal Server Error" when {
      "not able to invalidate the cache" in {
        val mockSessionRepository = mock[SessionRepository]
        when(mockSessionRepository.invalidateCache(cacheId)(Matchers.any()))
          .thenReturn(Future.successful(false))

        val sut = createSUT(mockSessionRepository)
        val result = sut.invalidateCache()(FakeRequest())

        status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }
  }

  val cacheId = CacheId(nino)(HeaderCarrier())

  private def createSUT(
    sessionRepository: SessionRepository,
    authentication: AuthenticationPredicate = loggedInAuthenticationPredicate) =
    new SessionController(sessionRepository, authentication)
}
