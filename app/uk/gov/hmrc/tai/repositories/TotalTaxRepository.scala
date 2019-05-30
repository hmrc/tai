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

package uk.gov.hmrc.tai.repositories

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.calculation.IncomeCategory
import uk.gov.hmrc.tai.model.domain.formatters.IncomeCategoryHodFormatters
import uk.gov.hmrc.tai.model.domain.formatters.taxComponents.TaxAccountHodFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TotalTaxRepository @Inject()(taxAccountRepository: TaxAccountRepository)(implicit ec: ExecutionContext)
  extends TaxAccountHodFormatters
    with IncomeCategoryHodFormatters {

  def incomeCategories(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[IncomeCategory]] =
    taxAccountRepository.taxAccount(nino, year)
      .map(_.as[Seq[IncomeCategory]](incomeCategorySeqReads))

  def taxFreeAllowance(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[BigDecimal] = {
    taxAccountRepository.taxAccount(nino, year)
      .map(_.as[BigDecimal](taxFreeAllowanceReads))
  }
}