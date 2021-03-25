/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.enums

import play.api.libs.json._
import uk.gov.hmrc.tai.model.enums

object APITypes extends Enumeration {
  type APITypes = Value
  val NpsTaxAccountAPI: enums.APITypes.Value = Value
  val NpsIabdAllAPI: enums.APITypes.Value = Value
  val NpsIabdSpecificAPI: enums.APITypes.Value = Value
  val NpsPersonAPI: enums.APITypes.Value = Value
  val NpsEmploymentAPI: enums.APITypes.Value = Value
  val NpsIabdUpdateEstPayAutoAPI: enums.APITypes.Value = Value
  val NpsIabdUpdateEstPayManualAPI: enums.APITypes.Value = Value
  val NpsIabdUpdateFlatRateExpensesAPI: enums.APITypes.Value = Value
  val RTIAPI: enums.APITypes.Value = Value
  val DesTaxAccountAPI: enums.APITypes.Value = Value
  val DesIabdAllAPI: enums.APITypes.Value = Value
  val DesIabdSpecificAPI: enums.APITypes.Value = Value
  val DesIabdUpdateEstPayAutoAPI: enums.APITypes.Value = Value
  val DesIabdUpdateEstPayManualAPI: enums.APITypes.Value = Value
  val DesIabdUpdateFlatRateExpensesAPI: enums.APITypes.Value = Value
  val DesIabdGetFlatRateExpensesAPI: enums.APITypes.Value = Value
  val DesIabdGetEmployeeExpensesAPI: enums.APITypes.Value = Value
  val DesIabdUpdateEmployeeExpensesAPI: enums.APITypes.Value = Value
  val PdfServiceAPI: enums.APITypes.Value = Value
  val CompanyCarAPI: enums.APITypes.Value = Value
  val FusCreateEnvelope: enums.APITypes.Value = Value
  val FusUploadFile: enums.APITypes.Value = Value
  val FusCloseEnvelope: enums.APITypes.Value = Value
  val BbsiAPI: enums.APITypes.Value = Value
  val TaxCodeChangeAPI: enums.APITypes.Value = Value
  val TaxAccountHistoryAPI: enums.APITypes.Value = Value
}

object BasisOperation extends Enumeration {
  type BasisOperation = Value
  val Week1Month1, Cumulative, Week1Month1NotOperated, CumulativeNotOperated = Value
  val basisOperations = Map(
    1 -> Week1Month1,
    2 -> Cumulative,
    3 -> Week1Month1NotOperated,
    4 -> CumulativeNotOperated
  )

  implicit val enumFormat: Format[BasisOperation] = new Format[BasisOperation] {
    def reads(json: JsValue): JsSuccess[Value] =
      JsSuccess(
        json
          .validate[Int]
          .fold(
            invalid = { _ =>
              BasisOperation.withName(json.as[String])
            },
            valid = { basis =>
              basisOperations.getOrElse(basis, throw new RuntimeException("Invalid BasisOperation Type"))
            }
          ))

    def writes(enum: BasisOperation): JsString = JsString(enum.toString)
  }
}
