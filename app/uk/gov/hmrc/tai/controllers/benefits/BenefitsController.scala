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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.benefits.RemoveCompanyBenefit
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.benefits.BenefitsService

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext

@Singleton
class BenefitsController @Inject() (
  benefitService: BenefitsService,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with ApiFormats with ControllerErrorHandler {

  def benefits(nino: Nino, taxYear: TaxYear): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      benefitService.benefits(nino, taxYear).map { benefitsFromService =>
        Ok(Json.toJson(ApiResponse(benefitsFromService, Nil)))
      } recoverWith taxAccountErrorHandler()
  }

  @nowarn("msg=parameter empId in method removeCompanyBenefits is never used")
  def removeCompanyBenefits(nino: Nino, empId: Int): Action[JsValue] =
    authentication.authWithUserDetails.async(parse.json) { implicit request =>
      withJsonBody[RemoveCompanyBenefit] { removeCompanyBenefit =>
        benefitService.removeCompanyBenefits(nino, removeCompanyBenefit) map (envelopeId =>
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        )
      }
    }
}
