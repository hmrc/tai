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
import org.mockito.Mockito.when
import play.api.http.Status.*
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status, stubControllerComponents}
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, InsufficientConfidenceLevel}
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class AuthActionSpec extends BaseSpec {

  private class TestAction(authAction: AuthAction) {
    val action: Action[AnyContent] =
      (stubControllerComponents().actionBuilder andThen authAction) { request =>
        Ok(request.nino.value)
      }
  }

  private val mockAuthConnector = mock[AuthConnector]

  "AuthAction" when {
    "the user has a NINO" must {
      "allow the request" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.successful(Some("AA000003D"))
        )

        val authAction = new AuthActionImpl(mockAuthConnector, stubControllerComponents())
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe OK
        contentAsString(result) mustBe "AA000003D"
      }
    }

    "the user has no NINO" must {
      "return Unauthorized" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.successful(None)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, stubControllerComponents())
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }

    "auth throws NoActiveSession" must {
      "return Unauthorized" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.failed(new BearerTokenExpired)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, stubControllerComponents())
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }

    "auth throws InsufficientConfidenceLevel" must {
      "return Unauthorized" in {
        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(
          Future.failed(new InsufficientConfidenceLevel)
        )

        val authAction = new AuthActionImpl(mockAuthConnector, stubControllerComponents())
        val testAction = new TestAction(authAction)

        val result = testAction.action(FakeRequest())

        status(result) mustBe UNAUTHORIZED
      }
    }
  }
}
