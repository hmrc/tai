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

package testOnly.controllers

import com.google.inject.Inject
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.NotFoundException
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.tai.connectors.{DefaultEmploymentDetailsConnector, DefaultIabdConnector, DefaultTaxAccountConnector}
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.ExecutionContext

class ApiCallController @Inject() (
  mcc: MessagesControllerComponents,
  defaultEmploymentDetailsConnector: DefaultEmploymentDetailsConnector,
  defaultTaxAccountConnector: DefaultTaxAccountConnector,
  defaultIabdConnector: DefaultIabdConnector
)(implicit ec: ExecutionContext)
    extends FrontendController(mcc) with I18nSupport with Logging {

  def employmentDetails(nino: Nino, taxYear: Int): Action[AnyContent] = Action.async { implicit request =>
    defaultEmploymentDetailsConnector.getEmploymentDetailsAsEitherT(nino, taxYear).value.map {
      case Left(errorResponse) =>
        Status(errorResponse.statusCode)(
          s"Error response - status is: ${errorResponse.statusCode} and response message is ${errorResponse.message}"
        )

      case Right(response) => Ok(response.body)
    }
  }

  def taxAccount(nino: Nino, taxYear: Int): Action[AnyContent] = Action.async { implicit request =>
    defaultTaxAccountConnector.taxAccount(nino, TaxYear(taxYear)).map { jsValue =>
      Ok(jsValue)
    } recover { case _: NotFoundException =>
      NotFound
    }
  }
  def iabds(nino: Nino, taxYear: Int): Action[AnyContent] = Action.async { implicit request =>
    defaultIabdConnector.iabds(nino, TaxYear(taxYear)).map { jsValue =>
      Ok(jsValue)
    } recover { case _: NotFoundException =>
      NotFound
    }
  }

}
