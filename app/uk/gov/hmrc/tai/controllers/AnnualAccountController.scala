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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{AnnualAccountCollection, ApiFormats, ApiResponse}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{AnnualAccountService, EmploymentService}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.tai.controllers.isolators.RtiIsolator

@Singleton
class AnnualAccountController @Inject()(
  annualAccountService: AnnualAccountService,
  employmentService: EmploymentService,
  authentication: AuthenticationPredicate,
  rtiIsolator: RtiIsolator)
    extends BaseController with ApiFormats {

  def getAccountsForEmployment(nino: Nino, year: TaxYear, employmentId: Int): Action[AnyContent] =
    (authentication andThen rtiIsolator).async { implicit request =>
      for {
        employmentFuture <- employmentService.employment(nino, employmentId)
        accountFuture    <- annualAccountService.annualAccounts(nino, year)
      } yield {
        val (employment, accounts) = (employmentFuture, accountFuture)

        employment match {
          case Right(emp) => {
            val accountSet = accounts.filter(a => a.employerDesignation == emp.employerDesignation)
            Ok(Json.toJson(ApiResponse(AnnualAccountCollection(accountSet), Nil)))
          }
          case Left(_) => NotFound("Employment not found")
        }
      }
    }

  def getAccounts(nino: Nino, year: TaxYear): Action[AnyContent] =
    (authentication andThen rtiIsolator).async { implicit request =>
      for {
        accounts <- annualAccountService.annualAccounts(nino, year)
      } yield {
        Ok(Json.toJson(ApiResponse(AnnualAccountCollection(accounts), Nil)))
      }
    }
}
