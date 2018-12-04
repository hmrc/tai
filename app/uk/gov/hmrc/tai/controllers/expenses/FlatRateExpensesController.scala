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
import play.api.libs.json.JsValue
import play.api.mvc.Action
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.IabdUpdateExpensesRequest
import uk.gov.hmrc.tai.model.api.ApiFormats
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
      withJsonBody[IabdUpdateExpensesRequest] {
        iabdUpdateExpensesRequest =>
          flatRateExpensesService.updateFlatRateExpensesAmount(
            nino = nino,
            taxYear = year,
            version = iabdUpdateExpensesRequest.version,
            newAmount = iabdUpdateExpensesRequest.newAmount
          ).map {
            value =>
              value.status match {
                case OK => NoContent
                case _ => InternalServerError
              }
          }
      }
  }
}
