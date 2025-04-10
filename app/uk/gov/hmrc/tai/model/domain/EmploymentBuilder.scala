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

import com.google.inject.Inject
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.tai.TaxYear

class EmploymentBuilder @Inject() (auditor: Auditor) {

  private val logger: Logger = Logger(getClass.getName)
  // scalastyle:off method.length
  def combineAccountsWithEmployments(
    employments: Seq[Employment],
    rtiTuple: (Seq[AnnualAccount], Boolean),
    nino: Nino,
    taxYear: TaxYear
  )(implicit hc: HeaderCarrier): Employments = {
    def associatedEmployment(account: AnnualAccount, employments: Seq[Employment], nino: Nino, taxYear: TaxYear)(
      implicit hc: HeaderCarrier
    ): Option[Employment] =
      employments.filter(_.sequenceNumber == account.sequenceNumber) match {
        case Seq(single) =>
          logger.info(s"single match found for $nino for $taxYear")
          Some(single.copy(annualAccounts = Seq(account)))
        case Nil =>
          logger.info(s"no match found for $nino for $taxYear")
          auditAssociatedEmployment(account, employments, nino.nino, taxYear.twoDigitRange)
        case many =>
          logger.info(s"multiple matches found for $nino for $taxYear")

          val combinedEmploymentAndAccount =
            many.find(_.sequenceNumber == account.sequenceNumber).map(_.copy(annualAccounts = Seq(account)))

          combinedEmploymentAndAccount orElse auditAssociatedEmployment(
            account,
            employments,
            nino.nino,
            taxYear.twoDigitRange
          )
      }

    def combinedDuplicates(employments: Seq[Employment]): Seq[Employment] =
      employments.map(_.sequenceNumber).distinct map { distinctKey =>
        val duplicates = employments.filter(_.sequenceNumber == distinctKey)
        duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
      }

    val accountAssignedEmployments = rtiTuple match {
      case (annualAccount, false) =>
        annualAccount flatMap { account =>
          associatedEmployment(account, employments, nino, taxYear)
        }
      case _ => Seq.empty
    }

    val unified = combinedDuplicates(accountAssignedEmployments)

    val nonUnified = employments.filterNot(emp => unified.map(_.sequenceNumber).contains(emp.sequenceNumber)) map {
      emp =>
        emp.copy(annualAccounts = Seq(AnnualAccount(emp.sequenceNumber, taxYear, TemporarilyUnavailable, Nil, Nil)))
    }

    Employments(unified ++ nonUnified, None, rtiTuple._2)
  }

  private def auditAssociatedEmployment(
    account: AnnualAccount,
    employments: Seq[Employment],
    nino: String,
    taxYear: String
  )(implicit hc: HeaderCarrier): Option[Employment] = {
    val employerKey = employments.map { employment =>
      s"${employment.name} : ${employment.sequenceNumber}; "
    }.mkString
    auditor.sendDataEvent(
      transactionName = "NPS RTI Data Mismatch",
      detail = Map(
        "nino"                -> nino,
        "tax year"            -> taxYear,
        "NPS Employment Keys" -> employerKey,
        "RTI Account Key"     -> account.sequenceNumber.toString
      )
    )
    None
  }
}
