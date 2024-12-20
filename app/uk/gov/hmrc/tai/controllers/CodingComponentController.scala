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
import play.api.Logger
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.calculation.CodingComponent.codingComponentWrites
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.CodingComponentService

import scala.concurrent.ExecutionContext

@Singleton
class CodingComponentController @Inject() (
  authentication: AuthJourney,
  codingComponentService: CodingComponentService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  private val logger: Logger = Logger(getClass.getName)

  def codingComponentsForYear(nino: Nino, year: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      codingComponentService.codingComponents(nino, year) map { codingComponentList =>
        Ok(Json.toJson(ApiResponse(Json.toJson(codingComponentList)(Writes.seq(codingComponentWrites)), Nil)))
      } recover {
        case e: BadRequestException =>
          logger.warn(s"BadRequestException on codingComponentsForYear: ${e.getMessage}")
          BadRequest(Json.toJson(Map("reason" -> e.getMessage)))
        case e: NotFoundException =>
          logger.warn(s"NotFoundException on codingComponentsForYear: ${e.getMessage}")
          NotFound(Json.toJson(Map("reason" -> e.getMessage)))
        case e: Throwable =>
          throw e
      }
    }
}
