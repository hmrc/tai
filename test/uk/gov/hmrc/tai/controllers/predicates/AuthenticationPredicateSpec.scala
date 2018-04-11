/*
 * Copyright 2018 HM Revenue & Customs
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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Status
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.MissingBearerToken
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.mocks.{MockAuthorisedUser, MockUnauthorisedUser}

import scala.concurrent.Future


class AuthenticationPredicateSpec extends PlaySpec with MockitoSugar {

  "async for get" must {
    "return UNAUTHORIZED when called with an Unauthenticated user" in {
      object TestAuthenticationPredicate extends AuthenticationPredicate(MockUnauthorisedUser)
      val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return OK when called with an Authenticated user" in {
      object TestAuthenticationPredicate extends AuthenticationPredicate(MockAuthorisedUser)
      val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())
      status(result) mustBe Status.OK
    }
  }

  "async for post" must {
    "return UNAUTHORIZED when called with an Unauthenticated user" in {
      object TestAuthenticationPredicate extends AuthenticationPredicate(MockUnauthorisedUser)
      val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())
      ScalaFutures.whenReady(result.failed) { e =>
        e mustBe a[MissingBearerToken]
      }
    }

    "return OK when called with an Authenticated user" in {
      object TestAuthenticationPredicate extends AuthenticationPredicate(MockAuthorisedUser)
      val result = new SUT(TestAuthenticationPredicate).get.apply(FakeRequest())
      status(result) mustBe Status.OK
    }
  }

  private class SUT(val authentication: AuthenticationPredicate) extends BaseController {
    def get: Action[AnyContent] = authentication.async {
      implicit request =>
        Future.successful(Ok)
    }

    def post: Action[JsValue] = authentication.async(parse.json){
      implicit request =>
        withJsonBody[String] {
          _ => Future.successful(Ok)
        }
    }
  }
}
