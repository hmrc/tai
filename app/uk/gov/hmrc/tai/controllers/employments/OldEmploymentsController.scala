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

package uk.gov.hmrc.tai.controllers.employments
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import com.google.inject.Inject
import javax.inject.Singleton
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.isolators.RtiIsolator
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, OldEmploymentCollection}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.OldEmploymentService
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.Future

//TODO: Renamme - this controller can work for both Old and New CombinedEmploymentAccount information formats. Maybe even so far as to remove the OldEmploymentsModel and replacing it with a Contract case class.
@Singleton
class OldEmploymentsController @Inject()(
  oldEmploymentService: OldEmploymentService,
  authentication: AuthenticationPredicate,
  rtiIsolator: RtiIsolator)
    extends BaseController with ApiFormats {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = (authentication andThen rtiIsolator).async {
    implicit request =>
      {
        try {
          oldEmploymentService.employments(nino, year).map { oes =>
            Ok(Json.toJson(ApiResponse(OldEmploymentCollection(oes), Nil)))
          }
        } catch {
          case ex: NotFoundException   => Future.successful(NotFound(ex.getMessage))
          case ex: BadRequestException => Future.successful(BadRequest(ex.getMessage))
          case ex: Throwable           => Future.successful(InternalServerError(ex.getMessage))
        }
      }
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = (authentication andThen rtiIsolator).async {
    implicit request =>
      oldEmploymentService.employment(nino, id).map { e =>
        e match {
          case Right(oe)     => Ok(Json.toJson(ApiResponse(oe, Nil)))
          case Left(message) => InternalServerError(message)
        }
      }
  }

}
