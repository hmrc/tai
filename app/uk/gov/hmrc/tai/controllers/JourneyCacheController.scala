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

package uk.gov.hmrc.tai.controllers

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import uk.gov.hmrc.tai.repositories.JourneyCacheRepository
import uk.gov.hmrc.tai.connectors.CacheId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate

import scala.concurrent.ExecutionContext

@Singleton
class JourneyCacheController @Inject()(
                                        repository: JourneyCacheRepository,
                                        authentication: AuthenticationPredicate,
                                        cc: ControllerComponents)(
                                        implicit ec: ExecutionContext
                                      ) extends BackendController(cc) {

  def currentCache(journeyName: String): Action[AnyContent] = authentication.async { implicit request => {
    journeyName match {
      case "update-income" => repository.currentCache(CacheId.noSession(request.nino), journeyName) map {
        case Some(cache) if cache.nonEmpty => Ok(Json.toJson(cache))
        case _ => NoContent
      } recover {
        case _ => InternalServerError
      }
      case _ => repository.currentCache(CacheId(request.nino), journeyName) map {
        case Some(cache) if cache.nonEmpty => Ok(Json.toJson(cache))
        case _ => NoContent
      } recover {
        case _ => InternalServerError
      }
    }
  }
  }

  def currentCacheValue(journeyName: String, key: String): Action[AnyContent] = authentication.async {
    implicit request => {
      journeyName match {
        case "update-income" => repository.currentCache(CacheId.noSession(request.nino), journeyName, key) map {
          case Some(value) if value.trim != "" => Ok(Json.toJson(value))
          case _ => NoContent
        } recover {
          case _ => InternalServerError
        }
        case _ => repository.currentCache(CacheId(request.nino), journeyName, key) map {
          case Some(value) if value.trim != "" => Ok(Json.toJson(value))
          case _ => NoContent
        } recover {
          case _ => InternalServerError
        }
      }

    }
  }

  def cached(journeyName: String): Action[JsValue] = authentication.async(parse.json) { implicit request => {
    journeyName match {
      case "update-income" => withJsonBody[Map[String, String]] { cache =>
        repository.cached(CacheId.noSession(request.nino), journeyName, cache) map { cache =>
          Created(Json.toJson(cache))
        } recover {
          case _ => InternalServerError
        }
      }
      case _ => withJsonBody[Map[String, String]] { cache =>
        repository.cached(CacheId(request.nino), journeyName, cache) map { cache =>
          Created(Json.toJson(cache))
        } recover {
          case _ => InternalServerError
        }
      }
    }

  }
  }

  def flush(journeyName: String): Action[AnyContent] = authentication.async { implicit request => {
    journeyName match {
      case "update-income" => repository.flushUpdateIncome(CacheId.noSession(request.nino), journeyName) map { res =>
        NoContent
      } recover {
        case _ => InternalServerError
      }
      case _ => repository.flush(CacheId(request.nino), journeyName) map { res =>
        NoContent
      } recover {
        case _ => InternalServerError
        }
      }
    }
  }
  def flushWithEmpId(journeyName: String, empId: Int): Action[AnyContent] = authentication.async { implicit request => {
    journeyName match {
      case "update-income" => repository.flushUpdateIncomeWithEmpId(CacheId.noSession(request.nino), journeyName, empId) map { res =>
        NoContent
      } recover {
        case _ => InternalServerError
      }
      case _ => repository.flush(CacheId(request.nino), journeyName) map { res =>
        NoContent
      } recover {
        case _ => InternalServerError
      }
    }
  }
  }

}
