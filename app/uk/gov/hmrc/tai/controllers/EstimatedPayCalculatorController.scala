/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.mvc.Action
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.service.TaiService

import scala.concurrent.Future

@Singleton
class EstimatedPayCalculatorController @Inject()(taiService: TaiService, authentication: AuthenticationPredicate)
    extends BaseController {

  def calculateFullYearEstimatedPay(): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[PayDetails] { payDetails =>
      Future(Ok(Json.toJson(taiService.getCalculatedEstimatedPay(payDetails))))
    }
  }
}
