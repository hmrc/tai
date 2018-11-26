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

package uk.gov.hmrc.tai.model.enums

import play.api.libs.json._

object APITypes extends Enumeration {
  type APITypes = Value
  val NpsTaxAccountAPI = Value
  val NpsIabdAllAPI = Value
  val NpsIabdSpecificAPI = Value
  val NpsPersonAPI = Value
  val NpsEmploymentAPI = Value
  val NpsIabdUpdateEstPayAutoAPI = Value
  val NpsIabdUpdateEstPayManualAPI = Value
  val RTIAPI = Value
  val DesTaxAccountAPI = Value
  val DesIabdAllAPI = Value
  val DesIabdSpecificAPI = Value
  val DesIabdUpdateEstPayAutoAPI = Value
  val DesIabdUpdateEstPayManualAPI = Value
  val DesIabdUpdateFlatRateExpensesAPI = Value
  val PdfServiceAPI = Value
  val CompanyCarAPI = Value
  val FusCreateEnvelope = Value
  val FusUploadFile = Value
  val FusCloseEnvelope = Value
  val BbsiAPI = Value
  val TaxCodeChangeAPI = Value
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

  implicit val enumFormat = new Format[BasisOperation] {
    def reads(json: JsValue) = {
      JsSuccess(json.validate[Int].fold(
        invalid = { _ =>
          BasisOperation.withName(json.as[String])
        },
        valid = { basis => basisOperations.getOrElse(basis, throw new RuntimeException("Invalid BasisOperation Type")) }
      ))
    }

    def writes(enum: BasisOperation) = JsString(enum.toString)
  }
}