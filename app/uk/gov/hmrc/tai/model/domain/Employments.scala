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

package uk.gov.hmrc.tai.model.domain

import uk.gov.hmrc.tai.model.tai.TaxYear

case class Employments(employments: Seq[Employment]) {

  def accountsForYear(year: TaxYear): Employments = {
    val accountsForYear = employments.collect {
      case employment if employment.hasAnnualAccountsForYear(year) =>
        employment.copy(annualAccounts = employment.annualAccountsForYear(year))
    }

    Employments(accountsForYear)
  }

  def employmentById(id: Int): Option[Employment] = employments.find(_.sequenceNumber == id)

  def sequenceNumbers: Seq[Int] = employments.map(_.sequenceNumber)

  def containsTempAccount(taxYear: TaxYear): Boolean = employments.exists(_.tempUnavailableStubExistsForYear(taxYear))

  def mergeEmploymentsForTaxYear(employmentsToMerge: Seq[Employment], taxYear: TaxYear): Seq[Employment] = {

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, employmentsToMerge) =>
      employmentsToMerge.find(_.sequenceNumber == employment.sequenceNumber).fold(employment) { employmentToMerge =>
        val accountsFromOtherYears = employment.annualAccounts.filterNot(_.taxYear == taxYear)
        employment.copy(annualAccounts = employmentToMerge.annualAccounts ++ accountsFromOtherYears)
    }

    merge(employmentsToMerge, amendEmployment)
  }

  def mergeEmployments(employmentsToMerge: Seq[Employment]): Seq[Employment] = {

    val amendEmployment: (Employment, Seq[Employment]) => Employment = (employment, employmentsToMerge) =>
      employmentsToMerge.find(_.sequenceNumber == employment.sequenceNumber).fold(employment) { employmentToMerge =>
        employment.copy(annualAccounts = employment.annualAccounts ++ employmentToMerge.annualAccounts)
    }

    merge(employmentsToMerge, amendEmployment)
  }

  private def merge(
    employmentsToMerge: Seq[Employment],
    amendEmployment: (Employment, Seq[Employment]) => Employment): Seq[Employment] = {

    val modifiedEmployments = employments map (amendEmployment(_, employmentsToMerge))
    val unmodifiedEmployments =
      employmentsToMerge.filterNot(employment => modifiedEmployments.map(_.sequenceNumber).contains(employment.sequenceNumber))

    unmodifiedEmployments ++ modifiedEmployments
  }
}
