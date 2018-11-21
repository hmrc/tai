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

package uk.gov.hmrc.tai.controllers.expenses

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.IabdEditDataRequest
import uk.gov.hmrc.tai.model.api.ApiFormats
import uk.gov.hmrc.tai.model.domain.response.{ExpensesUpdateFailure, ExpensesUpdateSuccess}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.expenses.FlatRateExpensesService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class FlatRateExpensesController @Inject()(
                                            authentication: AuthenticationPredicate,
                                            flatRateExpensesService: FlatRateExpensesService
                                          )
  extends BaseController
    with ApiFormats
    with ControllerErrorHandler {

  def updateFlatRateExpensesAmount(nino: Nino, year: TaxYear): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IabdEditDataRequest] {
        iabdEditDataRequest =>
          flatRateExpensesService.updateFlatRateExpensesAmount(nino, year, iabdEditDataRequest.version, iabdEditDataRequest.newAmount) map {
            case ExpensesUpdateSuccess => Ok
            case ExpensesUpdateFailure => InternalServerError
          }
      }.recover {
        case e =>
          Logger.error("[FlatRateExpensesController][UpdateFlatRateExpensesAmount]Failed with exception", e)
          InternalServerError
      }
  }

}
