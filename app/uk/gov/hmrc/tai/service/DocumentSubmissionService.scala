/*
 * Copyright 2026 HM Revenue & Customs
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.tai.model.admin.FeatureFlagNameTai.UpscanSubmissionToggle
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.templates.xml.PdfSubmissionMetadata

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DocumentSubmissionService @Inject() (
  featureFlagService: FeatureFlagService,
  fileUploadService: FileUploadService,
  upscanSubmissionService: UpscanSubmissionService
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private val dateFormat = DateTimeFormatter.ofPattern("YYYYMMdd")

  private def fileName(submissionId: String, submissionKey: String, suffix: String) =
    s"$submissionId-$submissionKey-${LocalDate.now().format(dateFormat)}-$suffix"

  def submit(pdf: Array[Byte], submissionKey: String, pdfSubmission: PdfSubmission)(implicit
    hc: HeaderCarrier
  ): Future[String] =
    useUpscan.flatMap { upscanEnabled =>
      if (upscanEnabled) {
        submitViaUpscan(pdf, submissionKey, pdfSubmission)
      } else {
        submitViaFileUpload(pdf, submissionKey, pdfSubmission)
      }
    }

  private def useUpscan: Future[Boolean] =
    featureFlagService
      .getAsEitherT(UpscanSubmissionToggle)
      .value
      .map(_.fold(_ => false, _.isEnabled))
      .recover { case ex: Exception =>
        logger.warn(s"Could not read ${UpscanSubmissionToggle.name}, using File Upload Service: ${ex.getMessage}")
        false
      }

  private def submitViaFileUpload(pdf: Array[Byte], submissionKey: String, pdfSubmission: PdfSubmission)(implicit
    hc: HeaderCarrier
  ): Future[String] =
    for {
      envelopeId <- fileUploadService.createEnvelope()
      metadata = PdfSubmissionMetadata(pdfSubmission).toString().getBytes
      _ <- fileUploadService.uploadFile(
             pdf,
             envelopeId,
             fileName(envelopeId, submissionKey, "iform.pdf"),
             MimeContentType.ApplicationPdf
           )
      _ <- fileUploadService.uploadFile(
             metadata,
             envelopeId,
             fileName(envelopeId, submissionKey, "metadata.xml"),
             MimeContentType.ApplicationXml
           )
    } yield envelopeId

  private def submitViaUpscan(pdf: Array[Byte], submissionKey: String, pdfSubmission: PdfSubmission)(implicit
    hc: HeaderCarrier
  ): Future[String] =
    for {
      submissionId <- upscanSubmissionService.initiateSubmission(DmsSubmissionMetadata.from(pdfSubmission))
      _ <- upscanSubmissionService.uploadFile(
             pdf,
             submissionId,
             fileName(submissionId, submissionKey, "iform.pdf"),
             MimeContentType.ApplicationPdf
           )
    } yield submissionId
}
