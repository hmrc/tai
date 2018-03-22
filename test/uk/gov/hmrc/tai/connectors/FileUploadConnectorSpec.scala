/*
 * Copyright 2018 HM Revenue & Customs
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
import com.codahale.metrics.Timer
import com.codahale.metrics.Timer.Context
import org.mockito.Matchers
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.http.Status.{BAD_REQUEST, CREATED, OK}
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.tai.config.FileUploadConfig
import uk.gov.hmrc.tai.metrics.Metrics
import uk.gov.hmrc.tai.model.domain.MimeContentType
import uk.gov.hmrc.tai.model.enums.APITypes._
import uk.gov.hmrc.tai.model.fileupload.{EnvelopeFile, EnvelopeSummary}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class FileUploadConnectorSpec extends PlaySpec
  with MockitoSugar
  with OneAppPerSuite {

  "createEnvelope" must {
    "return an envelope id" in {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, None,
          Map("Location" -> Seq(s"localhost:8898/file-upload/envelopes/$envelopeId")))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
      Await.result(sut.createEnvelope, 5 seconds) mustBe "4142477f-9242-4a98-9c8b-73295cfb170c"
    }
    "call the file upload service create envelope endpoint" in {
      val callbackUrl = "theCallBackURL"
      val envelopeBody = Json.obj("callbackUrl" -> callbackUrl)

      val mockConfig = mock[FileUploadConfig]
      when(mockConfig.callbackUrl)
        .thenReturn(callbackUrl)

      val mockUrls = mock[FileUploadUrls]
      when(mockUrls.envelopesUrl)
        .thenReturn("createEnvelopeURL")

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, None,
          Map("Location" -> Seq(s"localhost:8898/file-upload/envelopes/$envelopeId")))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mockUrls, mockConfig)
      Await.result(sut.createEnvelope, 5.seconds)

      verify(mockHttpClient, times(1))
        .POST(Matchers.eq("createEnvelopeURL"), Matchers.eq(envelopeBody), any())(any(), any(), any(), any())
    }
    "throw a runtime exception" when {
      "the success response does not contain a location header" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED)))

        val sut = createSut(mock[Metrics], mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"
      }
      "the call to the file upload service create envelope endpoint fails" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("call failed")))

        val sut = createSut(mock[Metrics], mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"

      }
      "the call to the file upload service returns a failure response" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        val sut = createSut(mock[Metrics], mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
        val ex = the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        ex.getMessage mustBe "File upload envelope creation failed"
      }
    }
    "update metrics for the status of the create envelope call" when {
      "the call is successful" in {
        val mockTimeContext: Context = mock[Timer.Context]

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimeContext)

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED, None,
            Map("Location" -> Seq(s"localhost:8898/file-upload/envelopes/$envelopeId")))))

        val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
        Await.result(sut.createEnvelope, 5.seconds)

        verify(mockMetrics, times(1))
          .incrementSuccessCounter(FusCreateEnvelope)
        verify(mockMetrics, times(1))
          .startTimer(Matchers.eq(FusCreateEnvelope))
        verify(mockTimeContext, times(1))
          .stop()
      }
      "the call failed" in {
        val mockMetrics = mock[Metrics]

        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])
        the[RuntimeException] thrownBy Await.result(sut.createEnvelope, 5 seconds)

        verify(mockMetrics, times(1)).incrementFailedCounter(FusCreateEnvelope)
      }
    }
  }

  "uploadFile" must {

    "return Success" when {
      "File upload service successfully upload the file" in {
        val mockTimeContext: Context = mock[Timer.Context]
        val mockWSResponse = createMockResponse(OK, "")

        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any())).thenReturn(mockTimeContext)

        val mockHttpClient = mock[HttpClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val mockWSRequest = mock[WSRequest]

        when(mockWSRequest.post(anyObject[Source[MultipartFormData.Part[Source[ByteString, _]], _]]()))
          .thenReturn(Future.successful(mockWSResponse))

        when(mockWSRequest.withHeaders(any())).thenReturn(mockWSRequest)

        val mockWsClient = mock[AhcWSClient]
        val mockAhcWSClient: AhcWSClient = mock[AhcWSClient]
        when(mockAhcWSClient.url(any())).thenReturn(mockWSRequest)

        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val result = Await.result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, mockAhcWSClient), 5 seconds)

        result.status mustBe OK
        verify(mockMetrics, times(1)).incrementSuccessCounter(Matchers.eq(FusUploadFile))
        verify(mockAhcWSClient, times(1)).url(any())
        verify(mockMetrics, times(1)).startTimer(Matchers.eq(FusUploadFile))
        verify(mockTimeContext, times(1)).stop()
      }
    }
    "throw runtime exception" when {
      "file upload service return status other than OK" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val ahcWSClient: AhcWSClient = mock[AhcWSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSResponse = createMockResponse(BAD_REQUEST, "")
        val mockWSRequest = mock[WSRequest]

        when(mockWSRequest.post(anyObject[Source[MultipartFormData.Part[Source[ByteString, _]], _]]()))
          .thenReturn(Future.successful(mockWSResponse))
        when(ahcWSClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.withHeaders(any())).thenReturn(mockWSRequest)

        the[RuntimeException] thrownBy Await.result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
        verify(mockMetrics, times(1)).incrementFailedCounter(Matchers.eq(FusUploadFile))
      }

      "any error occurred" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSRequest = mock[WSRequest]

        when(mockWSRequest.post(anyObject[Source[MultipartFormData.Part[Source[ByteString, _]], _]]()))
          .thenReturn(Future.failed(new RuntimeException("Error")))
        when(mockWsClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.withHeaders(any())).thenReturn(mockWSRequest)
        val ahcWSClient: AhcWSClient = mock[AhcWSClient]

        the[RuntimeException] thrownBy Await.result(sut.uploadFile(new Array[Byte](1), fileName, contentType, envelopeId, fileId, ahcWSClient), 5 seconds)
      }
    }

  }

  "closeEnvelope" must {
    "return an envelope id" in {
      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, None,
          Map("Location" -> Seq(s"/file-routing/requests/$envelopeId")))))

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mock[FileUploadUrls], mock[FileUploadConfig])

      Await.result(sut.closeEnvelope(envelopeId), 5 seconds) mustBe envelopeId
    }

    "call the file upload service routing request endpoint" in {
      val uploadEndpoint = "uploadEndpointURL"

      val mockHttpClient = mock[HttpClient]
      when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(CREATED, None,
          Map("Location" -> Seq(s"/file-routing/requests/$envelopeId")))))

      val mockUrls = mock[FileUploadUrls]
      when(mockUrls.fileUrl(any(), any()))
        .thenReturn(uploadEndpoint)
      when(mockUrls.routingUrl)
        .thenReturn(uploadEndpoint)

      val mockTimerContext = mock[Timer.Context]
      val mockMetrics = mock[Metrics]
      when(mockMetrics.startTimer(any()))
        .thenReturn(mockTimerContext)

      val sut = createSut(mockMetrics, mockHttpClient, mock[WSClient], mockUrls, mock[FileUploadConfig])
      Await.result(sut.closeEnvelope(envelopeId), 5.seconds)

      verify(mockHttpClient, times(1))
        .POST(Matchers.eq(uploadEndpoint), any(), any())(any(), any(), any(), any())
      verify(mockMetrics, times(1))
        .startTimer(Matchers.eq(FusCloseEnvelope))
    }
    "throw a runtime exception" when {
      "the success response does not contain a location header" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED)))

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"
      }
      "the call to the file upload service routing request endpoint fails" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.failed(new RuntimeException("call failed")))

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"

      }
      "the call to the file upload service returns a failure response" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        val ex = the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        ex.getMessage mustBe "File upload envelope routing request failed"

      }
    }
    "update metrics for the status of the close envelope call" when {

      "the call is successful" in {
        val mockHttpClient = mock[HttpClient]
        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(CREATED, None,
            Map("Location" -> Seq(s"/file-routing/requests/$envelopeId")))))

        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]

        val mockTimerContext = mock[Timer.Context]
        val mockMetrics = mock[Metrics]
        when(mockMetrics.startTimer(any()))
          .thenReturn(mockTimerContext)

        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)

        Await.result(sut.closeEnvelope(envelopeId), 5.seconds)
        verify(mockMetrics, times(1)).startTimer(FusCloseEnvelope)
        verify(mockMetrics, times(1)).incrementSuccessCounter(FusCloseEnvelope)
      }

      "the call failed" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)

        when(mockHttpClient.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any()))
          .thenReturn(Future.successful(HttpResponse(400)))

        the[RuntimeException] thrownBy Await.result(sut.closeEnvelope(envelopeId), 5 seconds)

        verify(mockMetrics, times(1)).incrementFailedCounter(FusCloseEnvelope)
      }
    }
  }

  "envelopeStatus" must {
    "return envelope summary" when {
      "both files are available" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSRequest = mock[WSRequest]
        val mockResponse = createMockResponse(200, envelopeStatusResponse("AVAILABLE", "AVAILABLE", "CLOSED"))
        when(mockWsClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.get()).thenReturn(Future.successful(mockResponse))

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result.get mustBe EnvelopeSummary(envelopeId, "CLOSED",
          Seq(EnvelopeFile("4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-iform", "AVAILABLE"),
            EnvelopeFile("4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "AVAILABLE")))

      }

      "there are no files" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSRequest = mock[WSRequest]
        val mockResponse = createMockResponse(200, Json.obj("id" -> envelopeId, "status" -> "OPEN", "TEST" -> "Data"))
        when(mockWsClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.get()).thenReturn(Future.successful(mockResponse))

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result.get mustBe EnvelopeSummary(envelopeId, "OPEN", Nil)

      }
    }

    "return None" when {
      "status is not OK" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSRequest = mock[WSRequest]
        val mockResponse = createMockResponse(400, envelopeStatusResponse("AVAILABLE", "AVAILABLE"))
        when(mockWsClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.get()).thenReturn(Future.successful(mockResponse))

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result mustBe None
      }

      "response is incorrect" in {
        val mockMetrics = mock[Metrics]
        val mockHttpClient = mock[HttpClient]
        val mockWsClient = mock[WSClient]
        val mockUrls = mock[FileUploadUrls]
        val mockConfig = mock[FileUploadConfig]
        val sut = createSut(mockMetrics, mockHttpClient, mockWsClient, mockUrls, mockConfig)
        val mockWSRequest = mock[WSRequest]
        val mockResponse = createMockResponse(200, Json.obj("asdas" -> "asdas"))
        when(mockWsClient.url(any())).thenReturn(mockWSRequest)
        when(mockWSRequest.get()).thenReturn(Future.successful(mockResponse))

        val result = Await.result(sut.envelope(envelopeId), 5.seconds)

        result mustBe None
      }
    }
  }

  private def createMockResponse(status: Int, body: String): WSResponse = {
    val wsResponseMock = mock[WSResponse]
    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.body).thenReturn(body)
    wsResponseMock
  }

  private def createMockResponse(status: Int, json: JsValue): WSResponse = {
    val wsResponseMock = mock[WSResponse]
    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.json).thenReturn(json)
    wsResponseMock
  }

  implicit val hc = HeaderCarrier()

  private val envelopeId: String = "4142477f-9242-4a98-9c8b-73295cfb170c"

  private def envelopeStatusResponse(file1Status: String, file2Status: String, status: String = "OPEN") = {
    val file1 = Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-iform", "status" -> file1Status)
    val file2 = Json.obj("id" -> "4142477f-9242-4a98-9c8b-73295cfb170c-EndEmployment-20171009-metadata", "status" -> file2Status)
    Json.obj("id" -> envelopeId, "status" -> status, "files" -> JsArray(Seq(file1, file2)))
  }

  private val fileId = "fileId"
  private val fileName = "fileName.pdf"
  private val contentType = MimeContentType.ApplicationPdf

  private def createSut(metrics: Metrics,
                        HttpClient: HttpClient,
                        wsClient: WSClient,
                        urls: FileUploadUrls,
                        config: FileUploadConfig) =

    new FileUploadConnector(metrics, HttpClient, wsClient, urls, config)
}
