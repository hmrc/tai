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
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, writeableOf_JsValue}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.tai.config.UpscanConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.enums.APITypes.*
import uk.gov.hmrc.tai.model.upscan.{UpscanInitiateRequest, UpscanInitiateResponse, UpscanUploadRequest}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UpscanConnector @Inject() (
  metrics: Metrics,
  httpClientV2: HttpClientV2,
  wsClient: WSClient,
  urls: UpscanUrls,
  config: UpscanConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  def initiate(implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    val request = UpscanInitiateRequest(
      callbackUrl = config.callbackUrl,
      minimumFileSize = Some(config.minFileSizeBytes),
      maximumFileSize = Some(config.maxFileSizeBytes),
      consumingService = Some(config.consumingService)
    )

    val timerContext = metrics.startTimer(UpscanInitiate)
    val url = urls.initiateUrl

    httpClientV2
      .post(url"$url")
      .withBody(Json.toJson(request))
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
      .map {
        case Right(response) if response.status == OK =>
          timerContext.stop()
          metrics.incrementSuccessCounter(UpscanInitiate)
          response.json.as[UpscanInitiateResponse]
        case Right(response) =>
          timerContext.stop()
          throw new RuntimeException(s"Upscan initiate failed with status: ${response.status}")
        case Left(error) =>
          timerContext.stop()
          throw new RuntimeException(s"Upscan initiate failed with status: ${error.statusCode}")
      }
      .recover { case ex: Exception =>
        metrics.incrementFailedCounter(UpscanInitiate)
        throw ex
      }
  }

  def download(downloadUrl: String)(implicit hc: HeaderCarrier): Future[Array[Byte]] = {
    val timerContext = metrics.startTimer(UpscanDownload)

    wsClient
      .url(downloadUrl)
      .get()
      .map { response =>
        timerContext.stop()

        if (response.status == OK) {
          metrics.incrementSuccessCounter(UpscanDownload)
          response.bodyAsBytes.toArray
        } else {
          throw new RuntimeException(s"Upscan file download failed with status: ${response.status}")
        }
      }
      .recoverWith { case ex: Exception =>
        metrics.incrementFailedCounter(UpscanDownload)
        Future.failed(ex)
      }
  }

  def upload(
    uploadRequest: UpscanUploadRequest,
    byteArray: Array[Byte],
    fileName: String,
    contentType: MimeContentType
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(UpscanUpload)

    val dataParts: Seq[MultipartFormData.Part[Source[ByteString, ?]]] =
      uploadRequest.fields.toSeq.map { case (key, value) => DataPart(key, value) }
    val filePart: MultipartFormData.Part[Source[ByteString, ?]] =
      FilePart("file", fileName, Some(contentType.description), Source(ByteString(byteArray) :: Nil))

    val multipartFormData = Source(dataParts :+ filePart)

    wsClient
      .url(uploadRequest.href)
      .post(multipartFormData)
      .map { response =>
        timerContext.stop()

        if (response.status == OK || response.status == CREATED || response.status == NO_CONTENT) {
          metrics.incrementSuccessCounter(UpscanUpload)
          HttpResponse(response.status, "")
        } else {
          throw new RuntimeException(s"Upscan file upload failed with status: ${response.status}")
        }
      }
      .recoverWith { case ex: Exception =>
        metrics.incrementFailedCounter(UpscanUpload)
        Future.failed(ex)
      }
  }
}
