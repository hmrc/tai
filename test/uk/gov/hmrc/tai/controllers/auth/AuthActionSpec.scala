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

package uk.gov.hmrc.tai.controllers.auth

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, verify, when}
import play.api.http.Status.*
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, InsufficientConfidenceLevel}
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.CachedAuthRetrievals
import uk.gov.hmrc.tai.repositories.cache.AuthCacheRepository
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec {

  private val mockAuthConnector = mock[AuthConnector]
  private val mockAuthCacheRepository = mock[AuthCacheRepository]
  private val mockMongoConfig = mock[MongoConfig]

  private class TestAction(authAction: AuthAction) {
    val action: Action[AnyContent] = (cc.actionBuilder andThen authAction) { request =>
      Ok(request.nino.value)
    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthConnector, mockAuthCacheRepository, mockMongoConfig)
  }

  "AuthAction" when {
    "the user has a NINO" must {
      "allow the request when cache is disabled" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(false)
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.successful(Some("AA000003D"))
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "AA000003D"

        verify(mockAuthConnector).authorise(any(), any())(any(), any())
        verify(mockAuthCacheRepository, never()).getFromSession[CachedAuthRetrievals](any())(any(), any())
        verify(mockAuthCacheRepository, never()).putSession[CachedAuthRetrievals](any(), any())(any(), any())
      }
    }

    "the user has no NINO" must {
      "return Unauthorized" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(false)
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.successful(None)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }

    "auth throws NoActiveSession" must {
      "return Unauthorized" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(false)
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.failed(new BearerTokenExpired)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)

        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }

    "auth throws InsufficientConfidenceLevel" must {
      "return Unauthorized" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(false)
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.failed(new InsufficientConfidenceLevel)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)

        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }

    "Auth cache is enabled" must {
      "use cached retrievals on cache hit" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(true)
        when(mockAuthCacheRepository.getFromSession[CachedAuthRetrievals](any())(any(), any())).thenReturn(
          Future.successful(Some(CachedAuthRetrievals("AA000003D")))
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "AA000003D"

        verify(mockAuthCacheRepository).getFromSession[CachedAuthRetrievals](any())(any(), any())
        verify(mockAuthConnector, never()).authorise(any(), any())(any(), any())
        verify(mockAuthCacheRepository, never()).putSession[CachedAuthRetrievals](any(), any())(any(), any())
      }

      "retrieve from auth and cache the result on cache miss" in {
        when(mockMongoConfig.mongoAuthEnabled).thenReturn(true)
        when(mockAuthCacheRepository.getFromSession[CachedAuthRetrievals](any())(any(), any())).thenReturn(
          Future.successful(None)
        )

        when(mockAuthCacheRepository.putSession[CachedAuthRetrievals](any(), any())(any(), any())).thenReturn(
          Future.successful("id" -> "id")
        )

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.successful(Some("AA000003D"))
        )

        val authAction = new AuthActionImpl(mockAuthConnector, mockMongoConfig, mockAuthCacheRepository, cc)
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "AA000003D"

        verify(mockAuthCacheRepository).getFromSession[CachedAuthRetrievals](any())(any(), any())
        verify(mockAuthCacheRepository).putSession[CachedAuthRetrievals](any(), any())(any(), any())
        verify(mockAuthConnector).authorise(any(), any())(any(), any())
      }
    }
  }
}
