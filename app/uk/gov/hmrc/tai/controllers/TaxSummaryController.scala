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
import play.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model._
import uk.gov.hmrc.tai.model.nps2.IabdType._
import uk.gov.hmrc.tai.model.nps2.MongoFormatter
import uk.gov.hmrc.tai.service.{NpsError, TaiService, TaxAccountService}

import scala.concurrent.Future

@Singleton
class TaxSummaryController @Inject()(
  taiService: TaiService,
  taxAccountService: TaxAccountService,
  metrics: Metrics,
  authentication: AuthenticationPredicate)
    extends BaseController with MongoFormatter {

  def updateEmployments(nino: Nino, year: Int): Action[JsValue] = authentication.async(parse.json) { implicit request =>
    withJsonBody[IabdUpdateEmploymentsRequest] { editIabd =>
      val updateEmploymentsResponse = taiService.updateEmployments(nino, year, NewEstimatedPay.code, editIabd)
      taxAccountService.invalidateTaiCacheData(nino)
      updateEmploymentsResponse.map(response => Ok(Json.toJson(response)))
    }
  }
}
