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

package uk.gov.hmrc.tai.service

import cats.data.EitherT
import com.google.inject.{Inject, Singleton}

import java.time.LocalDate
import play.api.Logger
import play.api.http.Status.NOT_FOUND
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{EmploymentDetailsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.domain._
import uk.gov.hmrc.tai.model.domain.formatters.EmploymentHodFormatters
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, PdfSubmission}
import uk.gov.hmrc.tai.repositories.deprecated.PersonRepository
import uk.gov.hmrc.tai.templates.html.EmploymentIForm
import uk.gov.hmrc.tai.templates.xml.PdfSubmissionMetadata
import uk.gov.hmrc.tai.util.IFormConstants

import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmploymentService @Inject() (
  employmentDetailsConnector: EmploymentDetailsConnector,
  rtiConnector: RtiConnector,
  employmentBuilder: EmploymentBuilder,
  personRepository: PersonRepository,
  iFormSubmissionService: IFormSubmissionService,
  fileUploadService: FileUploadService,
  pdfService: PdfService,
  auditable: Auditor
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private val EndEmploymentAuditRequest = "EndEmploymentRequest"

  private val dateFormat = DateTimeFormatter.ofPattern("YYYYMMdd")

  private def endEmploymentFileName(envelopeId: String) =
    s"$envelopeId-EndEmployment-${LocalDate.now().format(dateFormat)}-iform.pdf"

  private def endEmploymentMetaDataName(envelopeId: String) =
    s"$envelopeId-EndEmployment-${LocalDate.now().format(dateFormat)}-metadata.xml"

  private def addEmploymentFileName(envelopeId: String) =
    s"$envelopeId-AddEmployment-${LocalDate.now().format(dateFormat)}-iform.pdf"

  private def addEmploymentMetaDataName(envelopeId: String) =
    s"$envelopeId-AddEmployment-${LocalDate.now().format(dateFormat)}-metadata.xml"

  def employmentsAsEitherT(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Employments] = {
    val employmentsCollectionEitherT =
      employmentDetailsConnector.getEmploymentDetailsAsEitherT(nino, taxYear.year).map { hodResponse =>
        hodResponse.body
          .as[EmploymentCollection](EmploymentHodFormatters.employmentCollectionHodReads)
          .copy(etag = hodResponse.etag)
      }

    for {
      employmentsCollection <- employmentsCollectionEitherT
      accounts <- rtiConnector.getPaymentsForYear(nino, taxYear).transform {
                    case Right(accounts: Seq[AnnualAccount]) => Right(accounts)
                    case Left(_)                             => Right(Seq.empty)
                  }
    } yield employmentBuilder.combineAccountsWithEmployments(employmentsCollection.employments, accounts, nino, taxYear)
  }

  def employmentAsEitherT(nino: Nino, id: Int)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Employment] =
    employmentsAsEitherT(nino, TaxYear()).transform {
      case Right(employments) =>
        employments.employmentById(id) match {
          case Some(employment) => Right(employment)
          case None =>
            val sequenceNumbers = employments.sequenceNumbers.mkString(", ")
            logger.warn(s"employment id: $id not found in employment sequence numbers: $sequenceNumbers")
            Left(UpstreamErrorResponse("Not found", NOT_FOUND))
        }
      case Left(error) => Left(error)
    }

  def ninoWithoutSuffix(nino: Nino): String = nino.nino.take(8)

  def endEmployment(nino: Nino, id: Int, endEmployment: EndEmployment)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, String] =
    for {
      person <- EitherT[Future, UpstreamErrorResponse, Person](personRepository.getPerson(nino).map(Right(_)))
      existingEmployment <- employmentAsEitherT(nino, id)
      templateModel = EmploymentPensionViewModel(TaxYear(), person, endEmployment, existingEmployment)
      endEmploymentHtml = EmploymentIForm(templateModel).toString
      pdf <-
        EitherT[Future, UpstreamErrorResponse, Array[Byte]](pdfService.generatePdf(endEmploymentHtml).map(Right(_)))
      envelopeId <- EitherT[Future, UpstreamErrorResponse, String](fileUploadService.createEnvelope().map(Right(_)))
      endEmploymentMetadata = PdfSubmissionMetadata(PdfSubmission(ninoWithoutSuffix(nino), "TES1", 2))
                                .toString()
                                .getBytes
      _ <- EitherT[Future, UpstreamErrorResponse, HttpResponse](
             fileUploadService
               .uploadFile(pdf, envelopeId, endEmploymentFileName(envelopeId), MimeContentType.ApplicationPdf)
               .map(Right(_))
           )
      _ <- EitherT[Future, UpstreamErrorResponse, HttpResponse](
             fileUploadService
               .uploadFile(
                 endEmploymentMetadata,
                 envelopeId,
                 endEmploymentMetaDataName(envelopeId),
                 MimeContentType.ApplicationXml
               )
               .map(Right(_))
           )
    } yield {
      logger.info("Envelope Id for end employment- " + envelopeId)

      auditable.sendDataEvent(
        EndEmploymentAuditRequest,
        detail = Map(
          "nino"        -> nino.nino,
          "envelope Id" -> envelopeId,
          "end-date"    -> endEmployment.endDate.toString
        )
      )

      envelopeId
    }

  def addEmployment(nino: Nino, employment: AddEmployment)(implicit hc: HeaderCarrier): Future[String] =
    for {
      person <- personRepository.getPerson(nino)
      templateModel = EmploymentPensionViewModel(TaxYear(), person, employment)
      addEmploymentHtml = EmploymentIForm(templateModel).toString
      pdf        <- pdfService.generatePdf(addEmploymentHtml)
      envelopeId <- fileUploadService.createEnvelope()
      addEmploymentMetadata = PdfSubmissionMetadata(PdfSubmission(ninoWithoutSuffix(nino), "TES1", 2))
                                .toString()
                                .getBytes
      _ <- fileUploadService
             .uploadFile(pdf, envelopeId, addEmploymentFileName(envelopeId), MimeContentType.ApplicationPdf)
      _ <- fileUploadService.uploadFile(
             addEmploymentMetadata,
             envelopeId,
             addEmploymentMetaDataName(envelopeId),
             MimeContentType.ApplicationXml
           )
    } yield {
      logger.info("Envelope Id for add employment- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.AddEmploymentAuditTxnName,
        detail = Map(
          "nino"         -> nino.nino,
          "envelope Id"  -> envelopeId,
          "start-date"   -> employment.startDate.toString(),
          "payrollNo"    -> employment.payrollNumber,
          "employerName" -> employment.employerName
        )
      )

      envelopeId
    }

  def incorrectEmployment(nino: Nino, id: Int, incorrectEmployment: IncorrectEmployment)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): Future[String] =
    iFormSubmissionService.uploadIForm(
      nino,
      IFormConstants.IncorrectEmploymentSubmissionKey,
      "TES1",
      (person: Person) =>
        {
          for {
            existingEmployment <- employmentAsEitherT(nino, id)
            templateModel = EmploymentPensionViewModel(TaxYear(), person, incorrectEmployment, existingEmployment)
          } yield EmploymentIForm(templateModel).toString
        }.value.map {
          case Right(result) => result
          case Left(error)   => throw error
        }
    ) map { envelopeId =>
      logger.info("Envelope Id for incorrect employment- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.IncorrectEmploymentAuditTxnName,
        detail = Map(
          "nino"                    -> nino.nino,
          "envelope Id"             -> envelopeId,
          "what-you-told-us"        -> incorrectEmployment.whatYouToldUs.length.toString,
          "telephoneContactAllowed" -> incorrectEmployment.telephoneContactAllowed,
          "telephoneNumber"         -> incorrectEmployment.telephoneNumber.getOrElse("")
        )
      )

      envelopeId
    }

  def updatePreviousYearIncome(nino: Nino, year: TaxYear, incorrectEmployment: IncorrectEmployment)(implicit
    hc: HeaderCarrier
  ): Future[String] =
    iFormSubmissionService.uploadIForm(
      nino,
      IFormConstants.UpdatePreviousYearIncomeSubmissionKey,
      "TES1",
      (person: Person) =>
        Future.successful(EmploymentIForm(EmploymentPensionViewModel(year, person, incorrectEmployment)).toString)
    ) map { envelopeId =>
      logger.info("Envelope Id for updatePreviousYearIncome- " + envelopeId)

      auditable.sendDataEvent(
        transactionName = IFormConstants.UpdatePreviousYearIncomeAuditTxnName,
        detail = Map(
          "nino"                    -> nino.nino,
          "envelope Id"             -> envelopeId,
          "taxYear"                 -> year.year.toString,
          "what-you-told-us"        -> incorrectEmployment.whatYouToldUs.length.toString,
          "telephoneContactAllowed" -> incorrectEmployment.telephoneContactAllowed,
          "telephoneNumber"         -> incorrectEmployment.telephoneNumber.getOrElse("")
        )
      )

      envelopeId
    }
}
