/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}
import uk.gov.hmrc.tai.connectors.IabdConnector
import uk.gov.hmrc.tai.controllers.predicates.AuthenticatedRequest
import uk.gov.hmrc.tai.model.domain.formatters.IabdDetails
import uk.gov.hmrc.tai.model.domain.response._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.nps2.IabdType.NewEstimatedPay

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IabdService @Inject() (
  iabdConnector: IabdConnector
)(implicit ec: ExecutionContext) {

  def retrieveIabdDetails(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[IabdDetails]] =
    iabdConnector
      .iabds(nino, year)
      .map { responseJson =>
        val responseNotFound = (responseJson \ "error").asOpt[String].contains("NOT_FOUND")
        if (responseNotFound) {
          throw new NotFoundException(s"No iadbs found for year $year")
        }
        responseJson.as[Seq[IabdDetails]]
      }
      .map(_.filter(_.`type`.contains(NewEstimatedPay.code)))

  def updateTaxCodeAmount(nino: Nino, year: TaxYear, employmentId: Int, version: Int, amount: Int)(implicit
    hc: HeaderCarrier,
    request: AuthenticatedRequest[_]
  ): Future[IncomeUpdateResponse] =
    for {
      updateAmountResult <-
        iabdConnector.updateTaxCodeAmount(nino, year, employmentId, version, NewEstimatedPay.code, amount)
    } yield updateAmountResult match {
      case HodUpdateSuccess => IncomeUpdateSuccess
      case HodUpdateFailure => IncomeUpdateFailed(s"Hod update failed for ${year.year} update")
    }
}
