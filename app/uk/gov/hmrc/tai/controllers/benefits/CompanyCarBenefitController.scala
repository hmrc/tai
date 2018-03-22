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

package uk.gov.hmrc.tai.controllers.benefits

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.benefits.WithdrawCarAndFuel
import uk.gov.hmrc.tai.service.benefits.BenefitsService

import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class CompanyCarBenefitController @Inject()(companyCarBenefitService: BenefitsService) extends BaseController with ApiFormats {

  def companyCarBenefits(nino: Nino): Action[AnyContent] = Action.async { implicit request =>
    companyCarBenefitService.companyCarBenefits(nino).map{
      case Nil => NotFound
      case c => Ok(Json.toJson(ApiResponse(c,Nil)))
    }
  }

  def companyCarBenefitForEmployment(nino: Nino, employmentSeqNum: Int): Action[AnyContent] = Action.async { implicit request =>
    companyCarBenefitService.companyCarBenefitForEmployment(nino, employmentSeqNum).map{
      case None => NotFound
      case Some(c) => Ok(Json.toJson(ApiResponse(c,Nil)))
    }
  }

  def withdrawCompanyCarAndFuel(nino: Nino, employmentSeqNum: Int, carSeqNum: Int): Action[JsValue] = Action.async(parse.json) {
    implicit request =>
    withJsonBody[WithdrawCarAndFuel] { removeCarAndFuel =>
      companyCarBenefitService.withdrawCompanyCarAndFuel(nino, employmentSeqNum, carSeqNum, removeCarAndFuel) map(_ => Ok)
    }
  }
}