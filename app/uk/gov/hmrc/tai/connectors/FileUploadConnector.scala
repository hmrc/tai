/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.{Inject, Singleton}
import play.Logger
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import play.api.libs.ws.ahc.AhcWSClient
import play.api.mvc.MultipartFormData.{DataPart, FilePart}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.FileUploadConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.enums.APITypes._
import uk.gov.hmrc.tai.model.fileupload.EnvelopeSummary
import uk.gov.hmrc.tai.model.fileupload.formatters.FileUploadFormatters
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.pattern.Patterns.after

@Singleton
class FileUploadConnector @Inject()(
  metrics: Metrics,
  httpClient: HttpClient,
  wsClient: WSClient,
  urls: FileUploadUrls,
  config: FileUploadConfig) {
  val as = ActorSystem()

  def routingRequest(envelopeId: String): JsValue =
    Json.obj("envelopeId" -> envelopeId, "application" -> "TAI", "destination" -> "DMS")

  def createEnvelope(implicit hc: HeaderCarrier): Future[String] = {
    val envelopeBody: JsValue = Json.obj("callbackUrl" -> config.callbackUrl)

    val timerContext = metrics.startTimer(FusCreateEnvelope)
    httpClient
      .POST[JsValue, HttpResponse](urls.envelopesUrl, envelopeBody)
      .map { response =>
        timerContext.stop()

        if (response.status == CREATED) {
          metrics.incrementSuccessCounter(FusCreateEnvelope)

          envelopeId(response)
            .getOrElse {
              Logger.warn("FileUploadConnector.createEnvelope - No envelope id returned by file upload service")
              throw new RuntimeException("No envelope id returned by file upload service")
            }
        } else {
          Logger.warn(
            s"FileUploadConnector.createEnvelope - failed to create envelope with status [${response.status}]")
          throw new RuntimeException("File upload envelope creation failed")
        }
      }
      .recover {
        case _: Exception =>
          Logger.warn("FileUploadConnector.createEnvelope - call to create envelope failed")
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
    awsClient: AhcWSClient)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val url: String = urls.fileUrl(envelopeId, fileId)
    envelope(envelopeId)
      .flatMap(envelopeSummary =>
        envelopeSummary match {
          case Some(es) if es.isOpen =>
            uploadFileCall(byteArray, fileName, contentType, url, awsClient).recover {
              case _: RuntimeException =>
                Logger.warn("FileUploadConnector.uploadFile - call to upload file failed")
                throw new RuntimeException("File upload failed")
            }
          case Some(es) if !es.isOpen =>
            Logger.warn(
              s"FileUploadConnector.uploadFile - invalid envelope state for uploading file envelope: $envelopeId")
            throw new RuntimeException("Incorrect Envelope State")
          case _ =>
            Logger.warn(s"FileUploadConnector.uploadFile - could not read envelope state for envelope: $envelopeId")
            throw new RuntimeException("Could Not Read Envelope State")
      })
      .recover {
        case _: RuntimeException =>
          Logger.warn("FileUploadConnector.uploadFile - unable to find envelope")
          metrics.incrementFailedCounter(FusUploadFile)
          throw new RuntimeException("Unable to find Envelope")
      }
  }

  def uploadFileCall(
    byteArray: Array[Byte],
    fileName: String,
    contentType: MimeContentType,
    url: String,
    ahcWSClient: AhcWSClient)(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timerContext = metrics.startTimer(FusUploadFile)
    val multipartFormData = Source(
      FilePart("attachment", fileName, Some(contentType.description), Source(ByteString(byteArray) :: Nil)) :: DataPart(
        "",
        "") :: Nil)

    ahcWSClient
      .url(url)
      .withHeaders(hc.copy(otherHeaders = Seq("CSRF-token" -> "nocheck")).headers: _*)
      .post(multipartFormData)
      .map { response =>
        timerContext.stop()

        if (response.status == OK) {
          metrics.incrementSuccessCounter(FusUploadFile)
          ahcWSClient.close()
          HttpResponse(response.status)
        } else {
          Logger.warn(s"FileUploadConnector.uploadFile - failed to upload file with status [${response.status}]")
          ahcWSClient.close()
          throw new RuntimeException("File upload failed")
        }
      }
  }

  def closeEnvelope(envId: String)(implicit hc: HeaderCarrier): Future[String] = {
    val timerContext = metrics.startTimer(FusCloseEnvelope)
    httpClient
      .POST[JsValue, HttpResponse](urls.routingUrl, routingRequest(envId))
      .map { response =>
        timerContext.stop()
        if (response.status == CREATED) {
          metrics.incrementSuccessCounter(FusCloseEnvelope)
          envelopeId(response)
            .getOrElse {
              Logger.warn("FileUploadConnector.closeEnvelope - No envelope id returned by file upload service")
              throw new RuntimeException("No envelope id returned by file upload service")
            }
        } else {
          Logger.warn(s"FileUploadConnector.closeEnvelope - failed to close envelope with status [${response.status}]")
          throw new RuntimeException("File upload envelope routing request failed")
        }
      }
      .recover {
        case _: Exception =>
          Logger.warn("FileUploadConnector.closeEnvelope - call to close envelope failed")
          metrics.incrementFailedCounter(FusCloseEnvelope)
          throw new RuntimeException("File upload envelope routing request failed")
      }
  }

  def retry(envelopeId: String, firstRetryMs: Int, attempt: Int, factor: Int = 2)(
    implicit hc: HeaderCarrier): Future[Option[EnvelopeSummary]] =
    attempt match {
      case attempt: Int if attempt < config.maxAttempts =>
        val nextTry = firstRetryMs * factor
        val nextAttempt = attempt + 1

        after(nextTry.milliseconds, as.scheduler, defaultContext, Future.successful(1)).flatMap { _ =>
          envelope(envelopeId, nextTry, nextAttempt)
        }

      case _ =>
        Future.failed(
          new RuntimeException(s"[FileUploadConnector][retry] envelope[$envelopeId] failed at attempt: $attempt"))
    }

  def envelope(envId: String, nextTry: Int = config.firstRetryMilliseconds, attempt: Int = 1)(
    implicit hc: HeaderCarrier): Future[Option[EnvelopeSummary]] =
    wsClient.url(s"${urls.envelopesUrl}/$envId").get() flatMap { response =>
      response.status match {
        case OK =>
          Future.successful(response.json.asOpt[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads))
        case NOT_FOUND =>
          retry(envId, nextTry, attempt)
        case _ =>
          Logger.warn(
            s"FileUploadConnector.envelopeStatus - failed to read envelope status, Api failed with status [${response.status}]")
          Future.successful(None)
      }
    }

  private def envelopeId(response: HttpResponse): Option[String] =
    response.header("Location").map(path => path.split("/").reverse.head)
}
