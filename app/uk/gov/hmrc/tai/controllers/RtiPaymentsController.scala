/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.config.CustomErrorHandler
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.RtiPaymentsService

import scala.concurrent.ExecutionContext

@Singleton
class RtiPaymentsController @Inject() (
  rtiPaymentsService: RtiPaymentsService,
  authentication: AuthJourney,
  cc: ControllerComponents,
  customErrorHandler: CustomErrorHandler
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def rtiPayments(nino: Nino, taxYear: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      rtiPaymentsService
        .getRtiPayments(nino, taxYear)
        .bimap(
          error => customErrorHandler.handleControllerErrorStatuses(error),
          payments => Ok(Json.toJson(ApiResponse(payments, Nil)))
        )
        .merge recoverWith customErrorHandler.handleControllerExceptions()
    }
}
