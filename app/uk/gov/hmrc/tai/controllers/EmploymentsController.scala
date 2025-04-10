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
import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.{ApiResponse, EmploymentCollection}
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService

import scala.concurrent.ExecutionContext

@Singleton
class EmploymentsController @Inject() (
  employmentService: EmploymentService,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc) with ControllerErrorHandler with Logging {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.authForEmployeeExpenses.async {
    implicit request =>
      employmentService
        .employmentsAsEitherT(nino, year)
        .bimap(
          error => errorToResponse(error),
          employments => Ok(Json.toJson(ApiResponse(EmploymentCollection(employments.employments, None), Nil)))
        )
        .merge recoverWith taxAccountErrorHandler()
  }

  def employmentOnly(nino: Nino, id: Int, year: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      employmentService
        .employmentWithoutRTIAsEitherT(nino, id, year)
        .bimap(
          error => errorToResponse(error),
          {
            case Some(employment) => Ok(Json.toJson(ApiResponse(employment, Nil)))
            case None =>
              val message = s"employment id: $id not found in list of employments"
              logger.warn(message)
              errorToResponse(UpstreamErrorResponse(message, NOT_FOUND))
          }
        )
        .merge recoverWith taxAccountErrorHandler()
    }

  def employmentsOnly(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      employmentService
        .employmentsWithoutRtiAsEitherT(nino, year)
        .bimap(
          error => errorToResponse(error),
          employments => Ok(Json.toJson(ApiResponse(employments, Nil)))
        )
        .merge recoverWith taxAccountErrorHandler()
  }

  def getEmploymentsByStatusAndType(
    nino: Nino,
    year: TaxYear,
    incomeType: TaxCodeIncomeComponentType,
    status: TaxCodeIncomeStatus
  ): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    employmentService
      .employmentsWithoutRtiAsEitherT(nino, year)
      .bimap(
        error => errorToResponse(error),
        employmentsCollection => {
          val filteredEmployments = employmentsCollection.employments.filter(employment =>
            employment.employmentType == incomeType && employment.employmentStatus == status
          )
          if (filteredEmployments.isEmpty) {
            NotFound(s"No Employment with income type `$incomeType` and status `$status` found")
          } else {
            Ok(
              Json.toJson(ApiResponse(Json.toJson(employmentsCollection.copy(employments = filteredEmployments)), Nil))
            )
          }
        }
      )
      .merge recoverWith taxAccountErrorHandler()
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      employmentService
        .employmentAsEitherT(nino, id)
        .bimap(
          error => errorToResponse(error),
          {
            case Some(employment) =>
              Ok(
                Json.toJson(ApiResponse(employment, Nil))(
                  ApiResponse.apiWrites[Employment](Employment.employmentWritesWithRTIStatus)
                )
              )
            case None =>
              val message = s"employment id: $id not found in list of employments"
              logger.warn(message)
              errorToResponse(UpstreamErrorResponse(message, NOT_FOUND))
          }
        )
        .merge recoverWith taxAccountErrorHandler()
  }

  def endEmployment(nino: Nino, id: Int): Action[JsValue] = authentication.authWithUserDetails.async(parse.json) {
    implicit request =>
      withJsonBody[EndEmployment] { endEmployment =>
        employmentService
          .endEmployment(nino, id, endEmployment)
          .fold(
            error => errorToResponse(error),
            envelopeId => Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          )
      }
  }

  def addEmployment(nino: Nino): Action[JsValue] = authentication.authWithUserDetails.async(parse.json) {
    implicit request =>
      withJsonBody[AddEmployment] { employment =>
        employmentService.addEmployment(nino, employment) map (envelopeId =>
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        )
      }
  }

  def incorrectEmployment(nino: Nino, id: Int): Action[JsValue] = authentication.authWithUserDetails.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] { employment =>
        employmentService.incorrectEmployment(nino, id, employment) map (envelopeId =>
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        )
      }
  }

  def updatePreviousYearIncome(nino: Nino, taxYear: TaxYear): Action[JsValue] =
    authentication.authWithUserDetails.async(parse.json) { implicit request =>
      withJsonBody[IncorrectEmployment] { employment =>
        employmentService.updatePreviousYearIncome(nino, taxYear, employment) map (envelopeId =>
          Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
        )
      }
    }

}
