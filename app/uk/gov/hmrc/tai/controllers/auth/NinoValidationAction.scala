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

import com.google.inject.{Inject, Singleton}
import play.api.Logging
import play.api.mvc.*
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.tai.connectors.FandFConnector
import uk.gov.hmrc.tai.model.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class NinoValidationAction @Inject() (
  fandFConnector: FandFConnector,
  cc: ControllerComponents
) extends Results with Logging {

  def validateNino(urlNino: Nino): ActionFilter[AuthenticatedRequest] = new ActionFilter[AuthenticatedRequest] {

    override protected implicit def executionContext: ExecutionContext = cc.executionContext

    override def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = {
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      val authNino = request.nino.nino

      fandFConnector
        .getTrustedHelper()
        .map { maybeTrustedHelper =>
          val trustedHelperNino = maybeTrustedHelper.flatMap(_.principalNino)
          if (ninoMatches(urlNino.nino, authNino, trustedHelperNino)) {
            None
          } else {
            logger.error(
              s"[NinoValidationAction.validateNino] NINO validation failed. Request NINO did not match authenticated or delegated NINO"
            )
            Some(InternalServerError("NINO validation failed"))
          }
        }
        .recover { case ex =>
          logger.error(s"[NinoValidationAction.validateNino] Exception during NINO validation.", ex)
          Some(InternalServerError("NINO validation failed"))
        }
    }
  }

  private def ninoMatches(requestNino: String, authNino: String, trustedHelperNino: Option[String]): Boolean =
    trustedHelperNino match {
      case Some(principalNino) => principalNino == requestNino
      case None                => authNino == requestNino
    }
}
