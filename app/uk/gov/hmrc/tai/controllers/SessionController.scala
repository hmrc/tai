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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.repositories.cache.TaiSessionCacheRepository

import scala.concurrent.ExecutionContext

@Singleton
class SessionController @Inject() (
  sessionRepository: TaiSessionCacheRepository,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  def invalidateCache: Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    sessionRepository.deleteAllFromSession.map { _ =>
      Accepted
    }
  }
}
