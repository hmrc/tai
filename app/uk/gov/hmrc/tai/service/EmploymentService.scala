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
import play.api.Logger
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.Reads
import play.api.mvc.Request
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{EmploymentDetailsConnector, RtiConnector}
import uk.gov.hmrc.tai.model.admin.HipToggleEmploymentDetails
import uk.gov.hmrc.tai.model.api.EmploymentCollection
import uk.gov.hmrc.tai.model.api.EmploymentCollection.{employmentCollectionHodReadsHIP, employmentCollectionHodReadsNPS}
import uk.gov.hmrc.tai.model.domain.*
import uk.gov.hmrc.tai.model.tai.TaxYear
import uk.gov.hmrc.tai.model.templates.{EmploymentPensionViewModel, PdfSubmission}
import uk.gov.hmrc.tai.repositories.deprecated.PersonRepository
import uk.gov.hmrc.tai.templates.html.EmploymentIForm
import uk.gov.hmrc.tai.templates.xml.PdfSubmissionMetadata
import uk.gov.hmrc.tai.util.IFormConstants

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.collection.immutable.Seq
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
  auditable: Auditor,
  featureFlagService: FeatureFlagService
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

  private def fetchEmploymentsCollection(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, EmploymentCollection] = {
    val futureReads: Future[Reads[EmploymentCollection]] =
      featureFlagService.get(HipToggleEmploymentDetails).map { toggle =>
        if (toggle.isEnabled) {
          employmentCollectionHodReadsHIP
        } else {
          employmentCollectionHodReadsNPS
        }
      }

    EitherT(
      employmentDetailsConnector.getEmploymentDetailsAsEitherT(nino, taxYear.year).value.flatMap {
        case Left(e) => Future.successful(Left(e))
        case Right(hodResponse) =>
          futureReads.map { reads =>
            Right(
              hodResponse.body
                .as[EmploymentCollection](reads)
                .copy(etag = hodResponse.etag)
            )
          }
      }
    )
  }

  private def retrieveRTIPayments(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, (Seq[AnnualAccount], Boolean)] =
    EitherT(
      rtiConnector.getPaymentsForYear(nino, taxYear).value.map {
        case Right(accounts) => Right(Tuple2(accounts, false))
        case Left(e)         => Right(Tuple2(Seq.empty[AnnualAccount], true))
      }
    )

  def employmentsAsEitherT(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Employments] =
    retrieveRTIPayments(nino, taxYear).flatMap { case (rtiAnnualAccounts, rtiIsRTIException) =>
      fetchEmploymentsCollection(nino, taxYear).map { employmentsCollection =>
        employmentBuilder.combineAccountsWithEmployments(
          employmentsCollection.employments,
          rtiAnnualAccounts,
          rtiIsRTIException,
          nino,
          taxYear
        )
      }

    }

  def employmentAsEitherT(nino: Nino, id: Int)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[Employment]] =
    employmentsAsEitherT(nino, TaxYear()).map { employments =>
      employments.employmentById(id)
    }

  def employmentsWithoutRtiAsEitherT(nino: Nino, taxYear: TaxYear)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, EmploymentCollection] =
    fetchEmploymentsCollection(nino, taxYear)

  def employmentWithoutRTIAsEitherT(nino: Nino, id: Int, taxYear: TaxYear)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Option[Employment]] =
    employmentsWithoutRtiAsEitherT(nino, taxYear).map { employments =>
      employments.employmentById(id)
    }

  def ninoWithoutSuffix(nino: Nino): String = nino.nino.take(8)

  def endEmployment(nino: Nino, id: Int, endEmployment: EndEmployment)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, String] =
    EitherT(employmentAsEitherT(nino, id).value.flatMap {
      case Right(Some(existingEmployment)) =>
        for {
          person <- personRepository.getPerson(nino)
          templateModel = EmploymentPensionViewModel(TaxYear(), person, endEmployment, existingEmployment)
          endEmploymentHtml = EmploymentIForm(templateModel).toString
          pdf <-
            pdfService.generatePdf(endEmploymentHtml)
          envelopeId <- fileUploadService.createEnvelope()
          endEmploymentMetadata = PdfSubmissionMetadata(PdfSubmission(ninoWithoutSuffix(nino), "TES1", 2))
                                    .toString()
                                    .getBytes
          _ <- fileUploadService
                 .uploadFile(pdf, envelopeId, endEmploymentFileName(envelopeId), MimeContentType.ApplicationPdf)
          _ <- fileUploadService
                 .uploadFile(
                   endEmploymentMetadata,
                   envelopeId,
                   endEmploymentMetaDataName(envelopeId),
                   MimeContentType.ApplicationXml
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
          Right[UpstreamErrorResponse, String](envelopeId)
        }
      case Right(None) =>
        Future.successful(
          Left[UpstreamErrorResponse, String](
            UpstreamErrorResponse(s"employment id: $id not found in list of employments", NOT_FOUND)
          )
        )
      case Left(error) => Future.successful(Left[UpstreamErrorResponse, String](error))
    })

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
          "start-date"   -> employment.startDate.toString,
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
        employmentAsEitherT(nino, id)
          .map {
            case Some(employment) =>
              val templateModel = EmploymentPensionViewModel(TaxYear(), person, incorrectEmployment, employment)
              EmploymentIForm(templateModel).toString
            case None => throw new RuntimeException(s"employment id: $id not found in list of employments")
          }
          .foldF(Future.failed, Future.successful)
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
