/*
 * Copyright 2025 HM Revenue & Customs
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
import org.apache.pekko.actor.{ActorSystem, Scheduler}
import org.apache.pekko.pattern.retry
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, writeableOf_JsValue}
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.tai.config.FileUploadConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.enums.APITypes.*
import uk.gov.hmrc.tai.model.fileupload.EnvelopeSummary
import uk.gov.hmrc.tai.model.fileupload.formatters.FileUploadFormatters

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadConnector @Inject() (
  metrics: Metrics,
  httpClientV2: HttpClientV2,
  wsClient: WSClient,
  urls: FileUploadUrls,
  config: FileUploadConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  implicit val scheduler: Scheduler = ActorSystem().scheduler

  private def routingRequest(envelopeId: String): JsValue =
    Json.obj("envelopeId" -> envelopeId, "application" -> "TAI", "destination" -> "DMS")

  def createEnvelope(implicit hc: HeaderCarrier): Future[String] = {
    val envelopeBody: JsValue = Json.obj("callbackUrl" -> config.callbackUrl)

    val timerContext = metrics.startTimer(FusCreateEnvelope)
    val url = urls.envelopesUrl

    val futureResponse = httpClientV2
      .post(url"$url")
      .withBody(envelopeBody)
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)

    futureResponse
      .map {
        case Right(response) =>
          timerContext.stop()
          if (response.status == CREATED) {
            metrics.incrementSuccessCounter(FusCreateEnvelope)
            envelopeId(response).getOrElse {
              throw new RuntimeException("No envelope id returned by file upload service")
            }
          } else {
            throw new RuntimeException("File upload envelope creation failed")
          }
        case Left(error) =>
          timerContext.stop()
          throw new RuntimeException("File upload envelope creation failed")
      }
      .recover { case _: Exception =>
        metrics.incrementFailedCounter(FusCreateEnvelope)
        throw new RuntimeException("File upload envelope creation failed")
      }
  }

  def uploadFile(
    byteArray: Array[Byte],
    fileName: String,
    contentType: MimeContentType,
    envelopeId: String,
    fileId: String,
    awsClient: AhcWSClient
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url: String = urls.fileUrl(envelopeId, fileId)
    envelope(envelopeId)
      .flatMap {
        case Some(es) if es.isOpen =>
          uploadFileCall(byteArray, fileName, contentType, url, awsClient)
        case Some(es) if !es.isOpen =>
          Future.failed(new RuntimeException("Incorrect Envelope State"))
        case _ =>
          Future.failed(new RuntimeException("Could Not Read Envelope State"))
      }
      .recoverWith { case _: RuntimeException =>
        metrics.incrementFailedCounter(FusUploadFile)
        Future.failed(new RuntimeException("Unable to find Envelope"))
      }
  }

  private def uploadFileCall(
    byteArray: Array[Byte],
    fileName: String,
    contentType: MimeContentType,
    url: String,
    ahcWSClient: WSClient
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(FusUploadFile)
    val multipartFormData = Source(
      FilePart("attachment", fileName, Some(contentType.description), Source(ByteString(byteArray) :: Nil)) :: DataPart(
        "",
        ""
      ) :: Nil
    )

    ahcWSClient
      .url(url)
      .addHttpHeaders(
        "CSRF-token"           -> "nocheck",
        HeaderNames.xSessionId -> hc.sessionId.fold("-")(_.value),
        HeaderNames.xRequestId -> hc.requestId.fold("-")(_.value)
      )
      .post(multipartFormData)
      .map { response =>
        timerContext.stop()

        if (response.status == OK) {
          metrics.incrementSuccessCounter(FusUploadFile)
          ahcWSClient.close()
          HttpResponse(response.status, "")
        } else {
          ahcWSClient.close()
          throw new RuntimeException("File upload failed")
        }
      }
  }

  def closeEnvelope(envId: String)(implicit hc: HeaderCarrier): Future[String] = {
    val timerContext = metrics.startTimer(FusCloseEnvelope)
    val url = urls.routingUrl
    val futureResponse = httpClientV2
      .post(url"$url")
      .withBody(routingRequest(envId))
      .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)

    futureResponse
      .map {
        case Right(response) =>
          timerContext.stop()
          if (response.status == CREATED) {
            metrics.incrementSuccessCounter(FusCloseEnvelope)
            envelopeId(response).getOrElse {
              throw new RuntimeException("No envelope id returned by file upload service")
            }
          } else {
            throw new RuntimeException("File upload envelope routing request failed")
          }
        case Left(error) =>
          timerContext.stop()
          throw new RuntimeException("File upload envelope routing request failed")
      }
      .recover { case _: Exception =>
        metrics.incrementFailedCounter(FusCloseEnvelope)
        throw new RuntimeException("File upload envelope routing request failed")
      }
  }

  def envelope(envId: String): Future[Option[EnvelopeSummary]] = {
    def internal: Future[Option[EnvelopeSummary]] =
      wsClient.url(s"${urls.envelopesUrl}/$envId").get() flatMap { response =>
        response.status match {
          case OK =>
            Future.successful(response.json.asOpt[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads))
          case NOT_FOUND =>
            Future.failed(new RuntimeException(s"Could not find envelope with id: $envId"))
          case _ =>
            Future.successful(None)
        }
      }

    retry(() => internal, config.maxAttempts - 1, config.intervalMs.milliseconds)
  }

  private def envelopeId(response: HttpResponse): Option[String] =
    response.header("Location").map(path => path.split("/").reverse.head)
}
