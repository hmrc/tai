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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.tai.controllers.auth.AuthJourney
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TaxCodeChangeService

import scala.concurrent.ExecutionContext

class TaxCodeChangeController @Inject() (
  authentication: AuthJourney,
  taxCodeChangeService: TaxCodeChangeService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc) {

  def hasTaxCodeChanged(nino: Nino): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    taxCodeChangeService.hasTaxCodeChanged(nino).map { taxCodeChanged =>
      Ok(Json.toJson(taxCodeChanged))
    }
  }

  def taxCodeChange(nino: Nino): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    taxCodeChangeService.taxCodeChange(nino) map { taxCodeChange =>
      Ok(Json.toJson(ApiResponse(taxCodeChange, Seq.empty)))
    }
  }

  def taxCodeMismatch(nino: Nino): Action[AnyContent] = authentication.authWithUserDetails.async { implicit request =>
    taxCodeChangeService.taxCodeMismatch(nino).map { taxCodeMismatch =>
      Ok(Json.toJson(ApiResponse(taxCodeMismatch, Seq.empty)))
    }
  }

  def mostRecentTaxCodeRecords(nino: Nino, year: TaxYear): Action[AnyContent] =
    authentication.authWithUserDetails.async { implicit request =>
      val latestTaxCodeRecords = taxCodeChangeService.latestTaxCodes(nino, year)

      latestTaxCodeRecords.map { records =>
        Ok(Json.toJson(ApiResponse(records, Seq.empty)))
      }
    }
}
