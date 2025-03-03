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

package uk.gov.hmrc.tai.connectors

import cats.data.EitherT
import org.scalatest.RecoverMethods
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import play.api.Logging
import play.api.http.Status.*
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import scala.concurrent.{ExecutionContext, Future}

class HttpClientResponseSpec
    extends PlaySpec with ScalaFutures with IntegrationPatience with RecoverMethods with LogCapturing with TestLogger {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  private lazy val httpClientResponseUsingMockLogger: HttpClientResponse = new HttpClientResponse with Logging {}

  private val dummyMessage = "Error response message"

  "HttpClientResponse.read" must {

    behave like clientResponseLogger(
      httpClientResponseUsingMockLogger.read,
      infoLevel = Set(NOT_FOUND, LOCKED),
      errorLevelWithoutThrowable = Set(TOO_MANY_REQUESTS, INTERNAL_SERVER_ERROR),
      errorLevelWithThrowable = Set(BAD_GATEWAY)
    )

    "return a successful response when the future returns a valid HttpResponse" in {
      val response = Future.successful(Right(HttpResponse(OK, "")))

      whenReady(httpClientResponseUsingMockLogger.read(response).value) { result =>
        result.map(_.status) mustBe Right(OK)
      }
    }

    "log an error and return an UpstreamErrorResponse when an upstream error with status >= 499 is received" in {
      withCaptureOfLoggingFrom(getLogger) { capturedLogs =>
        val response = Future.successful(Left(UpstreamErrorResponse(dummyMessage, INTERNAL_SERVER_ERROR)))

        whenReady(httpClientResponseUsingMockLogger.read(response).value) { result =>
          result mustBe Left(UpstreamErrorResponse(dummyMessage, INTERNAL_SERVER_ERROR))
          capturedLogs.exists(_.getMessage.contains(dummyMessage)) mustBe true
        }
        ()
      }
    }

    "log an error and return an UpstreamErrorResponse when TOO_MANY_REQUESTS is received" in {
      withCaptureOfLoggingFrom(getLogger) { capturedLogs =>
        val response = Future.successful(Left(UpstreamErrorResponse(dummyMessage, TOO_MANY_REQUESTS)))

        whenReady(httpClientResponseUsingMockLogger.read(response).value) { result =>
          result mustBe Left(UpstreamErrorResponse(dummyMessage, TOO_MANY_REQUESTS))
          capturedLogs.exists(_.getMessage.contains(dummyMessage)) mustBe true
        }
        ()
      }
    }

    "log an info message when NOT_FOUND or LOCKED response is received" in {
      withCaptureOfLoggingFrom(getLogger) { capturedLogs =>
        val response = Future.successful(Left(UpstreamErrorResponse(dummyMessage, NOT_FOUND)))

        whenReady(httpClientResponseUsingMockLogger.read(response).value) { result =>
          result mustBe Left(UpstreamErrorResponse(dummyMessage, NOT_FOUND))
          capturedLogs.exists(_.getMessage.contains(dummyMessage)) mustBe true
        }
        ()
      }
    }

    "log an error and return BAD_GATEWAY when an HttpException is thrown" in {
      withCaptureOfLoggingFrom(getLogger) { capturedLogs =>
        val response = Future.failed(new HttpException(dummyMessage, BAD_GATEWAY))

        whenReady(httpClientResponseUsingMockLogger.read(response).value) { result =>
          result mustBe Left(UpstreamErrorResponse(dummyMessage, BAD_GATEWAY, BAD_GATEWAY))
          capturedLogs.exists(_.getMessage.contains(dummyMessage)) mustBe true
        }
        ()
      }
    }

    "throw an exception when a non-HttpException error occurs" in {
      recoverToSucceededIf[RuntimeException] {
        httpClientResponseUsingMockLogger.read(Future.failed(new RuntimeException(dummyMessage))).value
      }
    }
  }

  private def clientResponseLogger(
    block: Future[Either[UpstreamErrorResponse, HttpResponse]] => EitherT[Future, UpstreamErrorResponse, HttpResponse],
    infoLevel: Set[Int],
    errorLevelWithThrowable: Set[Int],
    errorLevelWithoutThrowable: Set[Int]
  ): Unit = {

    def testLogLevel(httpResponseCode: Int, logLevel: String): Unit =
      s"log message: $logLevel level when response code is $httpResponseCode" in {
        withCaptureOfLoggingFrom(getLogger) { capturedLogs =>
          val response = Future.successful(Left(UpstreamErrorResponse(dummyMessage, httpResponseCode)))
          whenReady(block(response).value) { actual =>
            actual mustBe Left(UpstreamErrorResponse(dummyMessage, httpResponseCode))
            capturedLogs
              .filter(_.getLevel.toString == logLevel)
              .map(_.getMessage)
              .exists(_.contains(dummyMessage)) mustBe true
          }
          ()
        }
      }

    infoLevel.foreach(testLogLevel(_, "INFO"))
    errorLevelWithThrowable.foreach(testLogLevel(_, "ERROR"))
    errorLevelWithoutThrowable.foreach(testLogLevel(_, "ERROR"))
  }
}
