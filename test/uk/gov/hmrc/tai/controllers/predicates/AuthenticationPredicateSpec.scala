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

package uk.gov.hmrc.tai.controllers.predicates

import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.mocks.MockAuthenticationPredicate

import scala.util.Random
import scala.concurrent.{ExecutionContext, Future}

class AuthenticationPredicateSpec
    extends PlaySpec with MockitoSugar with MockAuthenticationPredicate with ScalaFutures {

  class SUT(val authentication: AuthenticationPredicate) extends BaseController {
    def get: Action[AnyContent] = authentication.async { implicit request =>
      Future.successful(Ok)
    }
  }

  object TestAuthenticationPredicate extends AuthenticationPredicate(mockAuthService)

  val authErrors = Seq[RuntimeException](
    new InsufficientConfidenceLevel,
    new InsufficientEnrolments,
    new UnsupportedAffinityGroup,
    new UnsupportedCredentialRole,
    new UnsupportedAuthProvider,
    new IncorrectCredentialStrength,
    new InternalError,
    new InvalidBearerToken,
    new BearerTokenExpired,
    new MissingBearerToken,
    new SessionRecordNotFound
  )

  authErrors.foreach(error => {
    s"return UNAUTHORIZED when auth throws a $error" in {
      val mockAuthService = mock[AuthorisedFunctions]

      when(mockAuthService.authorised(any()))
        .thenReturn(new mockAuthService.AuthorisedFunction(EmptyPredicate) {
          override def retrieve[A](retrieval: Retrieval[A]) =
            new mockAuthService.AuthorisedFunctionWithResult[A](EmptyPredicate, retrieval) {
              override def apply[B](
                body: A => Future[B])(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[B] =
                Future.failed(error)
            }
        })

      object TestAuthenticationPredicate extends AuthenticationPredicate(mockAuthService)
      val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())

      status(result) mustBe Status.UNAUTHORIZED
    }
  })

  "return OK and contain the user's NINO when called with an Authenticated user" in {
    class SUT(val authentication: AuthenticationPredicate) extends BaseController {
      def get: Action[AnyContent] = authentication.async { implicit request =>
        request.nino mustBe nino
        Future.successful(Ok)
      }
    }

    val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())

    status(result) mustBe Status.OK
  }

  "return OK and contain the trusted helper's NINO when called with an Authenticated user" in {
    val principalNino = new Generator(Random).nextNino
    val trustedHelperAuthSuccessResponse =
      new ~(
        Some(nino.value),
        Some(TrustedHelper("principal name", "attorney name", "return url", principalNino.toString())))

    setupMockAuthRetrievalSuccess(trustedHelperAuthSuccessResponse)

    class SUT(val authentication: AuthenticationPredicate) extends BaseController {
      def get: Action[AnyContent] = authentication.async { implicit request =>
        request.nino mustBe principalNino
        Future.successful(Ok)
      }
    }

    val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())
    status(result) mustBe Status.OK
  }
}
