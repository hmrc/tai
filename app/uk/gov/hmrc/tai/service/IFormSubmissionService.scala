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
import org.joda.time.LocalDate
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.model.domain.{MimeContentType, Person}
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.repositories.PersonRepository
import uk.gov.hmrc.tai.templates.xml.PdfSubmissionMetadata

import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext

@Singleton
class IFormSubmissionService @Inject()(
  personRepository: PersonRepository,
  pdfService: PdfService,
  fileUploadService: FileUploadService) {

  private def iformFilename(envelopeId: String, iformSubmissionKey: String) =
    s"$envelopeId-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-iform.pdf"

  private def metadataFilename(envelopeId: String, iformSubmissionKey: String) =
    s"$envelopeId-$iformSubmissionKey-${LocalDate.now().toString("YYYYMMdd")}-metadata.xml"

  def uploadIForm(
    nino: Nino,
    iformSubmissionKey: String,
    iformId: String,
    iformGenerationFunc: (Person) => Future[String])(implicit hc: HeaderCarrier): Future[String] =
    for {
      person     <- personRepository.getPerson(nino)
      formData   <- iformGenerationFunc(person)
      pdf        <- pdfService.generatePdf(formData)
      envelopeId <- fileUploadService.createEnvelope()
      metadata = PdfSubmissionMetadata(PdfSubmission(nino.withoutSuffix, iformId, 2)).toString().getBytes
      _ <- fileUploadService
            .uploadFile(pdf, envelopeId, iformFilename(envelopeId, iformSubmissionKey), MimeContentType.ApplicationPdf)
      _ <- fileUploadService.uploadFile(
            metadata,
            envelopeId,
            metadataFilename(envelopeId, iformSubmissionKey),
            MimeContentType.ApplicationXml)
    } yield {
      Logger.info(s"Envelope Id for $iformSubmissionKey - " + envelopeId)
      envelopeId
    }
}
