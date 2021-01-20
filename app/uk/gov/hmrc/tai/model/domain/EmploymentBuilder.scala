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

package uk.gov.hmrc.tai.model.domain

import com.google.inject.Inject
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.tai.TaxYear

class EmploymentBuilder @Inject()(auditor: Auditor) {

  def combineAccountsWithEmployments(
    employments: Seq[Employment],
    accounts: Seq[AnnualAccount],
    nino: Nino,
    taxYear: TaxYear)(implicit hc: HeaderCarrier): Employments = {

    def associatedEmployment(account: AnnualAccount, employments: Seq[Employment], nino: Nino, taxYear: TaxYear)(
      implicit hc: HeaderCarrier): Option[Employment] =
      employments.filter(_.employerDesignation == account.employerDesignation) match {
        case Seq(single) =>
          Logger.warn(s"single match found for $nino for $taxYear")
          Some(single.copy(annualAccounts = Seq(account)))
        case Nil =>
          Logger.warn(s"no match found for $nino for $taxYear")
          auditAssociatedEmployment(account, employments, nino.nino, taxYear.twoDigitRange)
        case many =>
          Logger.warn(s"multiple matches found for $nino for $taxYear")

          val combinedEmploymentAndAccount = many.find(_.key == account.key).map(_.copy(annualAccounts = Seq(account)))

          combinedEmploymentAndAccount orElse auditAssociatedEmployment(
            account,
            employments,
            nino.nino,
            taxYear.twoDigitRange)
      }

    def combinedDuplicates(employments: Seq[Employment]): Seq[Employment] =
      employments.map(_.key).distinct map { distinctKey =>
        val duplicates = employments.filter(_.key == distinctKey)
        duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
      }

    val accountAssignedEmployments = accounts flatMap { account =>
      associatedEmployment(account, employments, nino, taxYear)
    }

    val unified = combinedDuplicates(accountAssignedEmployments)
    val nonUnified = employments.filterNot(emp => unified.map(_.key).contains(emp.key)) map { emp =>
      emp.copy(annualAccounts = Seq(AnnualAccount(emp.key, taxYear, Unavailable, Nil, Nil)))
    }

    Employments(unified ++ nonUnified)
  }

  private def auditAssociatedEmployment(
    account: AnnualAccount,
    employments: Seq[Employment],
    nino: String,
    taxYear: String)(implicit hc: HeaderCarrier): Option[Employment] = {
    val employerKey = employments.map { employment =>
      s"${employment.name} : ${employment.key}; "
    }.mkString
    auditor.sendDataEvent(
      transactionName = "NPS RTI Data Mismatch",
      detail = Map(
        "nino"                -> nino,
        "tax year"            -> taxYear,
        "NPS Employment Keys" -> employerKey,
        "RTI Account Key"     -> account.key)
    )

    Logger.warn(
      "EmploymentRepository: Failed to identify an Employment match for an AnnualAccount instance. NPS and RTI data may not align.")
    None
  }
}
