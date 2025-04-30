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

import play.api.http.Status.{BAD_REQUEST, INTERNAL_SERVER_ERROR, NOT_FOUND, TOO_MANY_REQUESTS}
import play.api.http.{ContentTypeOf, ContentTypes, Writeable}
import play.api.libs.json.*
import play.api.mvc.Results.{BadGateway, BadRequest, InternalServerError, NotFound, Status, TooManyRequests}
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HeaderCarrier, HttpException, JsValidationException, NotFoundException, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.tai.model.ErrorView

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

  import CustomErrorHandler.*

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

  def taxAccountErrorHandler(): PartialFunction[Throwable, Future[Result]] = {
    case ex: BadRequestException                                     => Future.successful(BadRequest(ex.message))
    case ex: NotFoundException                                       => Future.successful(NotFound(ex.message))
    case ex: GatewayTimeoutException                                 => Future.successful(BadGateway(ex.getMessage))
    case ex: BadGatewayException                                     => Future.successful(BadGateway(ex.getMessage))
    case ex: HttpException if ex.message.contains("502 Bad Gateway") => Future.successful(BadGateway(ex.getMessage))
    case ex                                                          => throw ex
  }

  def errorToResponse(error: UpstreamErrorResponse): Result =
    error.statusCode match {
      case NOT_FOUND               => NotFound(error.getMessage)
      case BAD_REQUEST             => BadRequest(error.getMessage)
      case TOO_MANY_REQUESTS       => TooManyRequests(error.getMessage)
      case status if status >= 499 => BadGateway(error.getMessage)
      case _                       => InternalServerError(error.getMessage)
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
        ApiResponse(
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

        ApiResponse(
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
          ApiResponse(
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
      ApiResponse(
        "INTERNAL_ERROR",
        s"An error has occurred. This has been audited with auditSource=${appConfig.appName}, X-Request-ID=$requestId",
        reportAs = INTERNAL_SERVER_ERROR
      ).toResult
    )
  }
}

object CustomErrorHandler {
  private case class ApiResponse(
    code: String,
    message: String,
    errorView: Option[ErrorView] = None,
    redirect: Option[String] = None,
    reportAs: Int
  ) {
    def toResult: Result = Status(reportAs)(this)
  }

  private object ApiResponse {
    implicit def writable[T](implicit writes: Writes[T]): Writeable[T] = {
      implicit val contentType: ContentTypeOf[T] = ContentTypeOf[T](Some(ContentTypes.JSON))
      Writeable(Writeable.writeableOf_JsValue.transform.compose(writes.writes))
    }

    private def removeNulls(jsObject: JsObject): JsValue =
      JsObject(jsObject.fields.collect {
        case (s, j: JsObject) =>
          (s, removeNulls(j))
        case other if other._2 != JsNull =>
          other
      })

    implicit val writes: Writes[ApiResponse] = new Writes[ApiResponse] {
      override def writes(o: ApiResponse): JsValue = removeNulls(
        Json.obj(
          "code"      -> JsString(o.code),
          "message"   -> JsString(o.message),
          "errorView" -> o.errorView.map(errorView => Json.toJson(errorView)),
          "redirect"  -> o.redirect.map(JsString.apply)
        )
      )
    }
  }

}
