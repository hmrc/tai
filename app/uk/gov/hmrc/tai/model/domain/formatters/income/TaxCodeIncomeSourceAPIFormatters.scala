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

package uk.gov.hmrc.tai.model.domain.formatters.income

import org.joda.time.LocalDate
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.income.{BasisOperation, IabdUpdateSource, TaxCodeIncome, TaxCodeIncomeStatus}

trait TaxCodeIncomeSourceAPIFormatters {

  val taxComponentTypeWrites = new Writes[TaxComponentType] {
    override def writes(taxComponentType: TaxComponentType) = JsString(taxComponentType.toString)
  }

  val taxCodeIncomeSourceStatusWrites = new Writes[TaxCodeIncomeStatus] {
    override def writes(status: TaxCodeIncomeStatus) = JsString(status.toString)
  }

  val basisOperationWrites = new Writes[BasisOperation] {
    override def writes(basisOperation: BasisOperation) = JsString(basisOperation.toString)
  }

  val taxCodeIncomeSourceWrites: Writes[TaxCodeIncome] = (
    (JsPath \ "componentType").write[TaxComponentType](taxComponentTypeWrites) and
      (JsPath \ "employmentId").writeNullable[Int] and
      (JsPath \ "amount").write[BigDecimal] and
      (JsPath \ "description").write[String] and
      (JsPath \ "taxCode").write[String] and
      (JsPath \ "name").write[String] and
      (JsPath \ "basisOperation").write[BasisOperation](basisOperationWrites) and
      (JsPath \ "status").write[TaxCodeIncomeStatus](taxCodeIncomeSourceStatusWrites) and
      (JsPath \ "inYearAdjustmentIntoCY").write[BigDecimal] and
      (JsPath \ "totalInYearAdjustment").write[BigDecimal] and
      (JsPath \ "inYearAdjustmentIntoCYPlusOne").write[BigDecimal] and
      (JsPath \ "iabdUpdateSource").writeNullable[IabdUpdateSource] and
      (JsPath \ "updateNotificationDate").writeNullable[LocalDate] and
      (JsPath \ "updateActionDate").writeNullable[LocalDate]
  )(unlift(TaxCodeIncome.unapply))
}
