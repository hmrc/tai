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

package uk.gov.hmrc.tai.controllers.benefits

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.service.benefits.BenefitsService

import scala.concurrent.ExecutionContext

@Singleton
class CompanyCarBenefitController @Inject() (
  companyCarBenefitService: BenefitsService,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  def companyCarBenefits(nino: Nino): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      companyCarBenefitService.companyCarBenefits(nino).map {
        case Nil => NotFound
        case c =>
          implicit val apiResponseWrites: Writes[ApiResponse[JsValue]] = ApiResponse.apiFormat[JsValue]
          Ok(Json.toJson(ApiResponse(Json.obj("companyCarBenefits" -> c.map(Json.toJson(_))), Nil)))
      }
  }

}
