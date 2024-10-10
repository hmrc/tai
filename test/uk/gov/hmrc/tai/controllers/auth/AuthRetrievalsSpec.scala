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

package uk.gov.hmrc.tai.controllers.auth

import org.mockito.ArgumentMatchers._
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.{MessagesControllerComponents, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.tai.util.RetrievalOps.Ops
import uk.gov.hmrc.tai.util.{BaseSpec, EqualsAuthenticatedRequest}

import scala.annotation.nowarn
import scala.concurrent.Future

class AuthRetrievalsSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder().build()

  private val mockAuthConnector: AuthConnector = mock[AuthConnector]
  private lazy val mcc = app.injector.instanceOf[MessagesControllerComponents]
  type retrievalsType = Option[String] ~ Option[TrustedHelper]

  private object Stubs {
    @nowarn("msg=parameter request in method successBlock is never used")
    def successBlock(request: AuthenticatedRequest[_]): Future[Result] = Future.successful(Ok(""))
  }

  private val testRequest = FakeRequest("GET", "/paye/benefits/medical-benefit")

  val authAction = new AuthRetrievalsImpl(mockAuthConnector, mcc)

  "Auth Action" when {

    "a user navigating to home page" when {
      "logged in with Confidence Level of 200 or above" must {
        "be successfully redirected to the service page" in {
          val retrievalResult: Future[retrievalsType] =
            Future.successful(Some(nino.nino) ~ None)

          when(
            mockAuthConnector
              .authorise[retrievalsType](any(), any())(any(), any())
          )
            .thenReturn(retrievalResult)

          val stubs = spy(Stubs)

          val result = authAction.invokeBlock(testRequest, stubs.successBlock)

          status(result) mustBe OK

          val expectedRequest = AuthenticatedRequest(testRequest, nino)
          verify(stubs, times(1)).successBlock(argThat(EqualsAuthenticatedRequest(expectedRequest)))
        }
      }

      "logged in with as a trusted helper for a different user" must {
        "be successfully redirected to the service page and see different user's data" in {
          val attorney = TrustedHelper("principal", "attorney", "returnLink", otherNino.nino)

          val retrievalResult: Future[retrievalsType] =
            Future.successful(Some(nino.nino) ~ Some(attorney))

          when(
            mockAuthConnector
              .authorise[retrievalsType](any(), any())(any(), any())
          )
            .thenReturn(retrievalResult)

          val stubs = spy(Stubs)

          val result = authAction.invokeBlock(testRequest, stubs.successBlock)
          status(result) mustBe OK

          val expectedAttorneyRequest = AuthenticatedRequest(
            testRequest,
            otherNino
          )
          verify(stubs, times(1)).successBlock(argThat(EqualsAuthenticatedRequest(expectedAttorneyRequest)))
        }
      }

      "logged in with Confidence Level of 200 or above without a Nino" must {
        "throw a RunTimeException" in {

          val retrievalResult: Future[retrievalsType] =
            Future.successful(None ~ None)

          when(
            mockAuthConnector
              .authorise[retrievalsType](any(), any())(any(), any())
          )
            .thenReturn(retrievalResult)

          val result = authAction.invokeBlock(testRequest, Stubs.successBlock)

          a[RuntimeException] should be thrownBy status(result)
        }
      }

      "none credentials" must {
        "be successfully redirected to the service page" in {
          val retrievalResult: Future[retrievalsType] =
            Future.successful(Some(nino.nino) ~ None)

          when(
            mockAuthConnector
              .authorise[retrievalsType](any(), any())(any(), any())
          )
            .thenReturn(retrievalResult)

          val stubs = spy(Stubs)

          val result = authAction.invokeBlock(testRequest, stubs.successBlock)

          status(result) mustBe OK

          val expectedRequest = AuthenticatedRequest(
            testRequest,
            nino
          )
          verify(stubs, times(1)).successBlock(argThat(EqualsAuthenticatedRequest(expectedRequest)))
        }
      }

      "is not logged in" must {
        "throw an exception" in {
          when(mockAuthConnector.authorise(any(), any())(any(), any()))
            .thenReturn(Future.failed(new MissingBearerToken))

          val result = authAction.invokeBlock(testRequest, Stubs.successBlock)

          status(result) mustBe UNAUTHORIZED
        }
      }
    }
  }
}
