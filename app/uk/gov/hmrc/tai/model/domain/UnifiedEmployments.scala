/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.tai.model.domain

import uk.gov.hmrc.tai.model.tai.TaxYear

case class UnifiedEmployments(employments: Seq[Employment]) {

  def withAccountsForYear(year: TaxYear): Seq[Employment] =
    employments.collect {
      case employment if employment.hasAnnualAccountsForYear(year) =>
        employment.copy(annualAccounts = employment.annualAccountsForYear(year))
    }

  def containsTempAccount(taxYear: TaxYear): Boolean = employments.exists(_.tempUnavailableStubExistsForYear(taxYear))

  def mergeEmploymentsForTaxYear(employmentsToMerge: Seq[Employment], taxYear: TaxYear): UnifiedEmployments = {

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, currentEmployments) =>
      currentEmployments.find(_.key == employment.key).fold(employment) { currentEmployment =>
        val accountsFromOtherYears = currentEmployment.annualAccounts.filterNot(_.taxYear == taxYear)
        employment.copy(annualAccounts = employment.annualAccounts ++ accountsFromOtherYears)

    }

    UnifiedEmployments(merge(employmentsToMerge, amendEmployment))
  }

  def mergeEmployments(employmentsToMerge: Seq[Employment]): UnifiedEmployments = {

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, employmentsToMerge) =>
      employmentsToMerge.find(_.key == employment.key).fold(employment) { currentCachedEmployment =>
        employment.copy(annualAccounts = employment.annualAccounts ++ currentCachedEmployment.annualAccounts)
    }

    UnifiedEmployments(merge(employmentsToMerge, amendEmployment))
  }

  private def merge(
    employmentsToMerge: Seq[Employment],
    amendEmployment: (Employment, Seq[Employment]) => Employment): Seq[Employment] = {

    val modifiedEmployments = employments map (amendEmployment(_, employmentsToMerge))
    val unmodifiedEmployments =
      employmentsToMerge.filterNot(employment => modifiedEmployments.map(_.key).contains(employment.key))

    unmodifiedEmployments ++ modifiedEmployments
  }
}
