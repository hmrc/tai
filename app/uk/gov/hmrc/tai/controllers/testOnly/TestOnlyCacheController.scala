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

package uk.gov.hmrc.tai.controllers.testOnly

import com.google.inject.{Inject, Singleton}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.connectors.cache.CacheId
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.repositories.deprecated.JourneyCacheRepository

import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyCacheController @Inject()(
  repository: JourneyCacheRepository,
  authentication: AuthenticationPredicate,
  cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def delete(): Action[AnyContent] = authentication.async { implicit request =>
    repository.deleteUpdateIncome(CacheId.noSession(request.nino)) map { _ =>
      NoContent
    } recover {
      case _ => InternalServerError
    }

  }
}
