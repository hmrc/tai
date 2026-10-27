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

import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.connectors.CitizenDetailsConnector
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.domain.{MimeContentType, Person}
import uk.gov.hmrc.tai.model.templates.PdfSubmission
import uk.gov.hmrc.tai.service.PdfService.PdfGeneratorRequest

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class IFormSubmissionService @Inject() (
  citizenDetailsConnector: CitizenDetailsConnector,
  pdfService: PdfService,
  upscanSubmissionService: UpscanSubmissionService
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private val dateFormat = DateTimeFormatter.ofPattern("YYYYMMdd")

  private def iformFilename(envelopeId: String, iformSubmissionKey: String) =
    s"$envelopeId-$iformSubmissionKey-${LocalDate.now().format(dateFormat)}-iform.pdf"

  def uploadIForm(
    nino: Nino,
    iformSubmissionKey: String,
    iformId: String,
    iformGenerationFunc: Person => Future[PdfGeneratorRequest[?]]
  )(implicit hc: HeaderCarrier): Future[String] =
    for {
      person   <- citizenDetailsConnector.getPerson(nino)
      formData <- iformGenerationFunc(person)
      pdf      <- pdfService.generatePdf(formData)
      envelopeId <- upscanSubmissionService.initiateSubmission(
                      DmsSubmissionMetadata.from(PdfSubmission(nino.withoutSuffix, iformId, 2))
                    )
      _ <- upscanSubmissionService
             .uploadFile(pdf, envelopeId, iformFilename(envelopeId, iformSubmissionKey), MimeContentType.ApplicationPdf)
    } yield {
      logger.info(s"Envelope Id for $iformSubmissionKey - " + envelopeId)
      envelopeId
    }
}
