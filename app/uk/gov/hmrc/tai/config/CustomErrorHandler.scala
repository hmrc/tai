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

package uk.gov.hmrc.tai.config

import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND}
import play.api.mvc.Results.Status
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.{HeaderCarrier, JsValidationException, NotFoundException}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.tai.model.ApiResponseFromPERTAX

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomErrorHandler @Inject() (
  appConfig: AppConfig,
  auditConnector: AuditConnector,
  httpAuditEvent: HttpAuditEvent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) with Logging {

  private def constructErrorMessage(input: String): String = {
    val unrecognisedTokenJsonError = "^Invalid Json: Unrecognized token '(.*)':.*".r
    val invalidJson = "^(?s)Invalid Json:.*".r
    val jsonValidationError = "^Json validation error.*".r
    val booleanParsingError = "^Cannot parse parameter .* as Boolean: should be true, false, 0 or 1$".r
    val missingParameterError = "^Missing parameter:.*".r
    val characterParseError =
      "^Cannot parse parameter .* with value '(.*)' as Char: .* must be exactly one digit in length.$".r
    val parameterParseError = "^Cannot parse parameter .* as .*: For input string: \"(.*)\"$".r
    val featureFlagParseError = "^The feature flag `(.*)` does not exist$".r
    val ninoInvalidParserError = "^The nino provided `(.*)` is invalid$".r
    input match {
      case unrecognisedTokenJsonError(toBeRedacted) => input.replace(toBeRedacted, "REDACTED")
      case invalidJson() | jsonValidationError() | booleanParsingError() | missingParameterError() => input
      case characterParseError(toBeRedacted) => input.replace(toBeRedacted, "REDACTED")
      case parameterParseError(toBeRedacted) => input.replace(toBeRedacted, "REDACTED")
      case featureFlagParseError(flagName)   => s"The feature flag `$flagName` does not exist"
      case ninoInvalidParserError(_)         => s"The nino provided is invalid"
      case _                                 => "bad request, cause: REDACTED"
    }
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)
    val requestId = headerCarrier.requestId.map(_.value).getOrElse("None")

    val result = statusCode match {
      case NOT_FOUND =>
        auditConnector.sendEvent(
          httpAuditEvent.dataEvent(
            eventType = "ResourceNotFound",
            transactionName = "Resource Endpoint Not Found",
            request = request,
            detail = Map.empty
          )
        )
        ApiResponseFromPERTAX(
          "NOT_FOUND",
          s"The resource `${request.uri}` has not been found",
          reportAs = NOT_FOUND
        ).toResult

      case BAD_REQUEST =>
        auditConnector.sendEvent(
          httpAuditEvent.dataEvent(
            eventType = "ServerValidationError",
            transactionName = "Request bad format exception",
            request = request,
            detail = Map.empty
          )
        )

        val msg =
          if (suppress4xxErrorMessages) "Bad request"
          else constructErrorMessage(message)

        ApiResponseFromPERTAX(
          "BAD_REQUEST",
          msg + s" [auditSource=${appConfig.appName}, X-Request-ID=$requestId]",
          reportAs = BAD_REQUEST
        ).toResult

      case _ =>
        auditConnector.sendEvent(
          httpAuditEvent.dataEvent(
            eventType = "ClientError",
            transactionName = s"A client error occurred, status: $statusCode",
            request = request,
            detail = Map.empty
          )
        )

        Status(statusCode)(
          ApiResponseFromPERTAX(
            "CLIENT_ERROR",
            s"Other error [auditSource=${appConfig.appName}, X-Request-ID=$requestId]",
            reportAs = statusCode
          )
        )
    }
    Future.successful(result)
  }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)
    val requestId = headerCarrier.requestId.map(_.value).getOrElse("None")
    val eventType = ex match {
      case _: NotFoundException      => "ResourceNotFound"
      case _: AuthorisationException => "ClientError"
      case _: JsValidationException  => "ServerValidationError"
      case _                         => "ServerInternalError"
    }

    logger.error(
      s"! Internal server error, for (${request.method}) [auditSource=${appConfig.appName}, X-Request-ID=$requestId -> ",
      ex
    )

    auditConnector.sendEvent(
      httpAuditEvent.dataEvent(
        eventType = eventType,
        transactionName = "Unexpected error",
        request = request,
        detail = Map("transactionFailureReason" -> ex.getMessage)
      )
    )

    Future.successful(
      ApiResponseFromPERTAX(
        "INTERNAL_ERROR",
        s"An error has occurred. This has been audited with auditSource=${appConfig.appName}, X-Request-ID=$requestId",
        reportAs = INTERNAL_SERVER_ERROR
      ).toResult
    )
  }
}
