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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.repositories.deprecated.JourneyCacheRepository

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JourneyCacheController @Inject() (
  repository: JourneyCacheRepository,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def currentCache(journeyName: String): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      def getCache(cacheId: CacheId): Future[Result] =
        repository.currentCache(cacheId, journeyName) map {
          case Some(cache) if cache.nonEmpty => Ok(Json.toJson(cache))
          case _                             => NoContent
        } recover { case _ =>
          InternalServerError
        }

      journeyName match {
        case "update-income" => getCache(CacheId.noSession(request.nino))
        case _               => getCache(CacheId(request.nino))
      }
  }

  def currentCacheValue(journeyName: String, key: String): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      def getCache(cacheId: CacheId): Future[Result] =
        repository.currentCache(cacheId, journeyName, key) map {
          case Some(value) if value.trim != "" => Ok(Json.toJson(value))
          case _                               => NoContent
        } recover { case _ =>
          InternalServerError
        }

      journeyName match {
        case "update-income" => getCache(CacheId.noSession(request.nino))
        case _               => getCache(CacheId(request.nino))
      }
    }

  def cached(journeyName: String): Action[JsValue] = authentication.authWithUserDetails.async(parse.json) {
    implicit request =>
      def getCache(cacheId: CacheId): Future[Result] =
        withJsonBody[Map[String, String]] { cache =>
          repository.cached(cacheId, journeyName, cache) map { cache =>
            Created(Json.toJson(cache))
          } recover { case _ =>
            InternalServerError
          }
        }

      journeyName match {
        case "update-income" => getCache(CacheId.noSession(request.nino))
        case _               => getCache(CacheId(request.nino))
      }
  }

  def flush(journeyName: String): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    journeyName match {
      case "update-income" =>
        repository.flushUpdateIncome(CacheId.noSession(request.nino), journeyName) map { _ =>
          NoContent
        } recover { case _ =>
          InternalServerError
        }
      case _ =>
        repository.flush(CacheId(request.nino), journeyName) map { _ =>
          NoContent
        } recover { case _ =>
          InternalServerError
        }
    }

  }

  def flushWithEmpId(journeyName: String, empId: Int): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      journeyName match {
        case "update-income" =>
          repository.flushUpdateIncomeWithEmpId(CacheId.noSession(request.nino), journeyName, empId) map { _ =>
            NoContent
          } recover { case _ =>
            InternalServerError
          }
        case _ =>
          repository.flush(CacheId(request.nino), journeyName) map { _ =>
            NoContent
          } recover { case _ =>
            InternalServerError
          }
      }
  }

}
