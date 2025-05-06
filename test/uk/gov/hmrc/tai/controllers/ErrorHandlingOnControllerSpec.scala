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
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HttpException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.config.CustomErrorHandler
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.util.BaseSpec

import scala.concurrent.Future

class ErrorHandlingOnControllerSpec extends BaseSpec {

  private class DummyService @Inject() (
  ) {
    def call(): EitherT[Future, UpstreamErrorResponse, Option[Int]] = EitherT(
      Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(None))
    )
  }

  private val mockDummyService = mock[DummyService]

  private class DummyController @Inject() (
    authentication: AuthJourney,
    cc: ControllerComponents,
    dummyService: DummyService,
    customErrorHandler: CustomErrorHandler
  ) extends BackendController(cc) with Logging {
    def testMethod(): Action[AnyContent] = authentication.authForEmployeeExpenses.async { implicit request =>
      dummyService
        .call()
        .bimap(
          error => customErrorHandler.handleControllerErrorStatuses(error),
          {
            case Some(i) => Ok(Json.toJson(ApiResponse(i, Nil)))
            case None    => NotFound("dummy message")
          }
        )
        .merge //
    }
  }

  private val sut: DummyController = new DummyController(
    loggedInAuthenticationAuthJourney,
    cc,
    mockDummyService,
    app.injector.instanceOf[CustomErrorHandler]
  )

  private def runTest: Future[Result] =
    sut.testMethod()(FakeRequest())

  private def withInfo(s: String)(block: String => Unit): Unit = block(s)

  private def failedResponseHandledByErrorHandler(ex: Throwable, info: String, expStatus: Int): Unit =
    s"throw exception when response Future failed ${ex.getClass} where $info" in {
      when(mockDummyService.call())
        .thenReturn(
          EitherT(
            Future.failed(ex)
          )
        )
      checkControllerResponse(ex, runTest, expStatus)
    }

  override protected def beforeEach(): Unit = super.beforeEach()

  "A dummy controller method with standard handling for exception responses in conjuction with the standard error handler" must {
    "return correct response when method maps service response to OK" in {
      when(mockDummyService.call())
        .thenReturn(EitherT(Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(Some(3)))))
      status(runTest) mustBe OK
    }

    "return correct response when method maps service response to NOT_FOUND" in {
      when(mockDummyService.call())
        .thenReturn(EitherT(Future.successful[Either[UpstreamErrorResponse, Option[Int]]](Right(None))))
      status(runTest) mustBe NOT_FOUND
    }

    Set(INTERNAL_SERVER_ERROR, INSUFFICIENT_STORAGE).foreach { responseCode =>
      s"return BAD_GATEWAY response when service response is Left UpstreamErrorResponse ($responseCode) - i.e. > 499" in {
        when(mockDummyService.call())
          .thenReturn(
            EitherT(
              Future.successful[Either[UpstreamErrorResponse, Option[Int]]](
                Left(UpstreamErrorResponse(s"$responseCode response", responseCode))
              )
            )
          )
        status(runTest) mustBe BAD_GATEWAY
      }
    }

    Set(TOO_MANY_REQUESTS, BAD_REQUEST, NOT_FOUND).foreach { responseCode =>
      s"return $responseCode response when service response is Left UpstreamErrorResponse ($responseCode)" in {
        when(mockDummyService.call())
          .thenReturn(
            EitherT(
              Future.successful[Either[UpstreamErrorResponse, Option[Int]]](
                Left(UpstreamErrorResponse("500 response", responseCode))
              )
            )
          )
        status(runTest) mustBe responseCode
      }
    }
    Set(METHOD_NOT_ALLOWED, PAYMENT_REQUIRED).foreach { responseCode =>
      s"return $responseCode response when service response is Left UpstreamErrorResponse ($responseCode) - Internal server error" in {
        when(mockDummyService.call())
          .thenReturn(
            EitherT(
              Future.successful[Either[UpstreamErrorResponse, Option[Int]]](
                Left(UpstreamErrorResponse("500 response", responseCode))
              )
            )
          )
        status(runTest) mustBe INTERNAL_SERVER_ERROR
      }
    }

    withInfo("previously handled in controller") { info =>
      behave like failedResponseHandledByErrorHandler(BadRequestException("dummy response"), info, BAD_REQUEST)
      behave like failedResponseHandledByErrorHandler(NotFoundException("dummy response"), info, NOT_FOUND)
      behave like failedResponseHandledByErrorHandler(GatewayTimeoutException("dummy response"), info, BAD_GATEWAY)
      behave like failedResponseHandledByErrorHandler(BadGatewayException("dummy response"), info, BAD_GATEWAY)
      behave like failedResponseHandledByErrorHandler(HttpException("502 Bad Gateway", BAD_GATEWAY), info, BAD_GATEWAY)
    }

    withInfo("previously handled in error handler") { info =>
      behave like failedResponseHandledByErrorHandler(
        HttpException("Http exception", 402),
        info,
        402
      )
      behave like failedResponseHandledByErrorHandler(
        RuntimeException("Runtime exception"),
        info,
        INTERNAL_SERVER_ERROR
      )
      behave like failedResponseHandledByErrorHandler(
        UpstreamErrorResponse("Upstream exception", INSUFFICIENT_STORAGE),
        info,
        INSUFFICIENT_STORAGE
      )
      behave like failedResponseHandledByErrorHandler(
        InsufficientConfidenceLevel("Insufficient confidence exception"),
        info,
        UNAUTHORIZED
      )
    }

  }

}
