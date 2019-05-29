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

package uk.gov.hmrc.tai.mocks

import org.scalatest.mockito.MockitoSugar
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.{AuthorisedFunctions, MissingBearerToken}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.auth.MicroserviceAuthConnector
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

import scala.concurrent.{ExecutionContext, Future}

trait MockAuthorisedFunctions extends AuthorisedFunctions with MockitoSugar {
  override val authConnector: MicroserviceAuthConnector = mock[MicroserviceAuthConnector]
}

object MockAuthorisedUser extends MockAuthorisedFunctions {
  override def authorised(predicate: Predicate): AuthorisedFunction = new AuthorisedFunction(predicate) {
    override def apply[A](body: => Future[A])(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[A] = body
  }
}

object MockUnauthorisedUser extends MockAuthorisedFunctions {
  override def authorised(predicate: Predicate): AuthorisedFunction = new AuthorisedFunction(predicate) {
    override def apply[A](body: => Future[A])(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[A] = Future.failed(new MissingBearerToken)
  }
}

trait MockAuthenticationPredicate extends MockitoSugar {
  val cc: ControllerComponents = mock[ControllerComponents]

  val loggedInAuthenticationPredicate = new AuthenticationPredicate(MockAuthorisedUser, cc)
  val notLoggedInAuthenticationPredicate = new AuthenticationPredicate(MockUnauthorisedUser, cc)
}