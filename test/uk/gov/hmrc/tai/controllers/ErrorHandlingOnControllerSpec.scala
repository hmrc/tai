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

package uk.gov.hmrc.tai.controllers

import cats.data.EitherT
import cats.instances.future.*
import com.google.inject.Inject
import org.mockito.Mockito.when
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.http.{BadRequestException, GatewayTimeoutException, HttpException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class ErrorHandlingOnControllerSpec extends BaseSpec {

  private class DummyService @Inject() (
  )(implicit ec: ExecutionContext) {
    def call(): EitherT[Future, UpstreamErrorResponse, Option[Int]] = EitherT(
      Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(None))
    )
  }

  private val mockDummyService = mock[DummyService]

  private class DummyController @Inject() (
    authentication: AuthJourney,
    cc: ControllerComponents,
    dummyService: DummyService
  )(implicit ec: ExecutionContext)
      extends BackendController(cc) with ControllerErrorHandler with Logging {
    def testMethod(): Action[AnyContent] = authentication.authForEmployeeExpenses.async { implicit request =>
      dummyService
        .call()
        .bimap(
          error => errorToResponse(error),
          {
            case Some(i) => Ok(Json.toJson(ApiResponse(i, Nil)))
            case None =>
              val message = s"dummy message"
              logger.warn(message)
              errorToResponse(UpstreamErrorResponse(message, NOT_FOUND))
          }
        )
        .merge recoverWith taxAccountErrorHandler()
    }
  }

  private val sut: DummyController = new DummyController(loggedInAuthenticationAuthJourney, cc, mockDummyService)

  private def failedResponse(ex: Throwable, expectedResponseCode: Int): Unit =
    s"return $expectedResponseCode response when service response is Future failed ${ex.toString}" in {
      when(mockDummyService.call())
        .thenReturn(
          EitherT(
            Future.failed(ex)
          )
        )
      val result = sut.testMethod()(FakeRequest())
      status(result) mustBe expectedResponseCode
    }

  override protected def beforeEach(): Unit = super.beforeEach()

  "test method" must {
    "return correct response when method maps service response to OK" in {
      when(mockDummyService.call())
        .thenReturn(EitherT(Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(Some(3)))))
      val result = sut.testMethod()(FakeRequest())
      status(result) mustBe OK
    }

    "return correct response when method maps service response to NOT_FOUND" in {
      when(mockDummyService.call())
        .thenReturn(EitherT(Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(None))))
      val result = sut.testMethod()(FakeRequest())
      status(result) mustBe NOT_FOUND
    }

    Set(INTERNAL_SERVER_ERROR, 508).foreach { responseCode =>
      s"return BAD_GATEWAY response when service response is Left UpstreamErrorResponse ($responseCode)" in {
        when(mockDummyService.call())
          .thenReturn(
            EitherT(
              Future.successful[Either[UpstreamErrorResponse, Option[Int]]](
                Left(UpstreamErrorResponse(s"$responseCode response", responseCode))
              )
            )
          )
        val result = sut.testMethod()(FakeRequest())
        status(result) mustBe BAD_GATEWAY
      }
    }

    Set(429, BAD_REQUEST, NOT_FOUND).foreach { responseCode =>
      s"return $responseCode response when service response is Left UpstreamErrorResponse ($responseCode)" in {
        when(mockDummyService.call())
          .thenReturn(
            EitherT(
              Future.successful[Either[UpstreamErrorResponse, Option[Int]]](
                Left(UpstreamErrorResponse("500 response", 429))
              )
            )
          )
        val result = sut.testMethod()(FakeRequest())
        status(result) mustBe 429
      }
    }

    behave like failedResponse(BadRequestException("dummy response"), BAD_REQUEST)
    behave like failedResponse(NotFoundException("dummy response"), NOT_FOUND)
    behave like failedResponse(GatewayTimeoutException("dummy response"), BAD_GATEWAY)
    behave like failedResponse(HttpException("502 Bad Gateway", 502), BAD_GATEWAY)
    behave like failedResponse(RuntimeException("Runtime exception"), INTERNAL_SERVER_ERROR)
  }

}
