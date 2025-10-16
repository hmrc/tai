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

import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.{BAD_GATEWAY, BAD_REQUEST, INTERNAL_SERVER_ERROR, UNAUTHORIZED}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.tai.connectors.PertaxConnector
import uk.gov.hmrc.tai.model.PertaxResponse
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class PertaxAuthActionSpec extends BaseSpec {

  override implicit lazy val app: Application = GuiceApplicationBuilder().build()

  private val mockPertaxConnector = mock[PertaxConnector]

  val pertaxAuthAction =
    new PertaxAuthAction(mockPertaxConnector, cc)

  private val testRequest = FakeRequest("GET", "/check-income-tax/what-do-you-want-to-do")

  "Pertax auth action" when {
    "the pertax API returns an ACCESS_GRANTED response" must {
      "load the request" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("ACCESS_GRANTED", "")))
            )
          )

        val result = pertaxAuthAction.filter(testRequest).futureValue
        result mustBe None
      }
    }

    "the pertax API response returns an error response" must {
      "returns unauthorised" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Right(PertaxResponse("ERROR_RESPONSE", "")))
            )
          )

        val result = pertaxAuthAction.filter(testRequest).futureValue

        result must not be empty
        result.get.header.status mustBe UNAUTHORIZED
      }
    }

    "pertax returns a server error" must {
      "returns bad gateway" in {
        when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PertaxResponse](
              Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
            )
          )

        val result = pertaxAuthAction.filter(testRequest).futureValue

        result must not be empty
        result.get.header.status mustBe BAD_GATEWAY
      }
    }
  }

  "pertax returns a client error" must {
    "returns internal server error" in {
      when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, PertaxResponse](
            Future.successful(Left(UpstreamErrorResponse("", BAD_REQUEST)))
          )
        )

      val result = pertaxAuthAction.filter(testRequest).futureValue

      result must not be empty
      result.get.header.status mustBe INTERNAL_SERVER_ERROR
    }
  }

  "pertax returns an unauthorised response" must {
    "returns unauthorised" in {
      when(mockPertaxConnector.pertaxPostAuthorise(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, PertaxResponse](
            Future.successful(Left(UpstreamErrorResponse("", UNAUTHORIZED)))
          )
        )

      val result = pertaxAuthAction.filter(testRequest).futureValue

      result must not be empty
      result.get.header.status mustBe UNAUTHORIZED
    }
  }
}
