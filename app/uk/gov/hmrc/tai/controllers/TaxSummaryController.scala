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

package uk.gov.hmrc.tai.controllers

import play.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, HttpException, InternalServerException, NotFoundException}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.connectors.{DesConnector, NpsConnector}
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.service.{NpsError, TaiService, TaxAccountService}

import scala.concurrent.Future
import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest

@Singleton
class TaxSummaryController @Inject()(taiService: TaiService,
                                     taxAccountService: TaxAccountService,
                                     metrics: Metrics,
                                     authentication: AuthenticationPredicate)
  extends BaseController
    with MongoFormatter {

  def getTaxSummaryPartial(nino: Nino, year: Int): Action[AnyContent] = authentication.async {
    implicit request =>
      taiService.getCalculatedTaxAccountPartial(nino, year)
        .map(summaryDetails => Ok(Json.toJson(summaryDetails)))
        .recoverWith(convertToErrorResponse)
  }

  def getTaxSummary(nino: Nino, year: Int): Action[AnyContent] = authentication.async {
    implicit request => {
      taxAccountService.taxSummaryDetails(nino, year).map(td => Ok(Json.toJson(td)))
    } recoverWith {
      case NpsError(body, NOT_FOUND) =>
        Logger.warn(s"Tax Account - No tax Account data returned from NPS for nino $nino")
        Future.failed(new NotFoundException(body))
      case NpsError(body, BAD_REQUEST) =>
        Logger.warn(s"Tax Account - Bad Request returned from NPS for nino $nino")
        Future.successful(BadRequest(body))
      case NpsError(body, INTERNAL_SERVER_ERROR) =>
        Logger.warn(s"Tax Account - Internal Server error returned from NPS for nino $nino")
        Future.failed(new InternalServerException(body))
      case NpsError(body, status) =>
        Logger.warn(s"Tax Account - Service Unavailable returned from NPS for nino $nino")
        Future.failed(new HttpException(body, status))
    }
  }

  def updateEmployments(nino: Nino, year: Int): Action[JsValue] = authentication.async(parse.json) {
    implicit request =>
      withJsonBody[IabdUpdateEmploymentsRequest] {
        editIabd =>
          val updateEmploymentsResponse = taiService.updateEmployments(nino, year, NewEstimatedPay.code, editIabd)
          taxAccountService.invalidateTaiCacheData()
          updateEmploymentsResponse.map(response => Ok(Json.toJson(response)))
      }
  }

  private def convertToErrorResponse(implicit request: Request[_]): PartialFunction[Throwable, Future[Result]] = PartialFunction[Throwable, Future[Result]] {
    case ex: BadRequestException => Future.successful(BadRequest(ex.message))
    case ex: HttpException => throw ex
  }
}