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
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.config.FeatureTogglesConfig
import uk.gov.hmrc.tai.connectors.{DesConnector, IabdConnector, NpsConnector}
import uk.gov.hmrc.tai.model.IabdUpdateAmount
import uk.gov.hmrc.tai.model.enums.APITypes
import uk.gov.hmrc.tai.model.nps.NpsIabdRoot
import uk.gov.hmrc.tai.model.nps2.IabdType
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.Future

@Singleton
class FlatRateExpensesService @Inject()(desConnector: DesConnector,
                                        npsConnector: NpsConnector,
                                        iabdConnector: IabdConnector,
                                        featureTogglesConfig: FeatureTogglesConfig) {

  def updateFlatRateExpensesData(nino: Nino, taxYear: TaxYear, version: Int, expensesData: IabdUpdateAmount)
                                (implicit hc: HeaderCarrier): Future[HttpResponse] = {

    if(featureTogglesConfig.desUpdateEnabled) {
      desConnector.updateExpensesDataToDes(
        nino = nino,
        year = taxYear.year,
        iabdType = IabdType.FlatRateJobExpenses.code,
        version = version,
        expensesData = List(expensesData),
        apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
      )
    } else {
      npsConnector.updateExpensesData(
        nino = nino,
        year = taxYear.year,
        iabdType = IabdType.FlatRateJobExpenses.code,
        version = version,
        expensesData = List(expensesData),
        apiType = APITypes.DesIabdUpdateFlatRateExpensesAPI
      )
    }
  }

  def getFlatRateExpenses(nino: Nino, taxYear: Int)
                         (implicit hc: HeaderCarrier): Future[List[NpsIabdRoot]] = {
    if (featureTogglesConfig.desEnabled) {
      desConnector.getIabdsForTypeFromDes(nino, taxYear, IabdType.FlatRateJobExpenses.code)
    } else {
      npsConnector.getIabdsForType(nino, taxYear, IabdType.FlatRateJobExpenses.code)
    }
  }
}
