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

package uk.gov.hmrc.tai.service.expenses

import com.google.inject.{Inject, Singleton}
import org.joda.time.DateTime
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.connectors.DesConnector
import uk.gov.hmrc.tai.model.IabdUpdateExpensesData
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class FlatRateExpensesService @Inject()(desConnector: DesConnector){

  def updateFlatRateExpensesData(nino: Nino, taxYear: TaxYear, version: Int, expensesData: IabdUpdateExpensesData)
                                (implicit hc: HeaderCarrier): Future[HttpResponse] = {

    desConnector.updateExpensesDataToDes(
      nino = nino,
      year = taxYear.year,
      iabdType = IabdType.FlatRateJobExpenses.code,
      version = version,
      expensesData = List(expensesData),
      apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
    )
  }
}
