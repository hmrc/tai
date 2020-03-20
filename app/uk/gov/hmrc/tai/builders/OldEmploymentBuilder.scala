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

package uk.gov.hmrc.tai.builders

import play.api.Logger
import uk.gov.hmrc.tai.model.domain.{AnnualAccount, Employment, OldEmployment, Unavailable}
import uk.gov.hmrc.tai.model.tai.TaxYear

object OldEmploymentBuilder {

  def build(employments: Seq[Employment], accounts: Seq[AnnualAccount], taxYear: TaxYear): Seq[OldEmployment] = {

    val accountAssignedEmployments = assignEmployments(employments, accounts)
    val united = combineDuplicates(accountAssignedEmployments)
    val nonunited = provideEmptyAccount(employments.filterNot(e => united.map(_.key).contains(e.key)), taxYear)

    united ++ nonunited
  }

  private def assignEmployments(employments: Seq[Employment], accounts: Seq[AnnualAccount]): Seq[OldEmployment] =
    accounts.flatMap(account => {
      employments.filter(emp => emp.employerDesignation == account.employerDesignation) match {
        case Seq(single) => Some(OldEmployment(account, single))
        case many =>
          Logger.warn(s"multiple matches found")
          many.find(e => e.key == account.key).map(e => OldEmployment(account, e))
        case _ =>
          Logger.warn(s"no match found")
          None
      }
    })

  private def combineDuplicates(oldEmployments: Seq[OldEmployment]): Seq[OldEmployment] =
    oldEmployments.map(_.key).distinct map { distinctKey =>
      val duplicates = oldEmployments.filter(_.key == distinctKey)
      duplicates.head.copy(annualAccounts = duplicates.flatMap(_.annualAccounts))
    }

  private def provideEmptyAccount(employments: Seq[Employment], taxYear: TaxYear): Seq[OldEmployment] =
    employments.map { e =>
//      OldEmployment(AnnualAccount(e.key, taxYear, Unavailable, Nil, Nil), e)
      OldEmployment(e)
    }
}
