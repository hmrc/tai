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
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.connectors.{DmsSubmissionConnector, UpscanConnector}
import uk.gov.hmrc.tai.model.dms.DmsSubmissionMetadata
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.upscan.{UpscanCallback, UpscanFileStatus, UpscanSubmission, UpscanUploadedFile}
import uk.gov.hmrc.tai.repositories.UpscanSubmissionRepository

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanSubmissionService @Inject() (
  upscanConnector: UpscanConnector,
  dmsSubmissionConnector: DmsSubmissionConnector,
  submissionRepository: UpscanSubmissionRepository,
  auditor: Auditor
)(implicit ec: ExecutionContext) {

  private val logger: Logger = Logger(getClass.getName)

  private val UpscanUploadSuccessAudit = "FileUploadSuccess"
  private val UpscanUploadFailureAudit = "FileUploadFailure"

  def initiateSubmission(dmsMetadata: DmsSubmissionMetadata)(implicit hc: HeaderCarrier): Future[String] = {
    val submissionId = UUID.randomUUID().toString
    submissionRepository.create(submissionId, Seq.empty, Some(dmsMetadata)).map(_ => submissionId)
  }

  def uploadFile(data: Array[Byte], submissionId: String, fileName: String, contentType: MimeContentType)(implicit
    hc: HeaderCarrier
  ): Future[HttpResponse] =
    for {
      initiateResponse <- upscanConnector.initiate
      _ <- submissionRepository.addFile(
             submissionId,
             UpscanUploadedFile(initiateResponse.reference, fileName)
           )
      response <- upscanConnector.upload(initiateResponse.uploadRequest, data, fileName, contentType)
    } yield response

  def handleCallback(callback: UpscanCallback)(implicit hc: HeaderCarrier): Future[Unit] =
    callback.fileStatus match {
      case UpscanFileStatus.Ready  => handleReady(callback)
      case UpscanFileStatus.Failed => handleFailed(callback)
      case other =>
        logger.warn(
          s"Received Upscan callback with unrecognised fileStatus '$other' for reference ${callback.reference}"
        )
        Future.unit
    }

  private def handleReady(callback: UpscanCallback)(implicit hc: HeaderCarrier): Future[Unit] =
    submissionRepository
      .updateFileStatus(callback.reference, UpscanFileStatus.Ready, callback.downloadUrl, None)
      .flatMap {
        case Some(submission) =>
          callback.downloadUrl match {
            case Some(_) =>
              if (submission.isComplete) completeSubmission(submission) else Future.unit
            case None =>
              recordFailure(
                callback.reference,
                Some("Upscan READY callback was missing downloadUrl, so the file could not be sent to DMS")
              )
          }
        case None =>
          logger.warn(s"Received Upscan READY callback for unknown reference: ${callback.reference}")
          Future.unit
      }

  private def handleFailed(callback: UpscanCallback)(implicit hc: HeaderCarrier): Future[Unit] =
    recordFailure(callback.reference, callback.failureDetails.map(fd => s"${fd.failureReason}: ${fd.message}"))

  private def recordFailure(reference: String, reason: Option[String])(implicit hc: HeaderCarrier): Future[Unit] = {
    logger.error(s"Upscan file upload failed for reference $reference: ${reason.getOrElse("unknown reason")}")

    auditor.sendDataEvent(
      UpscanUploadFailureAudit,
      detail = Map(
        "reference" -> reference,
        "status"    -> UpscanFileStatus.Failed,
        "reason"    -> reason.getOrElse("")
      )
    )

    submissionRepository
      .updateFileStatus(reference, UpscanFileStatus.Failed, None, reason)
      .map(_ => ())
  }

  private def completeSubmission(submission: UpscanSubmission)(implicit hc: HeaderCarrier): Future[Unit] =
    submission.dmsMetadata match {
      case Some(metadata) =>
        forwardToDms(submission, metadata).flatMap {
          case Right(dmsSubmissionId) =>
            auditSuccess(submission, Some(dmsSubmissionId))
            Future.unit
          case Left(reason) =>
            recordFailure(submission.files.headOption.map(_.reference).getOrElse(submission.submissionId), Some(reason))
        }
      case None =>
        logger.warn(s"Upscan submission ${submission.submissionId} has no DMS metadata, so it was not sent to DMS")
        auditSuccess(submission, None)
        Future.unit
    }

  private def forwardToDms(submission: UpscanSubmission, metadata: DmsSubmissionMetadata)(implicit
    hc: HeaderCarrier
  ): Future[Either[String, String]] =
    submission.files.headOption.flatMap(file => file.downloadUrl.map(url => (file, url))) match {
      case Some((file, downloadUrl)) =>
        upscanConnector
          .download(downloadUrl)
          .flatMap(pdf => dmsSubmissionConnector.submit(pdf, file.fileName, metadata))
          .map[Either[String, String]](response => Right(response.id))
          .recover { case ex: Exception => Left(s"Sending the file to DMS failed: ${ex.getMessage}") }
      case None =>
        Future.successful(Left("No Upscan download URL was available, so the file could not be sent to DMS"))
    }

  private def auditSuccess(submission: UpscanSubmission, dmsSubmissionId: Option[String])(implicit
    hc: HeaderCarrier
  ): Unit = {
    logger.info(s"Upscan submission complete for submissionId: ${submission.submissionId}")

    auditor.sendDataEvent(
      UpscanUploadSuccessAudit,
      detail = Map(
        "envelopId"       -> submission.submissionId,
        "status"          -> UpscanFileStatus.Ready,
        "file-Id"         -> submission.files.map(_.reference).mkString(","),
        "dmsSubmissionId" -> dmsSubmissionId.getOrElse("")
      )
    )
  }
}
