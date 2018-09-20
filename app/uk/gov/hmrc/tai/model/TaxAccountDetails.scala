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

package uk.gov.hmrc.tai.model

import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.tai.model.tai.TaxYear

case class TaxAccountDetails(taxAccountId: Int,
                             date: String,
                             nino: Nino,
                             noCYEmployment: Boolean,
                             taxYear: TaxYear,
                             previousTaxAccountId: Int,
                             previousYearTaxAccountId: Int,
                             nextTaxAccountId: Option[Int],
                             nextYearTaxAccountId: Option[Int],
                             totalEstTax: Int,
                             // totalEstPay: JsObject,
                             inYearCalcResult: Int,
                             inYearCalcAmount: Int,
                             // adjustedNetIncome: JsObject,
                             // totalLiability: JsObject,
                             incomeSources: Seq[IncomeSources])

object TaxAccountDetails {

  implicit val formats = Json.format[TaxAccountDetails]

}