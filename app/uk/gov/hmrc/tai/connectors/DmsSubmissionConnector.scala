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

package uk.gov.hmrc.tai.connectors

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.ws.WSClient
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.tai.config.DmsSubmissionConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.dms.{DmsSubmissionMetadata, DmsSubmissionResponse}
import uk.gov.hmrc.tai.model.enums.APITypes.*

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DmsSubmissionConnector @Inject() (
  metrics: Metrics,
  wsClient: WSClient,
  urls: DmsSubmissionUrls,
  config: DmsSubmissionConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def submit(pdf: Array[Byte], fileName: String, metadata: DmsSubmissionMetadata)(implicit
    hc: HeaderCarrier
  ): Future[DmsSubmissionResponse] = {
    val timerContext = metrics.startTimer(DmsSubmission)

    val timeOfReceipt = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(metadata.timeOfReceipt.atOffset(ZoneOffset.UTC))

    val dataParts: Seq[MultipartFormData.Part[Source[ByteString, ?]]] = Seq(
      "callbackUrl"                 -> config.callbackUrl,
      "metadata.store"              -> "true",
      "metadata.source"             -> metadata.source,
      "metadata.timeOfReceipt"      -> timeOfReceipt,
      "metadata.formId"             -> metadata.formId,
      "metadata.customerId"         -> metadata.customerId,
      "metadata.classificationType" -> metadata.classificationType,
      "metadata.businessArea"       -> metadata.businessArea
    ).map { case (key, value) => DataPart(key, value) }

    val filePart: MultipartFormData.Part[Source[ByteString, ?]] =
      FilePart("form", fileName, Some("application/octet-stream"), Source.single(ByteString(pdf)))

    wsClient
      .url(urls.submitUrl)
      .addHttpHeaders("Authorization" -> config.authToken)
      .post(Source(dataParts :+ filePart))
      .map { response =>
        timerContext.stop()
        if (response.status == ACCEPTED) {
          metrics.incrementSuccessCounter(DmsSubmission)
          response.json.as[DmsSubmissionResponse]
        } else {
          throw new RuntimeException(
            s"dms-submission request failed with status ${response.status}: ${response.body}"
          )
        }
      }
      .recoverWith { case ex: Exception =>
        metrics.incrementFailedCounter(DmsSubmission)
        Future.failed(ex)
      }
  }
}
