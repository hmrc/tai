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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.model.DateRequest
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, EmploymentCollection}
import uk.gov.hmrc.tai.model.domain.{AddEmployment, EndEmployment, IncorrectEmployment}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.EmploymentService
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class EmploymentsController @Inject()(employmentService: EmploymentService) extends BaseController
  with ApiFormats {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = Action.async { implicit request =>

    employmentService.employments(nino, year).map { employments =>

      Ok(Json.toJson(ApiResponse(EmploymentCollection(employments), Nil)))

    }.recover {
      case _: NotFoundException => NotFound
      case ex:BadRequestException => BadRequest(ex.getMessage)
      case _ => InternalServerError
    }
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = Action.async { implicit request =>
    employmentService.employment(nino, id).map {
      case Some(employment) => Ok(Json.toJson(ApiResponse(employment, Nil)))
      case None => NotFound
    }.recover {
      case _: NotFoundException => NotFound
      case _ => InternalServerError
    }
  }

  def endEmployment(nino: Nino, id: Int): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
      withJsonBody[EndEmployment] {
        endEmployment =>
          employmentService.endEmployment(nino, id, endEmployment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def addEmployment(nino: Nino): Action[JsValue] =  Action.async(parse.json) {
    implicit request =>
      withJsonBody[AddEmployment] {
        employment =>
          employmentService.addEmployment(nino, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def incorrectEmployment(nino: Nino, id: Int): Action[JsValue] =  Action.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] {
        employment =>
          employmentService.incorrectEmployment(nino, id, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }

  def updatePreviousYearIncome(nino: Nino, taxYear: TaxYear): Action[JsValue] =  Action.async(parse.json) {
    implicit request =>
      withJsonBody[IncorrectEmployment] {
        employment =>
          employmentService.updatePreviousYearIncome(nino, taxYear, employment) map (envelopeId => {
            Ok(Json.toJson(ApiResponse(envelopeId, Nil)))
          })
      }
  }
}
