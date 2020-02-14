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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{BadRequestException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.builders.OldEmploymentBuilder
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiResponse, EmploymentCollection, OldEmploymentCollection}
import uk.gov.hmrc.tai.model.domain.{Employment, EndEmployment}
import uk.gov.hmrc.tai.model.error.{EmploymentAccountStubbed, EmploymentNotFound}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{AnnualAccountService, EmploymentService}

//TODO: Renamme - this controller can work for both Old and New CombinedEmploymentAccount information formats. Maybe even so far as to remove the OldEmploymentsModel and replacing it with a Contract case class.
@Singleton
class OldEmploymentsController @Inject()(
  employmentService: EmploymentService,
  accountService: AnnualAccountService,
  authentication: AuthenticationPredicate)
    extends BaseController with ApiFormats {

  def employments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {

    //TODO: When switch on, return 503 with error message.
    implicit request =>
      val oldEmployments = for {
        employments <- employmentService.employments(nino, year)
        accounts    <- accountService.annualAccounts(nino, year)
      } yield OldEmploymentBuilder.build(employments, accounts, year)

      oldEmployments
        .map { oes =>
          Ok(Json.toJson(ApiResponse(OldEmploymentCollection(oes), Nil)))
        }
        .recover {
          case ex: NotFoundException   => NotFound(ex.getMessage)
          case ex: BadRequestException => BadRequest(ex.getMessage)
          case ex                      => InternalServerError(ex.getMessage)
        }
  }

  def employment(nino: Nino, id: Int): Action[AnyContent] = authentication.async { implicit request =>
    //TODO: When switch on, return 503 with error message.
    employmentService
      .employment(nino, id)
      .map {
        case Right(employment)        => Ok(Json.toJson(ApiResponse(employment, Nil))) //TODO: Weld to Account Info
        case Left(EmploymentNotFound) => NotFound("EmploymentNotFound")
        case Left(EmploymentAccountStubbed) =>
          BadGateway("Employment contains stub annual account data due to RTI unavailability") //TODO: Can we remove this?
      }
      .recover {
        case _: NotFoundException => NotFound("Employment not found")
        case error                => InternalServerError(error.getMessage)
      }
  }
}
