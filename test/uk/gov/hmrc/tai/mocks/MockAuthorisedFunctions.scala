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

import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Suite}
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AuthorisationException, AuthorisedFunctions, MissingBearerToken}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.tai.connectors.CacheId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait MockAuthenticationPredicate extends BeforeAndAfterEach with MockitoSugar {
  self: Suite =>

  val mockAuthService = mock[AuthorisedFunctions]

  val loggedInAuthenticationPredicate = new AuthenticationPredicate(mockAuthService)

  val nino = new Generator(Random).nextNino

  implicit val hc = HeaderCarrier(sessionId = Some(SessionId("TEST")))
  val cacheId = CacheId(nino)

  val testAuthSuccessResponse = new ~(Some(nino.value), None)

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthService)
    setupMockAuthRetrievalSuccess(testAuthSuccessResponse)
  }

  def setupMockAuthRetrievalSuccess[X, Y](retrievalValue: X ~ Y): Unit =
    when(mockAuthService.authorised(any()))
      .thenReturn(new mockAuthService.AuthorisedFunction(EmptyPredicate) {
        override def retrieve[A](retrieval: Retrieval[A]) =
          new mockAuthService.AuthorisedFunctionWithResult[A](EmptyPredicate, retrieval) {
            override def apply[B](body: A => Future[B])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[B] =
              body.apply(retrievalValue.asInstanceOf[A])
          }
      })

}
