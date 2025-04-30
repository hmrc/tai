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
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Application
import play.api.cache.AsyncCacheApi
import play.api.http.Status.*
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, status}
import uk.gov.hmrc.auth.core.InsufficientConfidenceLevel
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HeaderNames, HttpException, InternalServerException, JsValidationException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.tai.util.BaseSpec

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class CustomErrorHandlerSpec extends BaseSpec with ScalaCheckDrivenPropertyChecks {
  def pathGen: Gen[String] = Gen.nonEmptyListOf(Gen.alphaNumStr).map(_.mkString("/"))

  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditConnector)
  }

  val configValues: Map[String, AnyVal] =
    Map(
      "metrics.enabled"  -> false,
      "auditing.enabled" -> false
    )

  override implicit lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(configValues)
      .overrides(
        bind[AuditConnector].toInstance(mockAuditConnector),
        bind[AsyncCacheApi].toInstance(fakeAsyncCacheApi)
      )
      .build()

  "onClientError" must {
    "return an ApiResponse" in {
      forAll(Gen.choose(BAD_REQUEST + 1, INTERNAL_SERVER_ERROR - 1), Gen.alphaStr, pathGen) { (status, message, path) =>
        whenever(status != NOT_FOUND) {
          val uuid = UUID.randomUUID().toString
          val customErrorHandler = inject[CustomErrorHandler]
          val result = customErrorHandler.onClientError(
            FakeRequest(GET, path).withHeaders(HeaderNames.xRequestId -> uuid),
            status,
            message
          )
          val json = Json.parse(contentAsString(result))
          (json \ "code").get mustBe JsString("CLIENT_ERROR")
          (json \ "message").get.as[String] mustBe s"""Other error [auditSource=tai, X-Request-ID=$uuid]"""
          (json \ "errorView").isDefined mustBe false
          (json \ "redirect").isDefined mustBe false
        }
      }
    }

    "return a Not found ApiResponse" in {
      val customErrorHandler = inject[CustomErrorHandler]
      val url = "/pertax/notFound"
      val message = s"The resource `$url` has not been found"
      val result = customErrorHandler.onClientError(FakeRequest(GET, url), NOT_FOUND, message)
      val json = Json.parse(contentAsString(result))
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      (json \ "code").get mustBe JsString("NOT_FOUND")
      (json \ "message").get mustBe JsString(message)
      (json \ "errorView").isDefined mustBe false
      (json \ "redirect").isDefined mustBe false
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
      when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))

      (json \ "code").get mustBe JsString("BAD_REQUEST")
      (json \ "message").get
        .as[String] mustBe s"""bad request, cause: REDACTED [auditSource=tai, X-Request-ID=$uuid]"""
      (json \ "errorView").isDefined mustBe false
      (json \ "redirect").isDefined mustBe false
      verify(mockAuditConnector, times(1)).sendEvent(any())(any(), any())
    }
  }

  "onServerError" must {
    "return an ApiResponse" when {
      List(
        new NotFoundException("error"),
        new InsufficientConfidenceLevel,
        new JsValidationException("method", "url", classOf[Int], "errors"),
        new RuntimeException("error")
      ).foreach { ex =>
        s"Exception ${ex.getClass.getName} is thrown" in {
          val arg = ArgumentCaptor.forClass(classOf[DataEvent])
          val uuid = UUID.randomUUID().toString
          val customErrorHandler = inject[CustomErrorHandler]
          val result = customErrorHandler.onServerError(FakeRequest().withHeaders(HeaderNames.xRequestId -> uuid), ex)
          val json = Json.parse(contentAsString(result))
          (json \ "code").get mustBe JsString("INTERNAL_ERROR")
          (json \ "message").get.toString must include(
            s"An error has occurred. This has been audited with auditSource=tai, X-Request-ID=$uuid"
          )
          (json \ "errorView").isDefined mustBe false
          (json \ "redirect").isDefined mustBe false

          verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
          val dataEvent: DataEvent = arg.getValue
          dataEvent.tags.get(HeaderNames.xRequestId) mustBe defined

          val refInAudit = dataEvent.tags(HeaderNames.xRequestId)
          val uuidPattern = """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}""".r
          val refInResponse = uuidPattern.findFirstIn((json \ "message").get.toString).get
          refInAudit mustBe refInResponse
        }
      }
    }
  }

  private val exceptionMessage = s"""The nino provided `$nino` is invalid"""

  "taxAccountErrorHandler" must {
    Set(
      new BadRequestException(exceptionMessage)     -> BAD_REQUEST,
      new NotFoundException(exceptionMessage)       -> NOT_FOUND,
      new GatewayTimeoutException(exceptionMessage) -> BAD_GATEWAY,
      new BadGatewayException(exceptionMessage)     -> BAD_GATEWAY
    ).foreach { case (exception, response) =>
      s"return $response with cause redacted" when {
        s"there is hod ${exception.toString} exception" in {
          val customErrorHandler = inject[CustomErrorHandler]
          val pf = customErrorHandler.handleControllerExceptions()
          val result = pf(exception)
          status(result) mustBe response
          contentAsString(result) mustBe "The nino provided is invalid"
        }
      }
    }

    "return 500 with cause redacted" when {
      "there is hod error containing 502 Bad Gateway exception" in {
        val customErrorHandler = inject[CustomErrorHandler]
        val pf = customErrorHandler.handleControllerExceptions()
        val result = pf(new HttpException("error containing 502 Bad Gateway", 500))
        status(result) mustBe BAD_GATEWAY
        contentAsString(result) mustBe "bad request, cause: REDACTED"
      }
    }

    Set(
      new HttpException(
        "error NOT containing five zero two Bad Gateway",
        500
      )                                             -> "error NOT containing five zero two Bad Gateway",
      new InternalServerException(exceptionMessage) -> exceptionMessage
    ).foreach { case (exception, expectedMessage) =>
      s"return exception" when {
        s"there is hod ${exception.toString} exception" in {
          val customErrorHandler = inject[CustomErrorHandler]
          val pf = customErrorHandler.handleControllerExceptions()
          val result = the[HttpException] thrownBy
            Await.result(pf(exception), Duration.Inf)
          result.getMessage mustBe expectedMessage
        }
      }
    }

  }

  "errorToResponse" must {
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
