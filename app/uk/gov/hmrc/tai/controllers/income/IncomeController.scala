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

package uk.gov.hmrc.tai.controllers.income

import com.google.inject.{Inject, Singleton}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.tai.controllers.ControllerErrorHandler
import uk.gov.hmrc.tai.controllers.predicates.AuthenticationPredicate
import uk.gov.hmrc.tai.model.api.{ApiFormats, ApiLink, ApiResponse}
import uk.gov.hmrc.tai.model.domain.formatters.income.TaxCodeIncomeSourceAPIFormatters
import uk.gov.hmrc.tai.model.domain.income.{IncomeSource, Live, TaxCodeIncome}
import uk.gov.hmrc.tai.model.domain.requests.UpdateTaxCodeIncomeRequest
import uk.gov.hmrc.tai.model.domain.response.{IncomeUpdateFailed, IncomeUpdateSuccess, InvalidAmount}
import uk.gov.hmrc.tai.model.domain.{Employment, EmploymentIncome}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.service.{EmploymentService, IncomeService, TaxAccountService}

import scala.concurrent.Future

@Singleton
class IncomeController @Inject()(incomeService: IncomeService,
                                 taxAccountService: TaxAccountService,
                                 employmentService: EmploymentService,
                                 authentication: AuthenticationPredicate)
  extends BaseController
    with ApiFormats
    with TaxCodeIncomeSourceAPIFormatters
    with ControllerErrorHandler {

  def untaxedInterest(nino: Nino): Action[AnyContent] = authentication.async {
    implicit request =>
      incomeService.untaxedInterest(nino).map {
        case Some(untaxedInterest) => Ok(Json.toJson(ApiResponse(untaxedInterest, Nil)))
        case None => NotFound
      } recoverWith taxAccountErrorHandler
  }

  def taxCodeIncomesForYear(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>
      incomeService.taxCodeIncomes(nino, year).map {
        case Seq() => NotFound
        case taxCodeIncomes => Ok(Json.toJson(ApiResponse(Json.toJson(taxCodeIncomes)(Writes.seq(taxCodeIncomeSourceWrites)), Nil)))
      } recoverWith taxAccountErrorHandler
  }

  def matchedTaxCodeIncomesForYear(nino: Nino, year: TaxYear, incomeType: String, status: String): Action[AnyContent] = authentication.async {
    implicit request =>

      def filterMatchingEmploymentsToIncomeSource(employments: Seq[Employment], filteredTaxCodeIncomes: Seq[TaxCodeIncome]): Seq[IncomeSource] =
        employments.flatMap { emp =>
          filteredTaxCodeIncomes.flatMap(income =>
            income.employmentId.fold(Seq.empty[IncomeSource]) {
              id => if (id == emp.sequenceNumber) Seq(IncomeSource(income, emp)) else Seq.empty[IncomeSource]
            }
          )
        }

      def filterTaxCodeIncomes(taxCodeIncomes: Seq[TaxCodeIncome], incomeStatus: String): Seq[TaxCodeIncome] = {
        if (incomeStatus == Live.toString) { taxCodeIncomes.filter(income => income.status == Live && income.componentType.toString == incomeType) }
        else { taxCodeIncomes.filter(income => income.status != Live && income.componentType.toString == incomeType) }
      }

      (for {
        taxCodeIncomes <- incomeService.taxCodeIncomes(nino, year)
        filteredTaxCodeIncomes: Seq[TaxCodeIncome] = filterTaxCodeIncomes(taxCodeIncomes, status)
        employments: Seq[Employment] <-
          if (filteredTaxCodeIncomes.isEmpty) { Future.successful(Seq.empty[Employment]) }
          else { employmentService.employments(nino, year) }
        result: Seq[IncomeSource] = filterMatchingEmploymentsToIncomeSource(employments, filteredTaxCodeIncomes)
      } yield (result: Seq[IncomeSource]) match {
        case Seq() => NotFound
        case _ => Ok(Json.toJson(ApiResponse(Json.toJson(result), Nil)))
      }) recoverWith taxAccountErrorHandler
  }

  def nonMatchingCeasedEmployments(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>

      def filterNonMatchingCeasedEmploymentsWithEndDate(employments: Seq[Employment], taxCodeIncomes: Seq[TaxCodeIncome]): Seq[Employment] =
        employments
          .filter(emp => !taxCodeIncomes.exists(tci => tci.employmentId.contains(emp.sequenceNumber)))
          .filter(_.endDate.isDefined)

      (for {
        taxCodeIncomes <- incomeService.taxCodeIncomes(nino, year)
        filteredTaxCodeIncomes: Seq[TaxCodeIncome] = taxCodeIncomes.filter(income => income.status != Live && income.componentType == EmploymentIncome)
        employments: Seq[Employment] <-
          if (filteredTaxCodeIncomes.isEmpty) { Future.successful(Seq.empty[Employment]) }
          else { employmentService.employments(nino, year) }
        result: Seq[Employment] = filterNonMatchingCeasedEmploymentsWithEndDate(employments, filteredTaxCodeIncomes)
      } yield (result: Seq[Employment]) match {
        case Seq() => NotFound
        case _ => Ok(Json.toJson(ApiResponse(Json.toJson(result), Seq.empty[ApiLink])))
      }) recoverWith taxAccountErrorHandler
  }

  def income(nino: Nino, year: TaxYear): Action[AnyContent] = authentication.async {
    implicit request =>
      incomeService.incomes(nino, year).map {
        income => Ok(Json.toJson(ApiResponse(income, Seq.empty[ApiLink])))
      } recoverWith taxAccountErrorHandler
  }

  def updateTaxCodeIncome(nino: Nino, snapshotId: TaxYear, employmentId: Int): Action[JsValue] =
    authentication.async(parse.json) {
      implicit request =>
        withJsonBody[UpdateTaxCodeIncomeRequest] { updateTaxCodeIncomeRequest =>
          incomeService.updateTaxCodeIncome(nino, snapshotId, employmentId, updateTaxCodeIncomeRequest.amount) map {
            case IncomeUpdateSuccess =>
              taxAccountService.invalidateTaiCacheData()
              Ok
            case InvalidAmount(message) => BadRequest(message)
            case IncomeUpdateFailed(message) => InternalServerError(message)
          }
        }.recover {
          case _ => InternalServerError
        }
    }
}