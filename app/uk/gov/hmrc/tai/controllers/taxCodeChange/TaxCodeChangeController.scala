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

package uk.gov.hmrc.tai.controllers.taxCodeChange

import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.TaxCodeChangeService

class TaxCodeChangeController @Inject()(
  authentication: AuthenticationPredicate,
  taxCodeChangeService: TaxCodeChangeService)
    extends BaseController {

  def hasTaxCodeChanged(nino: Nino): Action[AnyContent] = authentication.async { implicit request =>
    taxCodeChangeService.hasTaxCodeChanged(nino).map { taxCodeChanged =>
      {
        Ok(Json.toJson(taxCodeChanged))
      }
    }
  }

  def taxCodeChange(nino: Nino): Action[AnyContent] = authentication.async { implicit request =>
    taxCodeChangeService.taxCodeChange(nino) map { taxCodeChange =>
      Ok(Json.toJson(ApiResponse(taxCodeChange, Seq.empty)))
    }
  }

  def taxCodeMismatch(nino: Nino): Action[AnyContent] = authentication.async { implicit request =>
    taxCodeChangeService.taxCodeMismatch(nino).map { taxCodeMismatch =>
      Ok(Json.toJson(ApiResponse(taxCodeMismatch, Seq.empty)))
    } recover {
      case ex: NotFoundException   => NotFound(Json.toJson(Map("reason"   -> ex.getMessage)))
      case ex: BadRequestException => BadRequest(Json.toJson(Map("reason" -> ex.getMessage)))
    }
  }

  def mostRecentTaxCodeRecords(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>
      val latestTaxCodeRecords = taxCodeChangeService.latestTaxCodes(nino, year)

      latestTaxCodeRecords.map { records =>
        Ok(Json.toJson(ApiResponse(records, Seq.empty)))
      } recover {
        case ex: BadRequestException => BadRequest(Json.toJson(Map("reason" -> ex.getMessage)))
      }

  }

}
