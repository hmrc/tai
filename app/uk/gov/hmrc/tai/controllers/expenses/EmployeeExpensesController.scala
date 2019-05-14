/*
 * Copyright 2019 HM Revenue & Customs
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
/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.ApiFormats
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.{IabdUpdateExpensesRequest, UpdateIabdEmployeeExpense}
import uk.gov.hmrc.tai.service.expenses.EmployeeExpensesService

import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class EmployeeExpensesController @Inject()(
                                            authentication: AuthenticationPredicate,
                                            employeeExpensesService: EmployeeExpensesService
                                          )
  extends BaseController
    with ApiFormats
    with ControllerErrorHandler {

  def updateEmployeeExpensesData(nino: Nino, year: TaxYear, iabd: Int ): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IabdUpdateExpensesRequest] {
        iabdUpdateExpensesRequest =>
          employeeExpensesService.updateEmployeeExpensesData(
            nino = nino,
            taxYear = year,
            version = iabdUpdateExpensesRequest.version,
            expensesData = UpdateIabdEmployeeExpense(iabdUpdateExpensesRequest.grossAmount),
            iabd = iabd
          ).map {
            value =>
              value.status match {
                case OK | NO_CONTENT | ACCEPTED => NoContent
                case _ => InternalServerError
              }
          }
      }
  }

  def getEmployeeExpensesData(nino: Nino, year: Int, iabd: Int): Action[AnyContent] = authentication.async {
    implicit request =>
      employeeExpensesService.getEmployeeExpenses(nino, year, iabd).map {
        iabdData =>
          Ok(Json.toJson(iabdData))
      } recoverWith taxAccountErrorHandler
  }
}
