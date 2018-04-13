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

package uk.gov.hmrc.tai.controllers.income

import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiLink, ApiResponse}
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{IncomeService, TaxAccountService}

@Singleton
class IncomeController @Inject()(incomeService: IncomeService,
                                 taxAccountService: TaxAccountService,
                                 authentication: AuthenticationPredicate)
  extends BaseController
    with ApiFormats
    with TaxCodeIncomeSourceAPIFormatters
    with ControllerErrorHandler{

  def untaxedInterest(nino: Nino): Action[AnyContent] = authentication.async {
    implicit request =>

      incomeService.untaxedInterest(nino).map {
        case Some(untaxedInterest) => Ok(Json.toJson(ApiResponse(untaxedInterest, Nil)))
        case None => NotFound
      } recoverWith taxAccountErrorHandler
  }

  def taxCodeIncomesForYear(nino: Nino, year:TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>
      incomeService.taxCodeIncomes(nino, year).map {
        case Seq() => NotFound
        case taxCodeIncomes => Ok(Json.toJson(ApiResponse(Json.toJson(taxCodeIncomes)(Writes.seq(taxCodeIncomeSourceWrites)), Nil)))
      }recoverWith taxAccountErrorHandler
  }

  def income(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>
      incomeService.incomes(nino, year).map{
        income => Ok(Json.toJson(ApiResponse(income, Seq.empty[ApiLink])))
      } recoverWith taxAccountErrorHandler
  }

  def updateTaxCodeIncome(nino: Nino, snapshotId: TaxYear, employmentId: Int): Action[JsValue] =
    authentication.async(parse.json) {
      implicit request =>
        withJsonBody[UpdateTaxCodeIncomeRequest] { updateTaxCodeIncomeRequest =>
          incomeService.updateTaxCodeIncome(nino, snapshotId, employmentId,
            updateTaxCodeIncomeRequest.amount) map {
            case IncomeUpdateSuccess => Ok
            case InvalidAmount(message) => BadRequest(message)
            case IncomeUpdateFailed(message) => InternalServerError(message)
          }
        }.recover {
          case _ => InternalServerError
        }
    }

}