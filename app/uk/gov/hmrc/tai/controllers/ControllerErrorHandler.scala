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

import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, TOO_MANY_REQUESTS}
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.http.{BadRequestException, NotFoundException, UpstreamErrorResponse}

import scala.concurrent.Future

trait ControllerErrorHandler {

  def taxAccountErrorHandler(): PartialFunction[Throwable, Future[Result]] = {
    case ex: BadRequestException => Future.successful(BadRequest(ex.message))
    case ex: NotFoundException   => Future.successful(NotFound(ex.message))
    case ex                      => throw ex
  }

  def errorToResponse(error: UpstreamErrorResponse): Result =
    error.statusCode match {
      case NOT_FOUND               => NotFound(error.getMessage())
      case BAD_REQUEST             => BadRequest(error.getMessage())
      case TOO_MANY_REQUESTS       => TooManyRequests(error.getMessage())
      case status if status >= 499 => BadGateway(error.getMessage())
      case _                       => InternalServerError(error.getMessage())
    }
}
