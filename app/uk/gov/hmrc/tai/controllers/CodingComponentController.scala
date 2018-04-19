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
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{Json, Writes}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.CodingComponentAPIFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.CodingComponentService
import uk.gov.hmrc.tai.util.RequestQueryFilter

@Singleton
class CodingComponentController @Inject()(authentication: AuthenticationPredicate,
                                          codingComponentService: CodingComponentService)
  extends BaseController
  with ApiFormats
  with RequestQueryFilter
  with CodingComponentAPIFormatters {

  def codingComponentsForYear(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async { implicit request =>
      codingComponentService.codingComponents(nino, year) map { codingComponentList =>
        Ok(Json.toJson(ApiResponse(Json.toJson(codingComponentList)(Writes.seq(codingComponentWrites)), Nil)))
      }
    }
}
