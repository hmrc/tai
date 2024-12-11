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

package uk.gov.hmrc.tai.controllers.income

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.{ApiLink, ApiResponse}
import uk.gov.hmrc.tai.model.domain.TaxCodeIncomeComponentType
import uk.gov.hmrc.tai.model.domain.income.TaxCodeIncomeStatus
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.IncomeService

import scala.concurrent.ExecutionContext

@Singleton
class IncomeController @Inject() (
  incomeService: IncomeService,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with ControllerErrorHandler {

  def untaxedInterest(nino: Nino): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    incomeService.untaxedInterest(nino).map {
      case Some(untaxedInterest) => Ok(Json.toJson(ApiResponse(untaxedInterest, Nil)))
      case None                  => NotFound
    } recoverWith taxAccountErrorHandler()
  }

  def taxCodeIncomesForYear(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      incomeService.taxCodeIncomes(nino, year).map {
        case Seq() => NotFound
        case taxCodeIncomes =>
          Ok(Json.toJson(ApiResponse(Json.toJson(taxCodeIncomes), Nil)))
      } recoverWith taxAccountErrorHandler()
  }

  def matchedTaxCodeIncomesForYear(
    nino: Nino,
    year: TaxYear,
    incomeType: TaxCodeIncomeComponentType,
    status: TaxCodeIncomeStatus
  ): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    incomeService
      .matchedTaxCodeIncomesForYear(nino, year, incomeType, status)
      .bimap(
        error => errorToResponse(error),
        result => Ok(Json.toJson(ApiResponse(Json.toJson(result), Nil)))
      )
      .merge recoverWith taxAccountErrorHandler()
  }

  def nonMatchingCeasedEmployments(nino: Nino, year: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      incomeService
        .nonMatchingCeasedEmployments(nino, year)
        .bimap(
          error => errorToResponse(error),
          result => Ok(Json.toJson(ApiResponse(Json.toJson(result), Seq.empty[ApiLink])))
        )
        .merge recoverWith taxAccountErrorHandler()
    }

  def income(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.authWithUserDetails.async {
    implicit request =>
      incomeService.incomes(nino, year).map { income =>
        Ok(Json.toJson(ApiResponse(income, Seq.empty[ApiLink])))
      } recoverWith taxAccountErrorHandler()
  }

  def updateTaxCodeIncome(nino: Nino, snapshotId: TaxYear, employmentId: Int): Action[JsValue] =
    authentication.authWithUserDetails.async(parse.json) { implicit request =>
      withJsonBody[UpdateTaxCodeIncomeRequest] { updateTaxCodeIncomeRequest =>
        incomeService.updateTaxCodeIncome(nino, snapshotId, employmentId, updateTaxCodeIncomeRequest.amount) map {
          case IncomeUpdateSuccess         => Ok
          case InvalidAmount(message)      => BadRequest(message)
          case IncomeUpdateFailed(message) => InternalServerError(message)
        }
      }.recover { case _ =>
        InternalServerError
      }
    }
}
