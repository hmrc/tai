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
import uk.gov.hmrc.tai.audit.Auditor
import uk.gov.hmrc.tai.model.dms.{DmsCallback, DmsSubmissionStatus}

import scala.concurrent.Future

@Singleton
class DmsSubmissionService @Inject() (auditor: Auditor) {

  private val logger: Logger = Logger(getClass.getName)

  private val DmsSubmissionProcessedAudit = "DmsSubmissionProcessed"
  private val DmsSubmissionFailedAudit = "DmsSubmissionFailed"

  def handleCallback(callback: DmsCallback)(implicit hc: HeaderCarrier): Future[Unit] = {
    callback.status match {
      case DmsSubmissionStatus.Processed =>
        logger.info(s"dms-submission processed submission ${callback.id}")
        auditor.sendDataEvent(
          DmsSubmissionProcessedAudit,
          detail = Map("dmsSubmissionId" -> callback.id)
        )
      case DmsSubmissionStatus.Failed =>
        logger.error(
          s"dms-submission failed for submission ${callback.id}: " +
            s"${callback.failureType.getOrElse("unknown type")} - ${callback.failureReason.getOrElse("unknown reason")}"
        )
        auditor.sendDataEvent(
          DmsSubmissionFailedAudit,
          detail = Map(
            "dmsSubmissionId" -> callback.id,
            "failureType"     -> callback.failureType.getOrElse(""),
            "failureReason"   -> callback.failureReason.getOrElse("")
          )
        )
      case other =>
        logger.warn(s"Received dms-submission callback with unrecognised status '$other' for ${callback.id}")
    }
    Future.unit
  }
}
