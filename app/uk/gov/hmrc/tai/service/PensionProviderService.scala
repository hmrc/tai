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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain.{AddPensionProvider, IncorrectPensionProvider, Person}
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.EmploymentPensionViewModel
import uk.gov.hmrc.tai.repositories.EmploymentRepository
import uk.gov.hmrc.tai.templates.html.{EmploymentIForm, PensionProviderIForm}
import uk.gov.hmrc.tai.util.IFormConstants

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PensionProviderService @Inject()(
  iFormSubmissionService: IFormSubmissionService,
  employmentRepository: EmploymentRepository,
  auditable: Auditor)(implicit ec: ExecutionContext) {

  def addPensionProvider(nino: Nino, pensionProvider: AddPensionProvider)(implicit hc: HeaderCarrier): Future[String] =
    iFormSubmissionService.uploadIForm(
      nino,
      IFormConstants.AddPensionProviderSubmissionKey,
      "TES1",
      addPensionProviderForm(pensionProvider)) map { envelopeId =>
      Logger.info("Envelope Id for incorrect employment- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.AddPensionProviderAuditTxnName,
        detail = Map(
          "nino"                -> nino.nino,
          "envelope Id"         -> envelopeId,
          "start-date"          -> pensionProvider.startDate.toString(),
          "pensionNumber"       -> pensionProvider.pensionNumber,
          "pensionProviderName" -> pensionProvider.pensionProviderName
        )
      )

      envelopeId
    }

  private[service] def addPensionProviderForm(pensionProvider: AddPensionProvider)(implicit hc: HeaderCarrier) = {
    person: Person =>
      {
        val templateModel = EmploymentPensionViewModel(TaxYear(), person, pensionProvider)
        Future.successful(PensionProviderIForm(templateModel).toString)
      }
  }

  def incorrectPensionProvider(nino: Nino, id: Int, incorrectPensionProvider: IncorrectPensionProvider)(
    implicit hc: HeaderCarrier): Future[String] =
    iFormSubmissionService.uploadIForm(
      nino,
      IFormConstants.IncorrectPensionProviderSubmissionKey,
      "TES1",
      incorrectPensionProviderForm(nino, id, incorrectPensionProvider)
    ) map { envelopeId =>
      Logger.info("Envelope Id for incorrect pension provider- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.IncorrectPensionProviderSubmissionKey,
        detail = Map(
          "nino"                    -> nino.nino,
          "envelope Id"             -> envelopeId,
          "what-you-told-us"        -> incorrectPensionProvider.whatYouToldUs.length.toString,
          "telephoneContactAllowed" -> incorrectPensionProvider.telephoneContactAllowed,
          "telephoneNumber"         -> incorrectPensionProvider.telephoneNumber.getOrElse("")
        )
      )

      envelopeId
    }

  private[service] def incorrectPensionProviderForm(
    nino: Nino,
    id: Int,
    incorrectPensionProvider: IncorrectPensionProvider)(implicit hc: HeaderCarrier) = { person: Person =>
    {
      for {
        Right(existingEmployment) <- employmentRepository.employment(nino, id)
        templateModel = EmploymentPensionViewModel(TaxYear(), person, incorrectPensionProvider, existingEmployment)
      } yield EmploymentIForm(templateModel).toString
    }
  }
}
