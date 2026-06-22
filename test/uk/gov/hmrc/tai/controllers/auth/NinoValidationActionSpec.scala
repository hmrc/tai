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
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{AnyContent, Request, Results}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.FandFConnector
import uk.gov.hmrc.tai.model.TrustedHelper
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class NinoValidationActionSpec extends BaseSpec with Results {

  override implicit lazy val app: Application = GuiceApplicationBuilder().build()

  private val mockAuthConnector = mock[AuthConnector]
  private val mockFandFConnector = mock[FandFConnector]
  private val sut = new NinoValidationAction(mockAuthConnector, mockFandFConnector, cc)
  private val fakeRequest = FakeRequest()

  type AuthRetrieval = Option[String]

  "NinoValidationAction" must {
    "allow the request" when {
      "authenticated nino matches request nino" in {
        val authResult: AuthRetrieval = Some("AA000000A")

        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.successful(authResult))

        when(mockFandFConnector.getTrustedHelper()(any[HeaderCarrier])).thenReturn(Future.successful(None))

        val result = sut
          .validateNino(Nino("AA000000A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue

        result.header.status mustBe OK
      }

      "trusted helper principal nino matches request nino" in {
        val authResult: AuthRetrieval = Some("AA000000A")

        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.successful(authResult))

        val trustedHelper = TrustedHelper("principal", "attorney", "returnUrl", principalNino = Some("AB123456A"))
        when(mockFandFConnector.getTrustedHelper()(any[HeaderCarrier])).thenReturn(
          Future.successful(Some(trustedHelper))
        )

        val result = sut
          .validateNino(Nino("AB123456A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue
        result.header.status mustBe OK
      }
    }

    "reject the request" when {
      "authenticated nino does NOT match request nino" in {
        val authResult: AuthRetrieval = Some("BB123456B")

        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.successful(authResult))

        when(mockFandFConnector.getTrustedHelper()(any[HeaderCarrier])).thenReturn(Future.successful(None))

        val result = sut
          .validateNino(Nino("AA000000A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue

        result.header.status mustBe INTERNAL_SERVER_ERROR
      }

      "trusted helper principal nino does not match request nino" in {
        val authResult: AuthRetrieval = Some("AB123456A")

        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.successful(authResult))

        val trustedHelper = TrustedHelper("principal", "attorney", "returnUrl", principalNino = Some("AB123456A"))

        when(mockFandFConnector.getTrustedHelper()(any[HeaderCarrier])).thenReturn(
          Future.successful(Some(trustedHelper))
        )

        val result = sut
          .validateNino(Nino("AA000000A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue

        result.header.status mustBe INTERNAL_SERVER_ERROR
      }

      "auth retrieval returns no nino" in {
        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.successful(None))

        val result = sut
          .validateNino(Nino("AA000000A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue

        result.header.status mustBe INTERNAL_SERVER_ERROR
      }

      "auth retrieval throws an exception" in {
        when(
          mockAuthConnector
            .authorise(any[Predicate], any[Retrieval[AuthRetrieval]])(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future.failed(new RuntimeException("Something went wrong")))

        val result = sut
          .validateNino(Nino("AA000000A"))
          .invokeBlock(fakeRequest, (_: Request[AnyContent]) => Future.successful(Ok))
          .futureValue

        result.header.status mustBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
