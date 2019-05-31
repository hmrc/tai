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

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadConnector @Inject()(metrics: Metrics,
                                    httpClient: HttpClient,
                                    wsClient: WSClient,
                                    urls: FileUploadUrls,
                                    config: FileUploadConfig)(implicit ec: ExecutionContext) {


  def routingRequest(envelopeId: String): JsValue = Json.obj(
    "envelopeId" -> envelopeId,
    "application" -> "TAI",
    "destination" -> "DMS")


  def createEnvelope(implicit hc: HeaderCarrier): Future[String] = {
    val envelopeBody: JsValue = Json.obj("callbackUrl" -> config.callbackUrl)

    val timerContext = metrics.startTimer(FusCreateEnvelope)
    httpClient.POST[JsValue, HttpResponse](urls.envelopesUrl, envelopeBody).map { response =>
      timerContext.stop()

      if (response.status == CREATED) {
        metrics.incrementSuccessCounter(FusCreateEnvelope)

        envelopeId(response)
          .getOrElse {
            Logger.warn("FileUploadConnector.createEnvelope - No envelope id returned by file upload service")
            throw new RuntimeException("No envelope id returned by file upload service")
          }
      }
      else {
        Logger.warn(s"FileUploadConnector.createEnvelope - failed to create envelope with status [${response.status}]")
        throw new RuntimeException("File upload envelope creation failed")
      }
    }.recover {
      case _: Exception =>
        Logger.warn("FileUploadConnector.createEnvelope - call to create envelope failed")
        metrics.incrementFailedCounter(FusCreateEnvelope)
        throw new RuntimeException("File upload envelope creation failed")
    }
  }

  def uploadFile(byteArray: Array[Byte], fileName: String, contentType: MimeContentType, envelopeId: String, fileId: String, awsClient: AhcWSClient)
                (implicit hc: HeaderCarrier) = {
    val url: String = urls.fileUrl(envelopeId, fileId)
    uploadFileCall(byteArray, fileName, contentType, url, awsClient).recover {
      case _: RuntimeException =>
        Logger.warn("FileUploadConnector.uploadFile - call to upload file failed")
        metrics.incrementFailedCounter(FusUploadFile)
        awsClient.close()
        throw new RuntimeException("File upload failed")
    }
  }

  def uploadFileCall(byteArray: Array[Byte], fileName: String, contentType: MimeContentType, url: String, ahcWSClient: AhcWSClient)(implicit hc: HeaderCarrier) = {
    val timerContext = metrics.startTimer(FusUploadFile)
    val multipartFormData = Source(FilePart("attachment", fileName, Some(contentType.description),
      Source(ByteString(byteArray) :: Nil)) :: DataPart("", "") :: Nil)

    ahcWSClient.url(url)
      .withHttpHeaders(hc.copy(otherHeaders = Seq("CSRF-token" -> "nocheck")).headers: _*).post(multipartFormData).map { response =>

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
    httpClient.POST[JsValue, HttpResponse](urls.routingUrl, routingRequest(envId)).map { response =>
      timerContext.stop()
      if (response.status == CREATED) {
        metrics.incrementSuccessCounter(FusCloseEnvelope)
        envelopeId(response)
          .getOrElse {
            Logger.warn("FileUploadConnector.closeEnvelope - No envelope id returned by file upload service")
            throw new RuntimeException("No envelope id returned by file upload service")
          }
      }
      else {
        Logger.warn(s"FileUploadConnector.closeEnvelope - failed to close envelope with status [${response.status}]")
        throw new RuntimeException("File upload envelope routing request failed")
      }
    }.recover {
      case _: Exception =>
        Logger.warn("FileUploadConnector.closeEnvelope - call to close envelope failed")
        metrics.incrementFailedCounter(FusCloseEnvelope)
        throw new RuntimeException("File upload envelope routing request failed")
    }
  }

  def envelope(envId: String)(implicit hc: HeaderCarrier): Future[Option[EnvelopeSummary]] = {
    wsClient.url(s"${urls.envelopesUrl}/$envId").get() map { response =>
      if (response.status == OK) {
        response.json.asOpt[EnvelopeSummary](FileUploadFormatters.envelopeSummaryReads)
      } else {
        Logger.warn(s"FileUploadConnector.envelopeStatus - failed to read envelope status, Api failed with status [${response.status}]")
        None
      }
    }
  }

  private def envelopeId(response: HttpResponse): Option[String] = {
    response.header("Location").map(path =>
      path.split("/")
        .reverse
        .head)
  }
}