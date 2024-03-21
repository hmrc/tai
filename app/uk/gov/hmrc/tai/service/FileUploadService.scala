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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.ws.ahc.AhcWSClient
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.FileUploadConnector
import uk.gov.hmrc.tai.model.FileUploadCallback
import uk.gov.hmrc.tai.model.domain.MimeContentType

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadService @Inject()(fileUploadConnector: FileUploadConnector, auditor: Auditor)(
  implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private val FileUploadSuccessStatus = "AVAILABLE"
  private val FileUploadErrorStatus = "ERROR"
  private val FileUploadOpenStatus = "OPEN"
  private val FileUploadSuccessAudit = "FileUploadSuccess"
  private val FileUploadFailureAudit = "FileUploadFailure"

  def createEnvelope()(implicit hc: HeaderCarrier): Future[String] =
    fileUploadConnector.createEnvelope

  def envelopeStatus(envelopeId: String): Future[EnvelopeStatus] =
    fileUploadConnector.envelope(envelopeId) map {
      case Some(envelopeSummary) =>
        if (envelopeSummary.status != FileUploadOpenStatus) {
          logger.warn(s"Multiple Callback received for envelope-id $envelopeId (${envelopeSummary.status})")
          Open
        } else if (envelopeSummary.files.size == 2 && envelopeSummary.files.forall(_.status == FileUploadSuccessStatus)) {
          Closed
        } else {
          Open
        }
      case None => Open
    }

  def uploadFile(data: Array[Byte], envelopeId: String, fileName: String, contentType: MimeContentType)(
    implicit hc: HeaderCarrier): Future[HttpResponse] = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = Materializer(system)

    val ahcWSClient: AhcWSClient = AhcWSClient()
    fileUploadConnector
      .uploadFile(data, fileName, contentType, envelopeId, removeExtension(fileName), ahcWSClient)
      .andThen { case _ => ahcWSClient.close() }
      .andThen { case _ => system.terminate() }
  }

  def closeEnvelope(envelopeId: String)(implicit hc: HeaderCarrier): Future[String] =
    fileUploadConnector.closeEnvelope(envelopeId)

  def fileUploadCallback(details: FileUploadCallback)(implicit hc: HeaderCarrier): Future[EnvelopeStatus] =
    if (details.status == FileUploadSuccessStatus) {
      callback(details)
    } else if (details.status == FileUploadErrorStatus) {

      auditor.sendDataEvent(FileUploadFailureAudit, detail = details.toMap)

      details.reason match {
        case Some(reason) => logger.error(s"File upload failed: ${details.status} $reason")
        case None         => logger.error(s"File upload failed for an unknown reason: ${details.status}")
      }

      Future.successful(Open)
    } else {
      Future.successful(Open)
    }

  private def callback(details: FileUploadCallback)(implicit hc: HeaderCarrier) =
    envelopeStatus(details.envelopeId) map {
      case Closed =>
        closeEnvelope(details.envelopeId)

        auditor.sendDataEvent(FileUploadSuccessAudit, detail = details.toMap)

        Closed
      case Open => Open
      case _ => throw new RuntimeException("Invalid EnvelopeStatus")
    }

  private def removeExtension(fileName: String): String = fileName.split("\\.").head

}

trait EnvelopeStatus
case object Closed extends EnvelopeStatus
case object Open extends EnvelopeStatus
