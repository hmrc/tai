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

package uk.gov.hmrc.tai.service.expenses

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.connectors.IabdConnector
import uk.gov.hmrc.tai.controllers.auth.AuthenticatedRequest
import uk.gov.hmrc.tai.model.UpdateIabdEmployeeExpense
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class EmployeeExpensesService @Inject() (iabdConnector: IabdConnector) {

  def updateEmployeeExpensesData(
    nino: Nino,
    taxYear: TaxYear,
    version: Int,
    expensesData: UpdateIabdEmployeeExpense,
    iabd: Int
  )(implicit hc: HeaderCarrier, authenticatedRequest: AuthenticatedRequest[_]): Future[HttpResponse] =
    iabdConnector.updateExpensesData(
      nino = nino,
      year = taxYear.year,
      iabdType = iabd,
      version = version,
      expensesData = expensesData,
      apiType = APITypes.DesIabdUpdateEmployeeExpensesAPI
    )

  def getEmployeeExpenses(nino: Nino, taxYear: Int, iabd: Int)(implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] =
    iabdConnector.getIabdsForType(nino, taxYear, iabd)
}
