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

package uk.gov.hmrc.tai.model

import java.time.LocalDate
import play.api.libs.json._
import uk.gov.hmrc.tai.model.enums.BasisOperation.BasisOperation

case class TaxBand(
  income: Option[BigDecimal] = None,
  tax: Option[BigDecimal] = None,
  lowerBand: Option[BigDecimal] = None,
  upperBand: Option[BigDecimal] = None,
  rate: Option[BigDecimal] = None
)
object TaxBand {
  implicit val formats: OFormat[TaxBand] = Json.format[TaxBand]
}

case class Employments(
  id: Option[Int] = None,
  name: Option[String] = None,
  taxCode: Option[String],
  basisOperation: Option[BasisOperation] = None
)

object Employments {
  implicit val format: OFormat[Employments] = Json.format[Employments]
}

case class Tax(
  totalIncome: Option[BigDecimal] = None,
  totalTaxableIncome: Option[BigDecimal] = None,
  totalTax: Option[BigDecimal] = None,
  totalInYearAdjustment: Option[BigDecimal] = None,
  inYearAdjustmentIntoCY: Option[BigDecimal] = None,
  inYearAdjustmentIntoCYPlusOne: Option[BigDecimal] = None,
  inYearAdjustmentFromPreviousYear: Option[BigDecimal] = None,
  taxBands: Option[List[TaxBand]] = None,
  allowReliefDeducts: Option[BigDecimal] = None,
  actualTaxDueAssumingBasicRateAlreadyPaid: Option[BigDecimal] = None,
  actualTaxDueAssumingAllAtBasicRate: Option[BigDecimal] = None
)

object Tax {
  implicit val formats: OFormat[Tax] = Json.format[Tax]
}

case class IabdSummary(
  iabdType: Int,
  description: String,
  amount: BigDecimal,
  employmentId: Option[Int] = None,
  estimatedPaySource: Option[Int] = None,
  employmentName: Option[String] = None
)

object IabdSummary {
  implicit val formats: OFormat[IabdSummary] = Json.format[IabdSummary]
}

case class TaxComponent(amount: BigDecimal, componentType: Int, description: String, iabdSummaries: List[IabdSummary])

object TaxComponent {
  implicit val formats: OFormat[TaxComponent] = Json.format[TaxComponent]
}

case class TaxCodeIncomeSummary(
  name: String,
  taxCode: String,
  employmentId: Option[Int] = None,
  employmentPayeRef: Option[String] = None,
  employmentType: Option[Int] = None,
  incomeType: Option[Int] = None,
  employmentStatus: Option[Int] = None,
  tax: Tax,
  worksNumber: Option[String] = None,
  jobTitle: Option[String] = None,
  startDate: Option[LocalDate] = None,
  endDate: Option[LocalDate] = None,
  income: Option[BigDecimal] = None,
  otherIncomeSourceIndicator: Option[Boolean] = None,
  isEditable: Boolean = false,
  isLive: Boolean = false,
  isOccupationalPension: Boolean = false,
  isPrimary: Boolean = true,
  potentialUnderpayment: Option[BigDecimal] = None,
  basisOperation: Option[BasisOperation] = None
)

object TaxCodeIncomeSummary {
  implicit val formats: OFormat[TaxCodeIncomeSummary] = Json.format[TaxCodeIncomeSummary]
}

case class TaxCodeIncomeTotal(
  taxCodeIncomes: List[TaxCodeIncomeSummary],
  totalIncome: BigDecimal,
  totalTax: BigDecimal,
  totalTaxableIncome: BigDecimal
)

object TaxCodeIncomeTotal {
  implicit val formats: OFormat[TaxCodeIncomeTotal] = Json.format[TaxCodeIncomeTotal]
}

case class NoneTaxCodeIncomes(
  statePension: Option[BigDecimal] = None,
  statePensionLumpSum: Option[BigDecimal] = None,
  otherPensions: Option[TaxComponent] = None,
  otherIncome: Option[TaxComponent] = None,
  taxableStateBenefit: Option[TaxComponent] = None,
  untaxedInterest: Option[TaxComponent] = None,
  bankBsInterest: Option[TaxComponent] = None,
  dividends: Option[TaxComponent] = None,
  foreignInterest: Option[TaxComponent] = None,
  foreignDividends: Option[TaxComponent] = None,
  totalIncome: BigDecimal
)

object NoneTaxCodeIncomes {
  implicit val formats: OFormat[NoneTaxCodeIncomes] = Json.format[NoneTaxCodeIncomes]
}

case class TaxCodeIncomes(
  employments: Option[TaxCodeIncomeTotal] = None,
  occupationalPensions: Option[TaxCodeIncomeTotal] = None,
  taxableStateBenefitIncomes: Option[TaxCodeIncomeTotal] = None,
  ceasedEmployments: Option[TaxCodeIncomeTotal] = None,
  hasDuplicateEmploymentNames: Boolean,
  totalIncome: BigDecimal,
  totalTaxableIncome: BigDecimal,
  totalTax: BigDecimal
)

object TaxCodeIncomes {
  implicit val formats: OFormat[TaxCodeIncomes] = Json.format[TaxCodeIncomes]
}

case class Incomes(taxCodeIncomes: TaxCodeIncomes, noneTaxCodeIncomes: NoneTaxCodeIncomes, total: BigDecimal)

object Incomes {
  implicit val formats: OFormat[Incomes] = Json.format[Incomes]
}

case class Adjustment(codingAmount: BigDecimal = BigDecimal(0), amountInTermsOfTax: BigDecimal = BigDecimal(0))
object Adjustment {
  implicit val formats: OFormat[Adjustment] = Json.format[Adjustment]
}
