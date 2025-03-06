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

package uk.gov.hmrc.tai.model.enums

import play.api.libs.json._

object APITypes extends Enumeration {
  type APITypes = Value
  val NpsTaxAccountAPI: Value = Value
  val NpsIabdAllAPI: Value = Value
  val NpsIabdSpecificAPI: Value = Value
  val NpsPersonAPI: Value = Value
  val NpsEmploymentAPI: Value = Value
  val NpsIabdUpdateEstPayAutoAPI: Value = Value
  val NpsIabdUpdateEstPayManualAPI: Value = Value
  val NpsIabdUpdateFlatRateExpensesAPI: Value = Value
  val RTIAPI: Value = Value
  val DesTaxAccountAPI: Value = Value
  val DesIabdAllAPI: Value = Value
  val DesIabdSpecificAPI: Value = Value
  val DesIabdUpdateEstPayAutoAPI: Value = Value
  val DesIabdUpdateEstPayManualAPI: Value = Value
  val DesIabdUpdateFlatRateExpensesAPI: Value = Value
  val DesIabdGetFlatRateExpensesAPI: Value = Value
  val DesIabdGetEmployeeExpensesAPI: Value = Value
  val DesIabdUpdateEmployeeExpensesAPI: Value = Value
  val HipIabdUpdateEmployeeExpensesAPI: Value = Value
  val PdfServiceAPI: Value = Value
  val CompanyCarAPI: Value = Value
  val FusCreateEnvelope: Value = Value
  val FusUploadFile: Value = Value
  val FusCloseEnvelope: Value = Value
  val BbsiAPI: Value = Value
  val TaxCodeChangeAPI: Value = Value
  val TaxAccountHistoryAPI: Value = Value
}

object BasisOperation extends Enumeration {
  type BasisOperation = Value
  val Week1Month1, Cumulative, Week1Month1NotOperated, CumulativeNotOperated = Value
  private val basisOperations: Map[Int, Value] = Map(
    1 -> Week1Month1,
    2 -> Cumulative,
    3 -> Week1Month1NotOperated,
    4 -> CumulativeNotOperated
  )

  implicit val enumFormat: Format[BasisOperation] = new Format[BasisOperation] {
    def reads(json: JsValue): JsResult[BasisOperation] =
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
          )
      )

    def writes(`enum`: BasisOperation): JsValue = JsString(`enum`.toString)
  }
}
