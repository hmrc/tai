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

package uk.gov.hmrc.tai.service

import com.google.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, PdfSubmission}
import uk.gov.hmrc.tai.repositories.{EmploymentRepository, PersonRepository}
import uk.gov.hmrc.tai.templates.html.EmploymentIForm
import uk.gov.hmrc.tai.templates.xml.PdfSubmissionMetadata
import uk.gov.hmrc.tai.util.IFormConstants

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

@Singleton
class EmploymentService @Inject()(employmentRepository: EmploymentRepository,
                                  personRepository: PersonRepository,
                                  iFormSubmissionService: IFormSubmissionService,
                                  fileUploadService: FileUploadService,
                                  pdfService: PdfService,
                                  auditable: Auditor) {

  private val EndEmploymentAuditRequest = "EndEmploymentRequest"

  private def endEmploymentFileName(envelopeId: String) = s"$envelopeId-EndEmployment-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"

  private def endEmploymentMetaDataName(envelopeId: String) = s"$envelopeId-EndEmployment-${LocalDate.now().toString("YYYYMMdd")}-metadata.xml"

  private def addEmploymentFileName(envelopeId: String) = s"$envelopeId-AddEmployment-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"

  private def addEmploymentMetaDataName(envelopeId: String) = s"$envelopeId-AddEmployment-${LocalDate.now().toString("YYYYMMdd")}-metadata.xml"

  def employments(nino: Nino, year: TaxYear)(implicit hc: HeaderCarrier): Future[Seq[Employment]] = employmentRepository.employmentsForYear(nino, year)

  def employment(nino: Nino, id: Int)(implicit hc: HeaderCarrier): Future[Option[Employment]] = employmentRepository.employment(nino, id)

  def ninoWithoutSuffix(nino: Nino): String = nino.nino.take(8)

  def endEmployment(nino: Nino, id: Int, endEmployment: EndEmployment)(implicit hc: HeaderCarrier): Future[String] = {

    for {
      person <- personRepository.getPerson(nino)
      Some(existingEmployment) <- employment(nino, id)
      templateModel = EmploymentPensionViewModel(TaxYear(), person, endEmployment, existingEmployment)
      endEmploymentHtml = EmploymentIForm(templateModel).toString
      pdf <- pdfService.generatePdf(endEmploymentHtml)
      envelopeId <- fileUploadService.createEnvelope()
      endEmploymentMetadata = PdfSubmissionMetadata(PdfSubmission(ninoWithoutSuffix(nino), "TES1", 2)).toString().getBytes
      _ <- fileUploadService.uploadFile(pdf, envelopeId, endEmploymentFileName(envelopeId), MimeContentType.ApplicationPdf)
      _ <- fileUploadService.uploadFile(endEmploymentMetadata, envelopeId, endEmploymentMetaDataName(envelopeId), MimeContentType.ApplicationXml)
    } yield {
      Logger.info("Envelope Id for end employment- " + envelopeId)

      auditable.sendDataEvent(EndEmploymentAuditRequest, detail = Map(
        "nino" -> nino.nino,
        "envelope Id" -> envelopeId,
        "end-date" -> endEmployment.endDate.toString()
      ))

      envelopeId
    }
  }

  def addEmployment(nino: Nino, employment: AddEmployment)(implicit hc: HeaderCarrier): Future[String] = {

    for {
      person <- personRepository.getPerson(nino)
      templateModel = EmploymentPensionViewModel(TaxYear(), person, employment)
      addEmploymentHtml = EmploymentIForm(templateModel).toString
      pdf <- pdfService.generatePdf(addEmploymentHtml)
      envelopeId <- fileUploadService.createEnvelope()
      addEmploymentMetadata = PdfSubmissionMetadata(PdfSubmission(ninoWithoutSuffix(nino), "TES1", 2)).toString().getBytes
      _ <- fileUploadService.uploadFile(pdf, envelopeId, addEmploymentFileName(envelopeId), MimeContentType.ApplicationPdf)
      _ <- fileUploadService.uploadFile(addEmploymentMetadata, envelopeId, addEmploymentMetaDataName(envelopeId), MimeContentType.ApplicationXml)
    } yield {
      Logger.info("Envelope Id for add employment- " + envelopeId)

      auditable.sendDataEvent(transactionName = IFormConstants.AddEmploymentAuditTxnName, detail = Map(
        "nino" -> nino.nino,
        "envelope Id" -> envelopeId,
        "start-date" -> employment.startDate.toString(),
        "payrollNo" -> employment.payrollNumber,
        "employerName" -> employment.employerName
      ))

      envelopeId
    }
  }

  def incorrectEmployment(nino: Nino, id: Int, incorrectEmployment: IncorrectEmployment)(implicit hc: HeaderCarrier): Future[String] = {
    iFormSubmissionService.uploadIForm(nino, IFormConstants.IncorrectEmploymentSubmissionKey, "TES1", (person: Person) => {
      for {
        Some(existingEmployment) <- employment(nino, id)
        templateModel = EmploymentPensionViewModel(TaxYear(), person, incorrectEmployment, existingEmployment)
      } yield EmploymentIForm(templateModel).toString
    }) map { envelopeId =>
      Logger.info("Envelope Id for incorrect employment- " + envelopeId)

      auditable.sendDataEvent(transactionName = IFormConstants.IncorrectEmploymentAuditTxnName, detail = Map(
        "nino" -> nino.nino,
        "envelope Id" -> envelopeId,
        "what-you-told-us" -> incorrectEmployment.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> incorrectEmployment.telephoneContactAllowed,
        "telephoneNumber" -> incorrectEmployment.telephoneNumber.getOrElse("")
      ))

      envelopeId
    }
  }

  def updatePreviousYearIncome(nino: Nino, year: TaxYear, incorrectEmployment: IncorrectEmployment)(implicit hc: HeaderCarrier): Future[String] = {
    iFormSubmissionService.uploadIForm(nino, IFormConstants.UpdatePreviousYearIncomeSubmissionKey, "TES1", (person: Person) => {
      Future.successful(EmploymentIForm(EmploymentPensionViewModel(year, person, incorrectEmployment)).toString)
    }) map { envelopeId =>
      Logger.info("Envelope Id for updatePreviousYearIncome- " + envelopeId)

      auditable.sendDataEvent(transactionName = IFormConstants.UpdatePreviousYearIncomeAuditTxnName, detail = Map(
        "nino" -> nino.nino,
        "envelope Id" -> envelopeId,
        "taxYear" -> year.year.toString,
        "what-you-told-us" -> incorrectEmployment.whatYouToldUs.length.toString,
        "telephoneContactAllowed" -> incorrectEmployment.telephoneContactAllowed,
        "telephoneNumber" -> incorrectEmployment.telephoneNumber.getOrElse("")
      ))

      envelopeId
    }
  }
}