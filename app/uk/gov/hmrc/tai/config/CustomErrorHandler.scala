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

import play.api.http.Status.*
import play.api.libs.json.*
import play.api.libs.json.Json.toJson
import play.api.mvc.Results.{BadGateway, BadRequest, InternalServerError, NotFound, Status, TooManyRequests}
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HeaderCarrier, HttpException, JsValidationException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.{ErrorResponse, JsonErrorHandler}
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomErrorHandler @Inject() (
  auditConnector: AuditConnector,
  httpAuditEvent: HttpAuditEvent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) with Logging {

  final def constructErrorMessage(input: String): String = {
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

  def handleControllerErrorStatuses(error: UpstreamErrorResponse): Result = {
    val constructedErrorMessage = constructErrorMessage(error.getMessage)
    error.statusCode match {
      case NOT_FOUND               => NotFound(constructedErrorMessage)
      case BAD_REQUEST             => BadRequest(constructedErrorMessage)
      case TOO_MANY_REQUESTS       => TooManyRequests(constructedErrorMessage)
      case status if status >= 499 => BadGateway(constructedErrorMessage)
      case _                       => InternalServerError(constructedErrorMessage)
    }
  }

  // This is the same as parent version except that above constructErrorMessage is called.
  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)
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
        NotFound(toJson(ErrorResponse(NOT_FOUND, "URI not found", requested = Some(request.path))))

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

        BadRequest(toJson(ErrorResponse(BAD_REQUEST, msg)))

      case _ =>
        auditConnector.sendEvent(
          httpAuditEvent.dataEvent(
            eventType = "ClientError",
            transactionName = s"A client error occurred, status: $statusCode",
            request = request,
            detail = Map.empty
          )
        )

        val msg =
          if (suppress4xxErrorMessages) "Other error"
          else message

        Status(statusCode)(toJson(ErrorResponse(statusCode, msg)))
    }
    Future.successful(result)
  }

  private def getEventTypeForAudit(ex: Throwable): String =
    ex match {
      case _: NotFoundException      => "ResourceNotFound"
      case _: AuthorisationException => "ClientError"
      case _: JsValidationException  => "ServerValidationError"
      case _                         => "ServerInternalError"
    }
  private def logException(exception: Throwable, responseCode: Int, request: RequestHeader): Unit = {
    val msg = s"${request.method} ${request.uri} failed with ${exception.getClass.getName}: ${exception.getMessage}"
    if (upstreamWarnStatuses.contains(responseCode)) {
      logger.warn(msg, exception)
    } else {
      logger.error(msg, exception)
    }
  }

  private def errMessage(e: Throwable): String = constructErrorMessage(e.getMessage)

  private def upstreamMessage(e: UpstreamErrorResponse): String =
    if (suppress5xxErrorMessages) { s"UpstreamErrorResponse: ${e.statusCode}" }
    else { errMessage(e) }

  private def throwableMessage(e: Throwable): String =
    if (suppress5xxErrorMessages) { "Other error" }
    else { errMessage(e) }

  private val isHttpExBadGateway: HttpException => Boolean = _.message.contains("502 Bad Gateway")

  private case class Analysis(newStatus: Int, newMessage: String, logStatus: Option[Int], doAudit: Boolean) {
    def toErrorResponse: ErrorResponse = ErrorResponse(newStatus, newMessage)
  }

  private def analyseError(e: Throwable, request: RequestHeader): Analysis =
    e match {
      case e: BadRequestException                    => Analysis(BAD_REQUEST, errMessage(e), None, false)
      case e: NotFoundException                      => Analysis(NOT_FOUND, errMessage(e), None, false)
      case e: GatewayTimeoutException                => Analysis(BAD_GATEWAY, errMessage(e), None, false)
      case e: BadGatewayException                    => Analysis(BAD_GATEWAY, errMessage(e), None, false)
      case e: HttpException if isHttpExBadGateway(e) => Analysis(BAD_GATEWAY, errMessage(e), None, false)
      case e: HttpException          => Analysis(e.responseCode, errMessage(e), Some(e.responseCode), true)
      case e: AuthorisationException => Analysis(UNAUTHORIZED, e.getMessage, Some(UNAUTHORIZED), true)
      case e: UpstreamErrorResponse  => Analysis(e.statusCode, upstreamMessage(e), Some(e.reportAs), true)
      case e: Throwable =>
        logger.error(s"! Internal server error, for (${request.method}) [${request.uri}] -> ", e)
        Analysis(INTERNAL_SERVER_ERROR, throwableMessage(e), None, true)
    }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    implicit val headerCarrier: HeaderCarrier = hc(request)
    val analysis: Analysis = analyseError(ex, request)

    analysis.logStatus.foreach(logException(ex, _, request))
    val auditResult = if (analysis.doAudit) {
      auditConnector
        .sendEvent(
          httpAuditEvent.dataEvent(
            eventType = getEventTypeForAudit(ex),
            transactionName = "Unexpected error",
            request = request,
            detail = Map("transactionFailureReason" -> ex.getMessage)
          )
        )
        .map(_ => (): Unit)
    } else {
      Future.successful((): Unit)
    }
    auditResult.map(_ => new Status(analysis.newStatus)(Json.toJson(analysis.toErrorResponse)))
  }

}
