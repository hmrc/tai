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

package uk.gov.hmrc.tai.model.nps

import uk.gov.hmrc.tai.model.TaxCodeIncomeSummary
import uk.gov.hmrc.tai.util.TaiConstants

case class MergedEmployment(
  incomeSource: NpsIncomeSource,
  employment: Option[NpsEmployment] = None,
  adjustedNetIncome: Option[BigDecimal] = None) {

  lazy val orderField =
    s"${incomeSource.employmentType.getOrElse(TaiConstants.SecondaryEmployment)}-${incomeSource.name.getOrElse("No Name Supplied")}"

  def toTaxCodeIncomeSummary: TaxCodeIncomeSummary =
    incomeSource.toTaxCodeIncomeSummary(employment, adjustedNetIncome)

}
