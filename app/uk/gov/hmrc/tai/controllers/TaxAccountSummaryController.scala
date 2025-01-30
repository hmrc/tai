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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadGatewayException, BadRequestException, GatewayTimeoutException, HttpException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{MissingEmploymentException, TaxAccountSummaryService}
import uk.gov.hmrc.tai.util.NpsExceptions

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TaxAccountSummaryController @Inject() (
  taxAccountSummaryService: TaxAccountSummaryService,
  authentication: AuthJourney,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) with NpsExceptions with ControllerErrorHandler {

  def taxAccountSummaryForYear(nino: Nino, year: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      taxAccountSummaryService.taxAccountSummary(nino, year) map { taxAccountSummary =>
        Ok(Json.toJson(ApiResponse(taxAccountSummary, Nil)))
      } recoverWith taxAccountErrorHandlerForAccountSummary()
    }

  private def taxAccountErrorHandlerForAccountSummary(): PartialFunction[Throwable, Future[Result]] = {
    case ex: MissingEmploymentException =>
      Future.successful(
        NotFound(Json.toJson(ApiResponse(ex.getMessage, Nil)))
      )
    case ex: BadRequestException                                     => Future.successful(BadRequest(ex.message))
    case ex: NotFoundException                                       => Future.successful(NotFound(ex.message))
    case ex: GatewayTimeoutException                                 => Future.successful(BadGateway(ex.getMessage))
    case ex: BadGatewayException                                     => Future.successful(BadGateway(ex.getMessage))
    case ex: HttpException if ex.message.contains("502 Bad Gateway") => Future.successful(BadGateway(ex.getMessage))
    case ex                                                          => throw ex
  }
}
