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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, EmploymentCollection}
import uk.gov.hmrc.tai.model.domain.{AddEmployment, EndEmployment, IncorrectEmployment}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService

import scala.concurrent.ExecutionContext

@Singleton
class EmploymentsController @Inject()(
  employmentService: EmploymentService,
  authentication: AuthenticationPredicate,
  cc: ControllerComponents)(implicit ec: ExecutionContext)
    extends BackendController(cc) with ApiFormats with ControllerErrorHandler {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async { implicit request =>
    employmentService
      .employmentsAsEitherT(nino, year)
      .bimap(
        error => errorToResponse(error),
        employments =>
        Ok(Json.toJson(ApiResponse(EmploymentCollection(employments.employments, None), Nil)))
      ).merge
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = authentication.async { implicit request =>
    employmentService
      .employmentAsEitherT(nino, id)
      .bimap(
      error => errorToResponse(error),
        employment        => Ok(Json.toJson(ApiResponse(employment, Nil)))
      ).merge
  }

  def endEmployment(nino: Nino, id: Int): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[EndEmployment] { endEmployment =>
      employmentService.endEmployment(nino, id, endEmployment).fold(
        error => errorToResponse(error),
        envelopeId => Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
      )
    }
  }

  def addEmployment(nino: Nino): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[AddEmployment] { employment =>
      employmentService.addEmployment(nino, employment) map (envelopeId => {
        Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
      })
    }
  }

  def incorrectEmployment(nino: Nino, id: Int): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[IncorrectEmployment] { employment =>
      employmentService.incorrectEmployment(nino, id, employment) map (envelopeId => {
        Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
      })
    }
  }

  def updatePreviousYearIncome(nino: Nino, taxYear: TaxYear): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] { employment =>
        employmentService.updatePreviousYearIncome(nino, taxYear, employment) map (envelopeId => {
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        })
      }
  }

}
