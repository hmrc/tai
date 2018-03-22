/*
 * Copyright 2018 HM Revenue & Customs
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
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.repositories.JourneyCacheRepository

import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class JourneyCacheController @Inject()(repository: JourneyCacheRepository) extends BaseController {

  def currentCache(journeyName: String): Action[AnyContent] = Action.async { implicit request =>
    repository.currentCache(journeyName) map {
      case Some(cache) if cache.nonEmpty => Ok(Json.toJson(cache))
      case _ => NotFound
    } recover {
      case _ => InternalServerError
    }
  }

  def currentCacheValue(journeyName: String, key: String): Action[AnyContent] = Action.async { implicit request =>
    repository.currentCache(journeyName, key) map {
      case Some(value) if value.trim!="" => Ok(Json.toJson(value))
      case _ => NotFound
    } recover {
      case _ => InternalServerError
    }
  }

  def cached(journeyName: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[Map[String, String]] {
      cache => repository.cached(journeyName, cache) map { cache =>
        Created(Json.toJson(cache))
      } recover {
        case _ => InternalServerError
      }
    }
  }

  def flush(journeyName: String): Action[AnyContent] = Action.async { implicit request =>
    repository.flush(journeyName) map { res =>
      NoContent
    } recover {
      case _ => InternalServerError
    }
  }
}