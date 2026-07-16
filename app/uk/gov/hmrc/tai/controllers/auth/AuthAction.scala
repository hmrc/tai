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
import play.api.Logging
import play.api.http.Status.UNAUTHORIZED
import play.api.mvc.*
import play.api.mvc.Results.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.tai.config.MongoConfig
import uk.gov.hmrc.tai.model.{AuthenticatedRequest, CachedAuthRetrievals}
import uk.gov.hmrc.tai.repositories.cache.AuthCacheRepository

import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject() (
  override val authConnector: AuthConnector,
  appConfig: MongoConfig,
  authCacheRepository: AuthCacheRepository,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends AuthAction with AuthorisedFunctions with Logging {

  private val AuthRetrievalsKey = DataKey[CachedAuthRetrievals]("auth-retrievals")

  private def callAuth[A](request: Request[A], cacheResult: Boolean)(implicit
    hc: HeaderCarrier
  ): Future[Either[Result, AuthenticatedRequest[A]]] =
    authorised(ConfidenceLevel.L200)
      .retrieve(Retrievals.nino) {
        case Some(nino) =>
          val retrievals = CachedAuthRetrievals(nino)
          val authenticatedRequest = AuthenticatedRequest(request, Nino(nino))

          if (cacheResult) {
            authCacheRepository.putSession(AuthRetrievalsKey, retrievals).map { _ =>
              Right(authenticatedRequest)
            }
          } else {
            Future.successful(Right(authenticatedRequest))
          }
        case None =>
          logger.error("Unable to retrieve NINO from Auth")
          Future.successful(Left(Status(UNAUTHORIZED)))
      }
      .recover {
        case x: NoActiveSession =>
          logger.error("Failed to authorise: " + x.reason)
          Left(Unauthorized(x.getMessage))
        case y: InsufficientConfidenceLevel =>
          logger.error("Failed to authorise: " + y.reason)
          Left(Unauthorized(y.getMessage))
      }

  override protected def refine[A](request: Request[A]): Future[Either[Result, AuthenticatedRequest[A]]] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    if (appConfig.mongoAuthEnabled) {
      authCacheRepository
        .getFromSession[CachedAuthRetrievals](AuthRetrievalsKey)
        .flatMap {
          case Some(cacheRetrievals) =>
            logger.debug("Auth retrieval cache HIT")
            Future.successful(
              Right(AuthenticatedRequest(request, Nino(cacheRetrievals.nino)))
            )

          case None =>
            logger.debug("Auth retrieval cache MISS..!!")
            callAuth(request, cacheResult = true)
        }
    } else {
      logger.debug("Mongo caching for auth retrievals DISABLED..!!")
      callAuth(request, cacheResult = false)
    }
  }

  override protected def executionContext: ExecutionContext = cc.executionContext
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction extends ActionRefiner[Request, AuthenticatedRequest]
