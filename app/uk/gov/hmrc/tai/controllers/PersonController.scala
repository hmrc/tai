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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.service.PersonService

import scala.concurrent.ExecutionContext

@Singleton
class PersonController @Inject() (
  authentication: AuthenticationPredicate,
  personService: PersonService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with ApiFormats {

  def person(nino: Nino): Action[AnyContent] = authentication.async { implicit request =>
    personService.person(nino) map { person =>
      Ok(Json.toJson(ApiResponse(person, Nil)))
    }
  }
}
