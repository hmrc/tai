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

package uk.gov.hmrc.tai.util

import play.api.mvc.*
import play.api.test.Helpers
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

object FakeAuthJourney extends AuthJourney {
  private val actionBuilderFixture: AuthenticatedActionBuilderFixture = new AuthenticatedActionBuilderFixture {
    override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
      block(AuthenticatedRequest(request, Nino("AA000003D")))
  }

  override val authWithUserDetails: AuthenticatedActionBuilderFixture = actionBuilderFixture

  override def authWithUserDetails(nino: Nino): AuthenticatedActionBuilderFixture = actionBuilderFixture

  override val authForEmployeeExpenses: AuthenticatedActionBuilderFixture = actionBuilderFixture

  override def authForEmployeeExpenses(nino: Nino): AuthenticatedActionBuilderFixture = actionBuilderFixture
}

trait AuthenticatedActionBuilderFixture extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result]

  override def parser: BodyParser[AnyContent] = Helpers.stubBodyParser()

  override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
