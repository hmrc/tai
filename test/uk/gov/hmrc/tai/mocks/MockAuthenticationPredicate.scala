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

package uk.gov.hmrc.tai.mocks

import org.mockito.ArgumentMatchers.any
import org.mockito.MockitoSugar
import org.scalatest._
import play.api.mvc._
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.auth.core.AuthorisedFunctions
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.controllers.auth.{AuthJourney, AuthenticatedRequest}
import uk.gov.hmrc.tai.util.ActionBuilderFixture

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait MockAuthenticationPredicate extends BeforeAndAfterEach with MockitoSugar {
  suite: TestSuite =>

  val cc: ControllerComponents = stubControllerComponents()

  val mockAuthService: AuthorisedFunctions = mock[AuthorisedFunctions]

  private val actionBuilderFixture = new ActionBuilderFixture {
    override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
      block(AuthenticatedRequest(request, Nino("AA000003A")))
  }

  lazy val loggedInAuthenticationAuthJourney: AuthJourney = new AuthJourney {
    val authWithUserDetails: ActionBuilder[AuthenticatedRequest, AnyContent] = actionBuilderFixture

    val authForEmployeeExpenses: ActionBuilder[AuthenticatedRequest, AnyContent] = actionBuilderFixture
  }

  val nino: Nino = new Generator(Random).nextNino
  val sessionIdValue: String = "some session id"
  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(sessionIdValue)))
  val cacheId: CacheId = CacheId(nino)
  val cacheIdNoSession: CacheId = CacheId.noSession(nino)

  val testAuthSuccessResponse = new ~(Some(nino.value), None)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthService)
    setupMockAuthRetrievalSuccess(testAuthSuccessResponse)
  }

  override protected def afterEach(): Unit = super.afterEach()

  def setupMockAuthRetrievalSuccess[X, Y](retrievalValue: X ~ Y): Unit =
    when(mockAuthService.authorised(any()))
      .thenReturn(new mockAuthService.AuthorisedFunction(EmptyPredicate) {
        override def retrieve[A](retrieval: Retrieval[A]): mockAuthService.AuthorisedFunctionWithResult[A] =
          new mockAuthService.AuthorisedFunctionWithResult[A](EmptyPredicate, retrieval) {
            override def apply[B](body: A => Future[B])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[B] =
              body.apply(retrievalValue.asInstanceOf[A])
          }
      })
}
