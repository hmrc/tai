/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.tai.controllers.predicates

import play.api.Logging
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthorisationException, AuthorisedFunctions, ConfidenceLevel}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class AuthenticatedRequest[A](request: Request[A], nino: Nino) extends WrappedRequest[A](request) with Logging

@Singleton
class AuthenticationPredicate @Inject()(val authorisedFunctions: AuthorisedFunctions, cc: ControllerComponents)(
  implicit ec: ExecutionContext
) extends BackendController(cc) with Logging {

  def async(action: AuthenticatedRequest[AnyContent] => Future[Result]): Action[AnyContent] =
    Action.async { implicit request: Request[AnyContent] =>
      authorisedFunctions
        .authorised(ConfidenceLevel.L200)
        .retrieve(Retrievals.nino and Retrievals.trustedHelper) {
          case _ ~ Some(trustedHelper) => action(AuthenticatedRequest(request, Nino(trustedHelper.principalNino)))
          case Some(nino) ~ _          => action(AuthenticatedRequest(request, Nino(nino)))
          case _                       => throw new RuntimeException("Can't find valid credentials for user")
        }
        .recover {
          case e: AuthorisationException =>
            logger.warn("Failed to authorise: " + e.reason)
            Unauthorized(e.getMessage)
        }
    }

  def async[A](bodyParser: BodyParser[A])(action: AuthenticatedRequest[A] => Future[Result]): Action[A] =
    Action.async(bodyParser) { implicit request =>
      authorisedFunctions
        .authorised(ConfidenceLevel.L200)
        .retrieve(Retrievals.nino and Retrievals.trustedHelper) {
          case _ ~ Some(trustedHelper) => action(AuthenticatedRequest(request, Nino(trustedHelper.principalNino)))
          case Some(nino) ~ _          => action(AuthenticatedRequest(request, Nino(nino)))
          case _                       => throw new RuntimeException("Can't find valid credentials for user")
        }
        .recover {
          case e: AuthorisationException =>
            logger.warn("Failed to authorise: " + e.reason)
            Unauthorized(e.getMessage)
        }
    }
}
