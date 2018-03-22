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
import play.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.SessionData
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.service.{NpsError, TaxAccountService}
import uk.gov.hmrc.time.TaxYearResolver

import scala.concurrent.Future
import uk.gov.hmrc.http.{HttpException, InternalServerException, NotFoundException}

@Singleton
class TaiController @Inject()(taxAccountService: TaxAccountService,
                              metrics: Metrics) extends BaseController with MongoFormatter {

  def getTaiRoot(nino: Nino): Action[AnyContent] = Action.async {
    implicit request =>
    for {
      taiRoot <- taxAccountService.personDetails(nino)
    } yield Ok(Json.toJson(taiRoot))

  }

  def taiData(nino: Nino): Action[AnyContent] = Action.async {
    implicit request => {
      for {
        sessionData <- taxAccountService.taiData(nino, TaxYearResolver.currentTaxYear)
      } yield Ok(Json.toJson(sessionData))
    }recoverWith {
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

  def updateTaiData(nino: Nino): Action[JsValue] = Action.async(parse.json) {
    implicit request => {
      withJsonBody[SessionData] {
        sessionData => {
          taxAccountService.updateTaiData(nino, sessionData) map (_ => Ok)
        }
      } recoverWith {
        case ex: Throwable => Future.failed(new InternalServerException(ex.getMessage))
      }
    }
  }
}