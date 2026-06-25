/*
 * Copyright 2026 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.tai.model.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject() (override val authConnector: AuthConnector, cc: ControllerComponents)(implicit
  ec: ExecutionContext
) extends AuthAction with AuthorisedFunctions {

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(ConfidenceLevel.L200).retrieve(Retrievals.nino) {
      case Some(nino) => Future.successful(Right(AuthenticatedRequest(request, Nino(nino))))
      case None       => Future.successful(Left(Status(UNAUTHORIZED)))
    } recover {
      case _: NoActiveSession             => Left(Status(UNAUTHORIZED))
      case _: InsufficientConfidenceLevel => Left(Status(UNAUTHORIZED))
    }
  }

  override protected def executionContext: ExecutionContext = cc.executionContext
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionRefiner[Request, AuthenticatedRequest]
