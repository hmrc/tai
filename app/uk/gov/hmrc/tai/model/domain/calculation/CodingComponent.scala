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

package uk.gov.hmrc.tai.model.domain.calculation

import play.api.libs.json.{JsPath, Writes}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.tai.model.domain.TaxComponentType.codingComponentTypeWrites
import uk.gov.hmrc.tai.model.domain.{AllowanceComponentType, BenefitComponentType, DeductionComponentType, NonTaxCodeIncomeComponentType, TaxComponentType}

case class CodingComponent(
  componentType: TaxComponentType,
  employmentId: Option[Int],
  amount: BigDecimal,
  description: String,
  inputAmount: Option[BigDecimal] = None
)

object CodingComponent {
  val codingComponentWrites: Writes[CodingComponent] = (
    (JsPath \ "componentType").write[TaxComponentType](codingComponentTypeWrites) and
      (JsPath \ "employmentId").writeNullable[Int] and
      (JsPath \ "amount").write[BigDecimal] and
      (JsPath \ "description").write[String] and
      (JsPath \ "iabdCategory").write[String] and
      (JsPath \ "inputAmount").writeNullable[BigDecimal]
    )(unapplyCodingComponentForApiJson _)

  private def unapplyCodingComponentForApiJson(
                                                t: CodingComponent
                                              ): (TaxComponentType, Option[Int], BigDecimal, String, String, Option[BigDecimal]) = {
    val iabdCategory = t.componentType match {
      case _: AllowanceComponentType        => "Allowance"
      case _: BenefitComponentType          => "Benefit"
      case _: DeductionComponentType        => "Deduction"
      case _: NonTaxCodeIncomeComponentType => "NonTaxCodeIncome"
      case _                                => throw new RuntimeException("Unrecognised coding Component type")
    }
    (t.componentType, t.employmentId, t.amount, t.description, iabdCategory, t.inputAmount)
  }
}