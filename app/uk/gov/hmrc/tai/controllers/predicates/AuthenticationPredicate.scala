/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.auth.core.{AuthorisationException, AuthorisedFunctions, ConfidenceLevel}
import uk.gov.hmrc.domain.Nino
import play.api.mvc.Results.Unauthorized
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class AuthenticatedRequest[A](request: Request[A], nino: Nino) extends WrappedRequest[A](request)

@Singleton
class AuthenticationPredicate @Inject()(val authorisedFunctions: AuthorisedFunctions)
    extends ActionBuilder[AuthenticatedRequest] with BaseController {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    implicit val rh = request.asInstanceOf[RequestHeader]
    implicit val hc = HeaderCarrierConverter.fromHeadersAndSessionAndRequest(rh.headers, request = Some(rh))

    authorisedFunctions
      .authorised(ConfidenceLevel.L100)
      .retrieve(Retrievals.nino and Retrievals.trustedHelper) {
        case _ ~ Some(trustedHelper) => block(AuthenticatedRequest(request, trustedHelper.principalNino))
        case Some(nino) ~ _          => block(AuthenticatedRequest(request, Nino(nino)))
        case _                       => throw new RuntimeException("Can't find valid credentials for user")
      }
      .recover {
        case e: AuthorisationException =>
          Logger.warn("Failed to authorise: " + e.reason)
          Unauthorized(e.getMessage)
      }
  }
}
