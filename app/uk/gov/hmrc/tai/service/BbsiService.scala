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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.CloseAccountRequest
import uk.gov.hmrc.tai.model.domain.{BankAccount, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.{CloseBankAccount, IncorrectBankAccount}
import uk.gov.hmrc.tai.repositories.BbsiRepository
import uk.gov.hmrc.tai.templates.html.{IncorrectBankAccountIform, RemoveBankAccountIform}
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BbsiService @Inject()(
  bbsiRepository: BbsiRepository,
  iFormSubmissionService: IFormSubmissionService,
  auditor: Auditor)(implicit ec: ExecutionContext) {

  private val CloseBankAccountAuditRequest = "CloseBankAccountRequest"

  def bbsiDetails(nino: Nino, taxYear: TaxYear = TaxYear())(implicit hc: HeaderCarrier): Future[Seq[BankAccount]] =
    bbsiRepository.bbsiDetails(nino, taxYear)

  def bbsiAccount(nino: Nino, id: Int)(implicit hc: HeaderCarrier): Future[Option[BankAccount]] =
    bbsiDetails(nino) map (_.find(_.id == id))

  def closeBankAccount(nino: Nino, id: Int, closeAccountRequest: CloseAccountRequest)(
    implicit hc: HeaderCarrier): Future[String] =
    bbsiAccount(nino, id) flatMap {
      case Some(bankAccount) =>
        iFormSubmissionService.uploadIForm(
          nino,
          "CloseBankAccount",
          "BBSI5",
          (person: Person) => {
            val templateModel = CloseBankAccount(
              person,
              TaxYear(),
              bankAccount,
              closeAccountRequest.date,
              closeAccountRequest.interestEarnedThisTaxYear)
            Future.successful(RemoveBankAccountIform(templateModel).toString())
          }
        ) map { envelopeId =>
          auditor.sendDataEvent(
            CloseBankAccountAuditRequest,
            detail =
              Map("nino" -> nino.nino, "envelope Id" -> envelopeId, "end-date" -> closeAccountRequest.date.toString()))

          envelopeId
        }

      case None => throw BankAccountNotFound("Bank Account not found")
    }

  def removeIncorrectBankAccount(nino: Nino, id: Int)(implicit hc: HeaderCarrier): Future[String] =
    bbsiAccount(nino, id) flatMap {
      case Some(bankAccount) =>
        iFormSubmissionService.uploadIForm(
          nino,
          "RemoveIncorrectBankAccount",
          "BBSI5",
          (person: Person) => {
            val templateModel = IncorrectBankAccount(person, TaxYear(), bankAccount)
            Future.successful(IncorrectBankAccountIform(templateModel).toString())
          }
        ) map { envelopeId =>
          auditor.sendDataEvent(
            IFormConstants.RemoveBankAccountRequest,
            detail = Map("nino" -> nino.nino, "envelope Id" -> envelopeId))

          envelopeId
        }

      case None => throw BankAccountNotFound("Bank Account not found")
    }

  def updateBankAccountInterest(nino: Nino, id: Int, interest: BigDecimal)(implicit hc: HeaderCarrier): Future[String] =
    bbsiAccount(nino, id) flatMap {
      case Some(bankAccount) =>
        iFormSubmissionService.uploadIForm(
          nino,
          "UpdateBankAccountInterest",
          "BBSI5",
          (person: Person) => {
            val templateModel = IncorrectBankAccount(person, TaxYear(), bankAccount, Some(interest))
            Future.successful(IncorrectBankAccountIform(templateModel).toString())
          }
        ) map { envelopeId: String =>
          auditor.sendDataEvent(
            IFormConstants.UpdateBankAccountRequest,
            detail = Map("nino" -> nino.nino, "envelope Id" -> envelopeId, "interest" -> interest.toString))

          envelopeId
        }

      case None => throw BankAccountNotFound("Bank Account not found")
    }
}

case class BankAccountNotFound(message: String) extends RuntimeException(s"error message :$message")
