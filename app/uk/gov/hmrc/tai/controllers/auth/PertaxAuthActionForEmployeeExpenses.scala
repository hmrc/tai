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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.http.Status.UNAUTHORIZED
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.benefits.connectors.PertaxConnector
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.tai.model.PertaxResponse

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PertaxAuthActionForEmployeeExpenses @Inject() (
  pertaxConnector: PertaxConnector,
  cc: ControllerComponents
) extends ActionFilter[Request] with Results with I18nSupport with Logging {

  override def messagesApi: MessagesApi = cc.messagesApi

  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    pertaxConnector.pertaxPostAuthorise
      .fold(
        {
          case UpstreamErrorResponse(_, status, _, _) if status == UNAUTHORIZED =>
            Some(Unauthorized(""))
          case UpstreamErrorResponse(_, status, _, _) if status >= 499 =>
            Some(BadGateway("Dependant services failing"))
          case error =>
            Some(
              InternalServerError(
                s"Unexpected response from pertax with status ${error.statusCode} and response ${error.message}"
              )
            )
        },
        {
          case PertaxResponse("ACCESS_GRANTED", _) => None
          case PertaxResponse("NO_HMRC_PT_ENROLMENT", _) =>
            None // The calling services do not check for this requirement
          case PertaxResponse(code, message) =>
            Some(Unauthorized(s"Unauthorised with error code: `$code` and message:`$message`"))
        }
      )
  }

  override protected implicit val executionContext: ExecutionContext = cc.executionContext

}
