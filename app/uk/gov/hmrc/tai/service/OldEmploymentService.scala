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

package uk.gov.hmrc.tai.service

import com.google.inject.Inject
import play.api.libs.json.Json
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.builders.OldEmploymentBuilder
import uk.gov.hmrc.tai.model.api.ApiResponse
import uk.gov.hmrc.tai.model.domain.OldEmployment
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.repositories.{AnnualAccountRepository, EmploymentRepository, PersonRepository}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class OldEmploymentService @Inject()(
  employmentRepository: EmploymentRepository,
  personRepository: PersonRepository,
  accountRepository: AnnualAccountRepository
) {

  def employments(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[OldEmployment]] = {

    val employmentsFuture = employmentRepository.employmentsForYear(nino, year)
    val accountsFuture = accountRepository.annualAccounts(nino, year)

    for {
      employments <- employmentsFuture
      accounts    <- accountsFuture
    } yield {
      OldEmploymentBuilder.build(employments, accounts, year)
    }
  }

  def employment(nino: Nino, id: Int)(implicit hc: HeaderCarrier): Future[Either[String, OldEmployment]] =
    employments(nino, TaxYear()) map { empsForYear =>
      empsForYear.filter(oe => oe.sequenceNumber == id) match {
        case Seq(single) => Right(single)
        case many        => Left("Many accounts found for this employment")
        case Nil         => Left("EmploymentNotFound")
        case _           => Left("Unknown Issue")
      }
    }
}
