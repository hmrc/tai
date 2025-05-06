/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.tai.config

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, reset, times, verify, when}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout}
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HeaderNames, HttpException, InternalServerException, JsValidationException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.tai.util.BaseSpec

import java.util.UUID
import scala.concurrent.Future

class CustomErrorHandlerSpec extends BaseSpec with ScalaCheckDrivenPropertyChecks {
  private val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditConnector)
    when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
    ()
  }

  private val configValues: Map[String, AnyVal] =
    Map(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false
    )
  private val exceptionMessage = s"""The nino provided `$nino` is invalid"""
  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(configValues)
      .overrides(
        bind[AuditConnector].toInstance(mockAuditConnector),
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )
      .build()

  "onClientError" must {
    "return a Not found ApiResponse" in {
      val customErrorHandler = inject[CustomErrorHandler]
      val url = "/tai/notFound"
      val message = "URI not found"
      val result = customErrorHandler.onClientError(FakeRequest(GET, url), NOT_FOUND, message)
      val json = Json.parse(contentAsString(result))

      (json \ "statusCode").get mustBe JsNumber(404)
      (json \ "message").get mustBe JsString(message)
      verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
    }

    "return a bad request ApiResponse" in {
      val uuid = UUID.randomUUID().toString
      val customErrorHandler = inject[CustomErrorHandler]
      val result = customErrorHandler.onClientError(
        FakeRequest().withHeaders(HeaderNames.xRequestId -> uuid),
        BAD_REQUEST,
        "An error message"
      )
      val json = Json.parse(contentAsString(result))

      (json \ "statusCode").get mustBe JsNumber(400)
      (json \ "message").get mustBe JsString("bad request, cause: REDACTED")
      verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
    }

    "return another ApiResponse" in {
      val customErrorHandler = inject[CustomErrorHandler]
      val url = "/tai/notFound"
      val message = "Unauthorised"
      val result = customErrorHandler.onClientError(FakeRequest(GET, url), UNAUTHORIZED, message)
      val json = Json.parse(contentAsString(result))

      (json \ "statusCode").get mustBe JsNumber(401)
      (json \ "message").get mustBe JsString(message)
      verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
    }
  }

  "onServerError" must {
    "return an api response and a call to audit connector" when {
      Set(
        new InsufficientConfidenceLevel -> (UNAUTHORIZED, "Insufficient ConfidenceLevel"),
        new JsValidationException("method", "url", classOf[Int], "errors")
          -> (INTERNAL_SERVER_ERROR, "bad request, cause: REDACTED"),
        new RuntimeException("error") -> (INTERNAL_SERVER_ERROR, "bad request, cause: REDACTED"),
        new HttpException("error NOT containing five zero two Bad Gateway", 500) ->
          (INTERNAL_SERVER_ERROR, "bad request, cause: REDACTED"),
        new InternalServerException(exceptionMessage) -> (INTERNAL_SERVER_ERROR, "The nino provided is invalid")
      ).foreach { case (ex, Tuple2(newStatus, newMessage)) =>
        s"Exception ${ex.getClass.getName} is thrown and return status code of $newStatus and correct message" in {
          val arg = ArgumentCaptor.forClass(classOf[DataEvent])
          val uuid = UUID.randomUUID().toString
          val customErrorHandler = inject[CustomErrorHandler]
          val result = customErrorHandler.onServerError(FakeRequest().withHeaders(HeaderNames.xRequestId -> uuid), ex)
          val json = Json.parse(contentAsString(result))
          (json \ "statusCode").get mustBe JsNumber(newStatus)
          (json \ "message").get.toString must include(newMessage)

          verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
          val dataEvent: DataEvent = arg.getValue
          dataEvent.tags.get(HeaderNames.xRequestId) mustBe defined
        }
      }
    }

    "return an api response and NO call to audit connector" when {
      Set(
        new BadRequestException(exceptionMessage)                  -> (BAD_REQUEST, "The nino provided is invalid"),
        new NotFoundException(exceptionMessage)                    -> (NOT_FOUND, "The nino provided is invalid"),
        new GatewayTimeoutException(exceptionMessage)              -> (BAD_GATEWAY, "The nino provided is invalid"),
        new BadGatewayException(exceptionMessage)                  -> (BAD_GATEWAY, "The nino provided is invalid"),
        new HttpException("error containing 502 Bad Gateway", 500) -> (BAD_GATEWAY, "bad request, cause: REDACTED")
      ).foreach { case (ex, Tuple2(newStatus, newMessage)) =>
        s"Exception ${ex.getClass.getName} is thrown and return status code of $newStatus and correct message" in {
          val arg = ArgumentCaptor.forClass(classOf[DataEvent])
          val uuid = UUID.randomUUID().toString
          val customErrorHandler = inject[CustomErrorHandler]
          val result = customErrorHandler.onServerError(FakeRequest().withHeaders(HeaderNames.xRequestId -> uuid), ex)
          val json = Json.parse(contentAsString(result))
          (json \ "statusCode").get mustBe JsNumber(newStatus)
          (json \ "message").get.toString must include(newMessage)

          verify(mockAuditConnector, never()).sendEvent(arg.capture())(any(), any())
        }
      }
    }
  }

  "handleControllerErrorStatuses" must {
    Set(
      NOT_FOUND,
      BAD_REQUEST,
      TOO_MANY_REQUESTS
    ).foreach { status =>
      s"return $status and redacted message for $status response" in {
        val customErrorHandler = inject[CustomErrorHandler]
        val result = customErrorHandler.handleControllerErrorStatuses(UpstreamErrorResponse(exceptionMessage, status))
        result.header.status mustBe status
        contentAsString(Future(result)) mustBe "The nino provided is invalid"
      }
    }

    Set(
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE,
      GATEWAY_TIMEOUT
    ).foreach { status =>
      s"return BAD GATEWAY status and redacted message for $status response" in {
        val customErrorHandler = inject[CustomErrorHandler]
        val result = customErrorHandler.handleControllerErrorStatuses(UpstreamErrorResponse(exceptionMessage, status))
        result.header.status mustBe BAD_GATEWAY
        contentAsString(Future(result)) mustBe "The nino provided is invalid"
      }
    }

    Set(
      IM_A_TEAPOT,
      UNPROCESSABLE_ENTITY
    ).foreach { status =>
      s"return internal server error status and redacted message for $status response" in {
        val customErrorHandler = inject[CustomErrorHandler]
        val result = customErrorHandler.handleControllerErrorStatuses(UpstreamErrorResponse(exceptionMessage, status))
        result.header.status mustBe INTERNAL_SERVER_ERROR
        contentAsString(Future(result)) mustBe "The nino provided is invalid"
      }
    }
  }

}
